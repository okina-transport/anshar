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
    private final String vehicleJourney;
    private final String type;


    public SiriObjectStorageKey(String codespaceId, String lineRef, String key) {
        this(codespaceId, lineRef, key, null,null, null);
    }
    public SiriObjectStorageKey(String codespaceId, String lineRef, String key, String stopRef, String vehicleJourney, String type) {
        this.codespaceId = codespaceId;
        this.lineRef = lineRef;
        this.key = key;
        this.stopRef = stopRef;
        this.vehicleJourney = vehicleJourney;
        this.type = type;

    }

    public String getCodespaceId() {
        return codespaceId;
    }

    public String getLineRef() {
        return lineRef;
    }

    public String getKey() {
        return key;
    }

    public String getStopRef() {
        return stopRef;
    }

    String getVehicleJourney() {
        return vehicleJourney;
    }

    public String getType() {
        return type;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", SiriObjectStorageKey.class.getSimpleName() + "[", "]")
                .add("codespaceId='" + codespaceId + "'")
                .add("lineRef='" + lineRef + "'")
                .add("vehicleJourney='" + vehicleJourney + "'")
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
                Objects.equal(vehicleJourney, that.vehicleJourney) &&
                Objects.equal(lineRef, that.lineRef) &&
                Objects.equal(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(codespaceId, lineRef, key, stopRef, vehicleJourney);
    }
}