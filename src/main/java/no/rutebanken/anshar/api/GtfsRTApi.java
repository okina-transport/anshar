package no.rutebanken.anshar.api;

import no.rutebanken.anshar.config.GTFSRTType;

public class GtfsRTApi {
    private String datasetId;
    private String url;

    private GTFSRTType type;

    public String getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(String datasetId) {
        this.datasetId = datasetId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public GTFSRTType getType() {
        return type;
    }

    public void setType(GTFSRTType type) {
        this.type = type;
    }
}
