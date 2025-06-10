/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.mldht;

import java.io.*;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TransferQueue;
import java.util.stream.Collectors;

import lbms.plugins.mldht.DHTConfiguration;
import lbms.plugins.mldht.kad.DHT;
import lbms.plugins.mldht.kad.DHT.LogLevel;
import lbms.plugins.mldht.kad.DHTLogger;
import lbms.plugins.mldht.kad.Key;
import lbms.plugins.mldht.kad.messages.GetRequest;
import the8472.TorrentListener;
import the8472.mldht.cli.Server;
import the8472.mldht.indexing.TorrentDumper;
import the8472.utils.ConfigReader;
import the8472.utils.FilesystemNotifications;
import the8472.utils.XMLUtils;
import the8472.utils.concurrent.NonblockingScheduledExecutor;
import the8472.utils.io.NetMask;

/**
 * dht launch
 */
public class Launcher {

    private ConfigReader configReader;

    /**
     * dumper
     */
    private TorrentDumper torrentDumper;
    /**
     * listener for torrent
     */
    private final TorrentListener torrentListener;
    /**
     * singleton instance
     */
    private static Launcher instance;

    /**
     * instance
     *
     * @return
     */
    public static Launcher getInstance() {
        return instance;
    }

    class XmlConfig implements DHTConfiguration {

        int port;
        boolean multihoming;

        void update() {
            port = configReader.getLong("//core/port").orElse(49001L).intValue();
            multihoming = configReader.getBoolean("//core/multihoming").orElse(true);
        }


        @Override
        public boolean noRouterBootstrap() {
            return !configReader.getBoolean("//core/useBootstrapServers").orElse(true);
        }

        @Override
        public boolean isPersistingID() {
            return configReader.getBoolean("//core/persistID").orElse(true);
        }

        @Override
        public Path getStoragePath() {
            return Paths.get("./work");
        }

        @Override
        public int getListeningPort() {
            return port;
        }

        @Override
        public boolean allowMultiHoming() {
            return multihoming;
        }
    }

    XmlConfig config = new XmlConfig();

    List<DHT> dhts = new ArrayList<>();

    volatile boolean running = true;

//    Thread shutdownHook = new Thread(this::onVmShutdown, "shutdownHook");

    ScheduledExecutorService scheduler;

    FilesystemNotifications notifications = new FilesystemNotifications();

    DHTLogger logger;

    /**
     * with default config
     */
    public Launcher(TorrentListener listener) {
        this.torrentListener = listener;
        InputStream config = Launcher.class.getResourceAsStream("config-defaults.xml");
        configReader = new ConfigReader(config);
        configReader.read();
        scheduler = new NonblockingScheduledExecutor("mlDHT", Math.max(Runtime.getRuntime().availableProcessors(), 4), (t, ex) -> {
            logger.log(ex, LogLevel.Fatal);
        });
        instance = this;
    }

    /**
     * fetch torrent
     *
     * @param hash
     */
    public void fetchTorrent(String hash) {
        torrentDumper.fetch(hash);
    }

    /**
     * download
     *
     * @param hash
     */
    public void download(String hash) {
        Key k = new Key(hash);
        int i = new Random().nextInt(dhts.size());
        dhts.get(i).get(new GetRequest(k));
    }

    /**
     * start
     *
     * @throws Exception
     */
    public void start() throws Exception {
        config.update();

        Arrays.stream(DHT.DHTtype.values()).filter(t -> !this.isIPVersionDisabled(t.PREFERRED_ADDRESS_TYPE)).forEach(type -> {
            dhts.add(new DHT(type));
        });

        dhts.forEach(d -> {
            d.addSiblings(dhts);
            d.setScheduler(scheduler);
        });

        Path logDir = Paths.get("./work/logs/");
        Files.createDirectories(logDir);

        final Path log = logDir.resolve("dht.log");
        Path exLog = logDir.resolve("exceptions.log");

        //final PrintWriter logWriter = ;
        final PrintWriter exWriter = new PrintWriter(Files.newBufferedWriter(exLog, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE, StandardOpenOption.WRITE), true);

        logger = new DHTLogger() {

            private String timeFormat(LogLevel level) {
                return "[" + Instant.now().toString() + "][" + level.toString() + "] ";
            }

            TransferQueue<String> toLog = new LinkedTransferQueue<>();

            Thread writer = new Thread() {
                @Override
                public void run() {
                    try {
                        FileChannel.open(log, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).close();

                        while (true) {

                            String toWrite = toLog.take();

                            // log rotate at 1GB
                            if (Files.size(log) > 1024 * 1024 * 1024)
                                Files.move(log, log.resolveSibling("dht.log.1"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                            try (PrintWriter logWriter = new PrintWriter(Files.newBufferedWriter(log, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND))) {
                                do {
                                    logWriter.println(toWrite);
                                } while ((toWrite = toLog.poll()) != null);
                                logWriter.flush();
                            }

                        }

                    } catch (InterruptedException | IOException e) {
                        e.printStackTrace();
                    }
                }
            };

            {
                writer.setDaemon(true);
                writer.setName("LogWriter");
                writer.start();
            }

            public void log(String message, LogLevel l) {
                try {
                    toLog.put(timeFormat(l) + message);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            public void log(Throwable e, LogLevel l) {
                exWriter.append(timeFormat(l));
                e.printStackTrace(exWriter);
                exWriter.flush();
            }
        };

        DHT.setLogger(logger);

        new Diagnostics().init(dhts, logDir);

        setLogLevel();

        for (DHT dht : dhts) {
            if (isIPVersionDisabled(dht.getType().PREFERRED_ADDRESS_TYPE))
                continue;
            dht.start(config);
            dht.bootstrap();
        }

        // need to run this after startup, Node doesn't exist before then
        setTrustedMasks();

        //components.forEach(c -> c.start(dhts, configReader));
        torrentDumper = new TorrentDumper();
        if (torrentListener != null) {
            torrentDumper.setTorrentListener(torrentListener);
        }
        torrentDumper.start(dhts, configReader);
        Server server = new Server();
        server.start(dhts, configReader);

        //Runtime.getRuntime().addShutdownHook(shutdownHook);

//        Path shutdown = Paths.get("./work/shutdown");

//        if (!Files.exists(shutdown))
//            Files.createFile(shutdown);

//        notifications.addRegistration(shutdown, (path, kind) -> {
//            if (path.equals(shutdown)) {
//                initiateShutdown();
//            }
//        });

        // need 1 non-daemon-thread to keep VM alive
//        while (running) {
//            synchronized (this) {
//                this.wait();
//            }
//        }
//        shutdownCleanup();
    }

    /**
     * stop
     */
    public void stop() {
        this.running = false;
        this.shutdownCleanup();
    }

    private void setLogLevel() {
        String rawLevel = configReader.get(XMLUtils.buildXPath("//core/logLevel")).orElse("Info");
        LogLevel level = LogLevel.valueOf(rawLevel);
        DHT.setLogLevel(level);
    }

    private void setTrustedMasks() {
        Collection<NetMask> masks = configReader.getAll(XMLUtils.buildXPath("//core/clusterNodes/networkPrefix")).map(NetMask::fromString).collect(Collectors.toList());
        dhts.forEach((d) -> {
            if (d.isRunning())
                d.getNode().setTrustedNetMasks(masks);
        });
    }

    private boolean isIPVersionDisabled(Class<? extends InetAddress> type) {
        long disabled = configReader.getLong("//core/disableIPVersion").orElse(-1L);
        if (disabled == 6 && type.isAssignableFrom(Inet6Address.class))
            return true;
        return disabled == 4 && type.isAssignableFrom(Inet4Address.class);
    }

    boolean cleanupDone = false;

    void shutdownCleanup() {
        synchronized (this) {
            if (cleanupDone)
                return;
            cleanupDone = true;
            Arrays.asList(torrentDumper).forEach(Component::stop);
            dhts.forEach(DHT::stop);
        }
    }

    /**
     * torrent listener
     *
     * @return
     */
    public TorrentListener getTorrentListener() {
        return torrentListener;
    }
}
