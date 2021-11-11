/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.mldht.cli;

import lbms.plugins.mldht.kad.Key;
import lbms.plugins.mldht.kad.utils.ThreadLocalUtils;
import the8472.bencode.PrettyPrinter;
import the8472.bt.TorrentUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static the8472.utils.Functional.typedGet;

public class TorrentInfo {

    Path source;
    ByteBuffer raw;
    Map<String, Object> root;
    Map<String, Object> info;
    Charset encoding = StandardCharsets.UTF_8;
    boolean truncate = true;

    /**
     * 根据raw生成
     */
    public TorrentInfo(ByteBuffer raw) {
        this.raw = raw;
        decode();
    }

    public TorrentInfo(Path source) {
        this.source = source;
    }

    void readRaw() {
        if (raw != null)
            return;
        try (FileChannel chan = FileChannel.open(source, StandardOpenOption.READ)) {
            raw = chan.map(MapMode.READ_ONLY, 0, chan.size());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 根据raw生成torrentinfo
     *
     * @param raw
     */
    public static TorrentInfo raw(ByteBuffer raw) {
        return new TorrentInfo(raw);
    }

    public void decode() {
        if (root != null)
            return;
        readRaw();
        root = ThreadLocalUtils.getDecoder().decode(raw.duplicate());
        typedGet(root, "info", Map.class).ifPresent(i -> info = i);
        if (info != null) {
            String charset = typedGet(info, "encoding", byte[].class).map(b -> new String(b, StandardCharsets.ISO_8859_1)).orElse(null);
            if (charset != null) {
                try {
                    this.encoding = Charset.forName(charset);
                } catch (Exception e) {
                    System.err.println("Charset " + charset + "not supported, falling back to " + encoding.name());
                }
            }
        }
    }

    public Key infoHash() {
        return TorrentUtils.infohash(raw);
    }

    public Optional<String> name() {
        decode();
        Optional<String> name = typedGet(info, "name.utf-8", byte[].class).map(b -> new String(b, StandardCharsets.UTF_8));
        if (!name.isPresent()) {
            name = typedGet(info, "name", byte[].class).map(b -> new String(b, encoding));
        }
        return name;
    }

    List<Map<String, Object>> files() {
        return typedGet(info, "files", List.class).map((List l) -> {
            return (List<Map<String, Object>>) l.stream().filter(Map.class::isInstance).collect(Collectors.toList());
        }).orElse(Collections.emptyList());
    }

    /**
     * 打印
     *
     * @return
     */
    public String raw() {
        decode();
        PrettyPrinter p = new PrettyPrinter();
        p.indent("  ");
        p.guessHumanReadableStringValues(true);
        p.truncateHex(truncate);
        p.append(root);
        return p.toString();
    }
}
