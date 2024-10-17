package no.rutebanken.anshar.ishtar.model;

import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class SubscriptionDto {

    private Long id;
    private String vendor;
    private String name;
    private String datasetId;
    private String operatorNamespace;
    private Integer previewIntervalSeconds;
    private Integer updateIntervalSeconds;
    private Integer changeBeforeUpdatesSeconds;
    private Integer heartbeatIntervalSeconds;
    private String subscriptionMode;
    private String subscriptionType;
    private String serviceType;
    private String version;
    private String contentType;
    private String subscriptionId;
    private String requestorRef;
    private Integer durationOfSubscriptionHours;
    private String mappingAdapterId;
    private String restartTime;
    private Boolean active;
    private Date updateDatetime;
    private Boolean revertIds;
    private Boolean discoverySubscription;
    private List<SubscriptionStopDto> subscriptionStops;
    private List<SubscriptionLineDto> subscriptionLines;
    private List<UrlMapDto> urlMaps;
    private List<CustomHeaderDto> customHeaders;
    private List<IdMappingPrefixDto> idMappingPrefixes;

}
