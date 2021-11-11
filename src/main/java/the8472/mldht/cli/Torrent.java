package the8472.mldht.cli;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import the8472.utils.Mappers;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * torrent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Torrent {

    /**
     * hash
     */
    private String hash;
    /**
     * pieces
     */
    private String pieces;
    /**
     * publisher
     */
    @JsonProperty("publisher.utf-8")
    private String publisherUtf8;
    /**
     * publisher
     */
    private String publisher;
    /**
     * name utf8
     */
    @JsonProperty("name.utf-8")
    private String nameUtf8;
    /**
     * name
     */
    @JsonProperty("name")
    private String name;
    /**
     * files
     */
    @JsonProperty("files")
    private List<TFile> files;
    /**
     * length
     */
    @JsonProperty("piece length")
    private long pieceLength;
    /**
     * publisher url
     */
    @JsonProperty("publisher-url")
    private String publisherUrl;
    /**
     * publisher url utf8
     */
    @JsonProperty("publisher-url.utf-8")
    private String publisherUrlUtf8;
    /**
     * len
     */
    private long length;
    /**
     * raw data
     */
    private ByteBuffer raw;

    /**
     * load from file
     *
     * @param file
     * @return
     */
    public static Torrent load(String file) {
        return load(new File(file));
    }

    /**
     * from file
     *
     * @param file
     * @return
     */
    public static Torrent load(File file) {
        Path p = Paths.get(file.getAbsolutePath());
        TorrentInfo ti = new TorrentInfo(p);
        return load(ti);
    }

    /**
     * load from raw data
     *
     * @param raw
     * @return
     */
    public static Torrent load(ByteBuffer raw) {
        return load(new TorrentInfo(raw.duplicate()));
    }

    /**
     * load from ti
     *
     * @param ti
     * @return
     */
    public static Torrent load(TorrentInfo ti) {
        ti.decode();
        String info = ti.raw();
        JsonNode node = Mappers.parseJson(info);
        node = node.get("info");
        info = node.toString();
        Torrent torrent = Mappers.parseJson(info, new TypeReference<Torrent>() {
        });
        torrent.setHash(ti.infoHash().toString(false));
        torrent.setName(ti.name().orElse(null));
        torrent.setLength(torrent.len());
        torrent.raw = ti.raw.duplicate();
        return torrent;
    }

    /**
     * len
     *
     * @return
     */
    public long len() {
        if (files != null) {
            return files.stream().mapToLong(TFile::getLength).sum();
        }
        return 0;
    }
}
