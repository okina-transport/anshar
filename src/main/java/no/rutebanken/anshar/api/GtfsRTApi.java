package no.rutebanken.anshar.api;

import lombok.Data;
import no.rutebanken.anshar.config.GTFSRTType;

@Data
public class GtfsRTApi {
    private Long id;
    private String datasetId;
    private String url;
    private GTFSRTType type;
    private Boolean active;
    private Boolean validated;
    private String routeIdList;
    private FlowStatus status;
    private long lastUpdate;
    private Boolean closeMissingAlerts;
    private Boolean generateActivePeriod;
    private Integer activePeriodDays;
    private PublishedLineNameMapping publishedLineNameMapping;
    private String apiKey;
}
