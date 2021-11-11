package the8472.mldht.cli;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class TFile {
    /**
     * path
     */
    private List<String> path;
    /**
     * ed2k
     */
    private String ed2k;
    /**
     * len
     */
    private long length;
    /**
     * file hash
     */
    private String filehash;
    /**
     * path utf8
     */
    @JsonProperty("path.utf-8")
    private List<String> pathUtf8;
}
