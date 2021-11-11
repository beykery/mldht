package the8472;

import the8472.mldht.cli.Torrent;

/**
 * torrent listener
 */
public interface TorrentListener {

    /**
     * on torrent
     *
     * @param torrent
     */
    void onTorrent(Torrent torrent);

    /**
     * torrent exist
     *
     * @param hash
     * @return
     */
    boolean torrentExists(String hash);
}
