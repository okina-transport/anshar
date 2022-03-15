package no.rutebanken.anshar.data;

import com.google.common.base.Objects;

import java.io.Serializable;
import java.util.StringJoiner;

public class SiriObjectStorageKey implements Serializable {

    private static final long serialVersionUID = 6251469909676984750L;

    private final String codespaceId;
    private final String lineRef;
    private final String stopRef;

    private final String key;


    public SiriObjectStorageKey(String codespaceId, String lineRef, String key) {
        this(codespaceId, lineRef, key, null);
    }
    public SiriObjectStorageKey(String codespaceId, String lineRef, String key, String stopRef) {
        this.codespaceId = codespaceId;
        this.lineRef = lineRef;
        this.key = key;
        this.stopRef = stopRef;
    }

    String getCodespaceId() {
        return codespaceId;
    }

    String getLineRef() {
        return lineRef;
    }

    String getKey() {
        return key;
    }

    String getStopRef() {
        return stopRef;
    }



    @Override
    public String toString() {
        return new StringJoiner(", ", SiriObjectStorageKey.class.getSimpleName() + "[", "]")
                .add("codespaceId='" + codespaceId + "'")
                .add("lineRef='" + lineRef + "'")
                .add("stopRef='" + stopRef + "'")
                .add("key='" + key + "'")
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SiriObjectStorageKey that = (SiriObjectStorageKey) o;
        return Objects.equal(codespaceId, that.codespaceId) &&
                Objects.equal(stopRef, that.stopRef) &&
                Objects.equal(lineRef, that.lineRef) &&
                Objects.equal(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(codespaceId, lineRef, key, stopRef);
    }
}