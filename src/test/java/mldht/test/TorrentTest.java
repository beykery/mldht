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
    Launcher la;

    @BeforeEach
    void setUp() throws Exception {
        la = new Launcher(new TorrentListener() {
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

    @Test
    public void download() throws Exception {
        la.fetchTorrent("6B16D5ACD79E0BCD444ED769A87E790854E5E3F6");
//        Thread.sleep(10 * 1000L);
//        Client client = new Client(new String[]{"HELP", "6B16D5ACD79E0BCD444ED769A87E790854E5E3F6"});
        Thread.sleep(24 * 3600 * 1000L);
    }
}
