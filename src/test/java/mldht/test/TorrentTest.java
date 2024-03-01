package mldht.test;

import org.junit.jupiter.api.Test;
import the8472.TorrentListener;
import the8472.mldht.Launcher;
import the8472.mldht.cli.Client;
import the8472.mldht.cli.Torrent;

import java.io.File;
import java.nio.file.Files;

/**
 * test torrent
 */
public class TorrentTest {
    Launcher la;

    @Test
    void start() throws Exception {
        la = new Launcher(new TorrentListener() {
            @Override
            public void onTorrent(Torrent torrent) {
                System.out.println(torrent);
                try {
                    torrent.save(Files.newOutputStream(new File("./" + torrent.getHash() + ".torrent").toPath()));
                } catch (Exception ex) {
                    System.out.println("save to disk error: " + torrent.getHash());
                }
            }

            @Override
            public boolean torrentExists(String hash) {
                return false;
            }
        });
        la.start();
        Thread.sleep(24 * 3600 * 1000);
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
        Thread.sleep(2000);
        new Client("GET", "6B16D5ACD79E0BCD444ED769A87E790854E5E3F6");
        //la.download("6B16D5ACD79E0BCD444ED769A87E790854E5E3F6");
//        Thread.sleep(10 * 1000L);
//        Client client = new Client(new String[]{"HELP", "6B16D5ACD79E0BCD444ED769A87E790854E5E3F6"});
        Thread.sleep(24 * 3600 * 1000L);
    }

    @Test
    void get() throws Exception {
        new Client("GETTORRENT", "6B16D5ACD79E0BCD444ED769A87E790854E5E3F6");
    }

    /**
     * burst
     */
    @Test
    void burst() throws Exception {
        new Client("BURST");
    }
}
