package no.rutebanken.anshar.api;

import lombok.Data;
import no.rutebanken.anshar.config.GTFSRTType;

@Data
public class GtfsRTApi {

    private String datasetId;
    private String url;
    private GTFSRTType type;
    private Boolean active;
    
}
