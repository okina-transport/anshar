package no.rutebanken.anshar.api;

import lombok.Data;
import no.rutebanken.anshar.config.GTFSRTType;

import java.io.Serializable;

@Data
public class GtfsRTApi implements Serializable {
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
}
