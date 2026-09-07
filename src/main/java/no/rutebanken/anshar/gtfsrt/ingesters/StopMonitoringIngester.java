package no.rutebanken.anshar.gtfsrt.ingesters;

import jakarta.xml.bind.JAXBException;
import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.data.DiscoveryCache;
import no.rutebanken.anshar.routes.health.HealthManager;
import no.rutebanken.anshar.routes.siri.handlers.inbound.StopMonitoringInbound;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import org.apache.camel.Exchange;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.MonitoredStopVisit;
import uk.org.siri.siri21.MonitoredStopVisitCancellation;
import uk.org.siri.siri21.Siri;

import javax.xml.stream.XMLStreamException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static no.rutebanken.anshar.routes.validation.validators.Constants.*;

@Service
@Slf4j
public class StopMonitoringIngester extends AbstractIngester {
    private final SubscriptionManager subscriptionManager;
    private final StopMonitoringInbound stopMonitoringInbound;
    private final HealthManager healthManager;
    private final DiscoveryCache discoveryCache;

    public StopMonitoringIngester(SubscriptionManager subscriptionManager, StopMonitoringInbound stopMonitoringInbound, HealthManager healthManager, DiscoveryCache discoveryCache) {
        this.subscriptionManager = subscriptionManager;
        this.stopMonitoringInbound = stopMonitoringInbound;
        this.healthManager = healthManager;
        this.discoveryCache = discoveryCache;
        this.dataType = SiriDataType.STOP_MONITORING;
    }


    public void processIncomingSMFromGTFSRT(Exchange e) {
        InputStream xml = e.getIn().getBody(InputStream.class);
        Long inboundTime = e.getIn().getHeader(INBOUND_TIME_HEADER_NAME, Long.class);
        try {
            Siri siri = SiriValueTransformer.parseXml(xml);
            String datasetId = e.getIn().getHeader(DATASET_ID_HEADER_NAME, String.class);
            String url = e.getIn().getHeader(URL_HEADER_NAME, String.class);

            if (siri.getServiceDelivery() == null || siri.getServiceDelivery().getStopMonitoringDeliveries() == null ||
                    siri.getServiceDelivery().getStopMonitoringDeliveries().get(0) == null) {
                log.info("Empty StopMonitoring from GTFS-RT on dataset: {}", datasetId);
                return;
            }

            healthManager.dataReceived();

            List<MonitoredStopVisit> stopVisits = siri.getServiceDelivery().getStopMonitoringDeliveries().getFirst().getMonitoredStopVisits();
            List<String> visitSubscriptionList = getSubscriptionsFromVisits(stopVisits);
            checkAndCreateSubscriptions(visitSubscriptionList, GTFSRT_SM_PREFIX, SiriDataType.STOP_MONITORING, RequestType.GET_STOP_MONITORING, datasetId, url);
            Collection<MonitoredStopVisit> ingestedVisits = stopMonitoringInbound.ingestStopVisits(datasetId, stopVisits, inboundTime);

            for (MonitoredStopVisit visit : ingestedVisits) {
                subscriptionManager.touchSubscription(GTFSRT_SM_PREFIX + visit.getMonitoringRef().getValue(), false);
            }

            List<MonitoredStopVisitCancellation> stopVisitToCancel = siri.getServiceDelivery().getStopMonitoringDeliveries().getFirst().getMonitoredStopVisitCancellations();

            if (CollectionUtils.isNotEmpty(stopVisitToCancel)) {
                stopMonitoringInbound.cancelStopVisits(datasetId, stopVisitToCancel, inboundTime);
            }

            log.info("GTFS-RT - Ingested  stop Times {} on {} . datasetId:{}, URL:{}", ingestedVisits.size(), stopVisits.size(), datasetId, url);


        } catch (JAXBException | XMLStreamException ex) {
            log.error("Error while unmarshalling siri message from gtfsrt SM", ex);
        }
    }

    /**
     * Read all stopVisit messages and build a list of subscriptions that must be checked(or created if not exists)
     *
     * @param stopVisits The list of stop visits
     * @return The list of subscription ids build by reading the visits
     */
    private List<String> getSubscriptionsFromVisits(List<MonitoredStopVisit> stopVisits) {

        return stopVisits.stream()
                .filter(visit -> visit.getMonitoringRef() != null && visit.getMonitoringRef().getValue() != null)
                .map(visit -> visit.getMonitoringRef().getValue())
                .collect(Collectors.toList());


    }

    /***
     * Read the list of subscription ids and for each, check if it exists. If not, a new subscription is created
     * @param subscriptionIds subscription identifiers
     * @param customPrefix custom prefix for subscription ids
     * @param dataType subscription data type
     * @param requestType subscription request type
     *  The list of subscription ids
     */
    private void checkAndCreateSubscriptions(List<String> subscriptionIds, String customPrefix, SiriDataType dataType, RequestType requestType, String datasetId, String url) {

        for (String subscriptionId : subscriptionIds) {
            if (subscriptionManager.isGTFSRTSubscriptionExisting(customPrefix + datasetId + "_" + subscriptionId))
                //A subscription is already existing for this vehicle journey. No need to create one
                continue;

            if (dataType.equals(SiriDataType.STOP_MONITORING)) {
                discoveryCache.addStop(datasetId, subscriptionId);
            }

            createNewSubscription(subscriptionId, customPrefix, dataType, requestType, datasetId, url);
            subscriptionManager.addGTFSRTSubscription(customPrefix + datasetId + "_" + subscriptionId);
        }
    }

    /**
     * Create a new subscription for the ref given in parameter
     *
     * @param ref          The id for which a subscription must be created
     * @param customPrefix
     * @param dataType
     * @param requestType
     */
    private void createNewSubscription(String ref, String customPrefix, SiriDataType dataType, RequestType requestType, String datasetId, String url) {

        // 1 subscription by type (SM/ET/SX/VM) and by datasetId
        String globalSubscriptionId = customPrefix + datasetId;
        SubscriptionSetup globalSub = subscriptionManager.getSubscriptionBySubscriptionId(globalSubscriptionId);

        if (globalSub != null) {
            if (!globalSub.getStopMonitoringRefValues().contains(ref)) {
                globalSub.getStopMonitoringRefValues().add(ref);
            }
        } else {
            SubscriptionSetup setup = createStandardSubscription(ref, datasetId, url);
            setup.setName(globalSubscriptionId);
            setup.setSubscriptionType(dataType);
            setup.setSubscriptionId(globalSubscriptionId);
            setup.getUrlMap().clear();
            setup.getUrlMap().put(requestType, url);
            setup.getStopMonitoringRefValues().add(ref);
            subscriptionManager.addSubscription(globalSubscriptionId, setup);
        }
    }

}
