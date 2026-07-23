package no.rutebanken.anshar.api;

import lombok.Data;

@Data
public class SiriApi {
    private Long id;
    private String datasetId;
    private String type;
    private String url;
    private Boolean active;
    private Boolean validated;
}
