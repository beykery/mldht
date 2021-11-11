package mldht.test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import the8472.TorrentListener;
import the8472.mldht.Launcher;
import the8472.mldht.cli.Torrent;

/**
 * test torrent
 */
public class TorrentTest {

    @BeforeEach
    void setUp() throws Exception {

    }

    @Test
    public void start() throws Exception {
        Launcher la = new Launcher(new TorrentListener() {
            @Override
            public void onTorrent(Torrent torrent) {
                System.out.println(torrent);
            }

            @Override
            public boolean torrentExists(String hash) {
                return false;
            }
        });
        la.start();
        Thread.sleep(10 * 60 * 60 * 1000);
    }

    /**
     * test torrent file from file
     */
    @Test
    public void testTorrentFile() {
        String file = "./6B16D5ACD79E0BCD444ED769A87E790854E5E3F6.torrent";
        Torrent t = Torrent.load(file);
        System.out.println(t);
    }

}
