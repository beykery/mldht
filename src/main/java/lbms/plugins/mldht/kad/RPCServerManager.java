/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lbms.plugins.mldht.kad.utils.AddressUtils;
import lbms.plugins.mldht.kad.utils.ThreadLocalUtils;

public class RPCServerManager {
	
	boolean destroyed;
	
	public RPCServerManager(DHT dht) {
		this.dht = dht;
		updateBindAddrs();
	}
	
	DHT dht;
	private ConcurrentHashMap<InetAddress,RPCServer> interfacesInUse = new ConcurrentHashMap<>();
	private volatile List<InetAddress> validBindAddresses = Collections.emptyList();
	private volatile RPCServer[] activeServers = new RPCServer[0];
	private SpamThrottle outgoingThrottle = new SpamThrottle();
	
	public void updateReachableEndpoints(long now) {
		if(destroyed)
			return;
		
		List<RPCServer> reachableServers = new ArrayList<>(interfacesInUse.values().size());
		for(Iterator<RPCServer> it = interfacesInUse.values().iterator();it.hasNext();)
		{
			RPCServer srv = it.next();
			srv.checkReachability(now);
			if(srv.isReachable())
				reachableServers.add(srv);
		}
		
		if(reachableServers.size() > 0) {
			CompletableFuture<RPCServer> cf = activeServerFuture.getAndSet(null);
			if(cf != null) {
				cf.complete(reachableServers.get(ThreadLocalRandom.current().nextInt(reachableServers.size())));
			}
		}
		
		activeServers = reachableServers.toArray(new RPCServer[reachableServers.size()]);
	}
	
	private void updateBindAddrs() {
		Class<? extends InetAddress> type = dht.getType().PREFERRED_ADDRESS_TYPE;
		
		List<InetAddress> oldBindAddresses = validBindAddresses;
		
		List<InetAddress> newBindAddrs = AddressUtils.allAddresses().filter(type::isInstance)
				.distinct()
				.collect(Collectors.toCollection(() -> new ArrayList<>()));
		
		newBindAddrs.add(AddressUtils.getAnyLocalAddress(type));
		
		newBindAddrs.removeIf(normalizedAddressPredicate().negate());
		
		if(!oldBindAddresses.equals(newBindAddrs)) {
			DHT.logInfo("updating set of valid bind addresses\n old: " + oldBindAddresses + "\n new: " + newBindAddrs);
		}
		
		validBindAddresses = newBindAddrs;
	}
	
	public void doBindChecks() {
		updateBindAddrs();
		Collection<InetAddress> valid = validBindAddresses;
		getAllServers().forEach(srv -> {
			InetAddress addr = srv.getBindAddress();
			if(!valid.contains(addr)) {
				DHT.logInfo("bind address no longer valid, removing from active set: " + addr);
				srv.stop();
			}
		});
		
	}
	
	private Predicate<InetAddress> normalizedAddressPredicate() {
		Predicate<InetAddress> pred = dht.config.filterBindAddress();
		return (addr) -> {
			if(pred.test(AddressUtils.getAnyLocalAddress(addr.getClass()))) {
				return true;
			}
			return pred.test(addr);
		};
	}
	
	
	public void startNewServers() {
		if(destroyed) {
			return;
		}
		boolean multihome = dht.config.allowMultiHoming();
		Class<? extends InetAddress> addressType = dht.getType().PREFERRED_ADDRESS_TYPE;
		
		Predicate<InetAddress> addressFilter = normalizedAddressPredicate();
		
		
		if(multihome) {
			// we only consider global unicast addresses in multihoming mode
			// this is mostly meant for server configurations
			List<InetAddress> addrs = AddressUtils.availableGloballyRoutableAddrs(validBindAddresses.stream(), addressType)
				.filter(addressFilter)
				.collect(Collectors.toCollection(ArrayList::new));
			addrs.removeAll(interfacesInUse.keySet());
			// create new servers for all IPs we aren't currently haven't bound
			addrs.forEach(addr -> newServer(addr));
			return;
		}
		
		// single home
		RPCServer current = interfacesInUse.values().stream().findAny().orElse(null);
		InetAddress defaultBind = Optional.ofNullable(AddressUtils.getDefaultRoute(addressType)).filter(addressFilter).orElse(null);
		
		// check if we have bound to an anylocaladdress because we didn't know any better and consensus converged on a local address
		// that's mostly going to happen on v6 if we can't find a default route for v6
		// no need to recheck address filter since it allowed any local address bind in the first place
		if(current != null && current.getBindAddress().isAnyLocalAddress() && current.getConsensusExternalAddress() != null && AddressUtils.isValidBindAddress(current.getConsensusExternalAddress().getAddress()))
		{
			InetAddress rebindAddress = current.getConsensusExternalAddress().getAddress();
			DHT.logInfo("rebinding any local to" + rebindAddress);
			current.stop();
			newServer(rebindAddress);
			return;
		}
		
		// default bind changed and server is not reachable anymore. this may happen when an interface is nominally still available but not routable anymore. e.g. ipv6 temporary addresses
		if(current != null && defaultBind != null && !current.getBindAddress().equals(defaultBind) && !current.isReachable() && current.age().getSeconds() > TimeUnit.MINUTES.toSeconds(2)) {
			DHT.logInfo("stopping currently unreachable " + current.getBindAddress() + "to bind to new default route" + defaultBind);
			current.stop();
			newServer(defaultBind);
			return;
		}
		
		// single homed & already have a server -> no need for another one
		if(current != null)
			return;
		
		// this is our default strategy.
		if(defaultBind != null) {
			DHT.logInfo("selecting default route bind" + defaultBind);
			newServer(defaultBind);
			return;
		}
		
		// last resort for v6, try a random global unicast address, otherwise anylocal
		if(addressType.isAssignableFrom(Inet6Address.class)) {
			InetAddress addr = AddressUtils.availableGloballyRoutableAddrs(validBindAddresses.stream(), addressType).filter(addressFilter)
				.findAny()
				.orElse(Optional.of(AddressUtils.getAnyLocalAddress(addressType))
					.filter(addressFilter)
					.orElse(null));
			if(addr != null) {
				newServer(addr);
				DHT.logInfo("Last resort address selection" + addr);
			}
			return;
		}
		
		// last resort v4: try any-local address first. If the address filter forbids that we try any of the interface addresses including non-global ones
		Stream.concat(Stream.of(AddressUtils.getAnyLocalAddress(addressType)), AddressUtils.nonlocalAddresses()
			.filter(dht.getType()::canUseAddress))
			.filter(addressFilter)
			.findFirst()
			.ifPresent(addr -> {
				DHT.logInfo("last resort address selection " + addr);
				newServer(addr);
		});
	}
	
	void newServer(InetAddress addr) {
		RPCServer srv = new RPCServer(this,addr,dht.config.getListeningPort(), dht.serverStats);
		if(interfacesInUse.putIfAbsent(addr, srv) == null)  {
			srv.setOutgoingThrottle(outgoingThrottle);
			onServerRegistration.forEach(c -> c.accept(srv));
			// doing the socket setup takes time, do it in the background
			dht.getScheduler().execute(srv::start);
		} else {
			srv.stop();
		}
	}
	
	List<Consumer<RPCServer>> onServerRegistration = new CopyOnWriteArrayList<>();
	
	void notifyOnServerAdded(Consumer<RPCServer> toNotify) {
		onServerRegistration.add(toNotify);
	}
	
	void serverRemoved(RPCServer srv) {
		interfacesInUse.remove(srv.getBindAddress(),srv);
		dht.getTaskManager().removeServer(srv);
	}
	
	public void destroy() {
		destroyed = true;
		new ArrayList<>(interfacesInUse.values()).parallelStream().forEach(RPCServer::stop);
		
		CompletableFuture<RPCServer> cf = activeServerFuture.getAndSet(null);
		if(cf != null) {
			cf.completeExceptionally(new DHTException("could not obtain active server, DHT was shut down"));
		}
		
	}
	
	public int getServerCount() {
		return interfacesInUse.size();
	}
	
	public int getActiveServerCount()
	{
		return activeServers.length;
	}
	
	public SpamThrottle getOutgoingRequestThrottle() {
		return outgoingThrottle;
	}
	
	/**
	 * @param fallback tries to return an inactive server if no active one can be found
	 * @return a random active server, or <code>null</code> if none can be found
	 */
	public RPCServer getRandomActiveServer(boolean fallback)
	{
		RPCServer[] srvs = activeServers;
		if(srvs.length == 0)
			return fallback ? getRandomServer() : null;
		return srvs[ThreadLocalUtils.getThreadLocalRandom().nextInt(srvs.length)];
	}
	
	AtomicReference<CompletableFuture<RPCServer>> activeServerFuture = new AtomicReference<>(null);
	
	public CompletableFuture<RPCServer> awaitActiveServer() {
		return activeServerFuture.updateAndGet(existing -> {
			if(existing != null)
				return existing;
			return new CompletableFuture<>();
		});
	}
	
	/**
	 * may return null
	 */
	public RPCServer getRandomServer() {
		List<RPCServer> servers = getAllServers();
		if(servers.isEmpty())
			return null;
		return servers.get(ThreadLocalUtils.getThreadLocalRandom().nextInt(servers.size()));
	}
	
	public List<RPCServer> getAllServers() {
		return new ArrayList<>(interfacesInUse.values());
	}
	
}
