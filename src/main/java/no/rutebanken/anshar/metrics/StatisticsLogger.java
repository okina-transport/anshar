package no.rutebanken.anshar.metrics;


import no.rutebanken.anshar.data.MonitoredStopVisits;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.*;
import java.util.stream.Collectors;


public class StatisticsLogger {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private MonitoredStopVisits monitoredStopVisits;

    @Autowired
    private SubscriptionManager subscriptionManager;


    public void writeLogs() {

        List<SubscriptionSetup> smSubscriptions = subscriptionManager.getAllSubscriptions(SiriDataType.STOP_MONITORING);

        List<String> datasetIds = smSubscriptions.stream()
                                               .map(SubscriptionSetup::getDatasetId)
                                               .distinct()
                                               .collect(Collectors.toList());

        monitoredStopVisits.writeStatistics(datasetIds);

    }
}
