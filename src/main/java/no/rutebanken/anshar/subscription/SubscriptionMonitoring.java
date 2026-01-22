package no.rutebanken.anshar.subscription;

import lombok.Data;

@Data
public class SubscriptionMonitoring {

    private String dataset;
    private String dataType;
    private String httpStatus;
    private String producerUrl;
    private SiriDataType siriDataType;
}
