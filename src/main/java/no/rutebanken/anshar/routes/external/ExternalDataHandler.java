package no.rutebanken.anshar.routes.external;

import no.rutebanken.anshar.data.DiscoveryCache;
import no.rutebanken.anshar.data.util.TimingTracer;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.siri.handlers.inbound.EstimatedTimetableInbound;
import no.rutebanken.anshar.routes.siri.handlers.inbound.SituationExchangeInbound;
import no.rutebanken.anshar.routes.siri.handlers.inbound.StopMonitoringInbound;
import no.rutebanken.anshar.routes.siri.handlers.inbound.VehicleMonitoringInbound;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import org.apache.camel.Exchange;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.*;

import javax.xml.bind.JAXBException;
import javax.xml.stream.XMLStreamException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static no.rutebanken.anshar.routes.validation.validators.Constants.DATASET_ID_HEADER_NAME;
import static no.rutebanken.anshar.routes.validation.validators.Constants.URL_HEADER_NAME;

@Service
public class ExternalDataHandler {

    private static final Logger logger = LoggerFactory.getLogger(ExternalDataHandler.class);

    private static final int DEFAULT_HEARTBEAT_SECONDS = 300;

    @Autowired
    private VehicleMonitoringInbound vehicleMonitoringInbound;

    @Autowired
    private SituationExchangeInbound situationExchangeInbound;

    @Autowired
    private StopMonitoringInbound stopMonitoringInbound;

    @Autowired
    private EstimatedTimetableInbound estimatedTimetableInbound;

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Autowired
    private PrometheusMetricsService metrics;

    @Autowired
    private DiscoveryCache discoveryCache;


    public void processIncomingSiriSM(Exchange e) {
        InputStream xml = e.getIn().getBody(InputStream.class);
        try {
            TimingTracer timingTracer = new TimingTracer("externalDataHandler-SM");

            Siri siri = SiriValueTransformer.parseXml(xml);
            timingTracer.mark("siri transform");

            String datasetId = e.getIn().getHeader(DATASET_ID_HEADER_NAME, String.class);
            String url = e.getIn().getHeader(URL_HEADER_NAME, String.class);

            if (StringUtils.isEmpty(datasetId)) {
                logger.error("No datasetId were specified in external SM data");
                return;
            }

            checkAndCreateSMSubscription(siri, datasetId, url);

            timingTracer.mark("subscription created");

            List<MonitoredStopVisit> stopVisitToIngest = collectStopVisits(siri);
            timingTracer.mark("collectStopVisit");
            metrics.registerIncomingDataFromExternalSource(SiriDataType.STOP_MONITORING, datasetId, stopVisitToIngest.size());

            timingTracer.mark("metrics");

            if (stopVisitToIngest.size() > 0) {
                stopMonitoringInbound.ingestStopVisits(datasetId, stopVisitToIngest);
            }

            timingTracer.mark("ingest completed");

            List<MonitoredStopVisitCancellation> stopVisitToCancel = collectStopVisitsCancellations(siri);

            if (stopVisitToCancel.size() > 0) {
                stopMonitoringInbound.cancelStopVisits(datasetId, stopVisitToCancel);
            }
            timingTracer.mark("cancel ingest completed");

            if (timingTracer.getTotalTime() > 3000) {
                logger.debug(timingTracer.toString());
            }

        } catch (JAXBException | XMLStreamException jaxbException) {
            logger.error("Error while unmarshalling siri message from external", e);
        }
    }

    public void processIncomingSiriET(Exchange e) {
        InputStream xml = e.getIn().getBody(InputStream.class);
        try {
            Siri siri = SiriValueTransformer.parseXml(xml);
            String datasetId = e.getIn().getHeader(DATASET_ID_HEADER_NAME, String.class);
            String url = e.getIn().getHeader(URL_HEADER_NAME, String.class);

            if (StringUtils.isEmpty(datasetId)) {
                logger.error("No datasetId were specified in external SM data");
                return;
            }

            checkAndCreateETSubscription(siri, datasetId, url);

            List<EstimatedVehicleJourney> etToIngest = collectEstimatedTimeTables(siri);


            if (etToIngest.size() > 0) {
                estimatedTimetableInbound.ingestEstimatedTimeTables(datasetId, etToIngest);
            }

        } catch (JAXBException | XMLStreamException jaxbException) {
            logger.error("Error while unmarshalling siri message from external", e);
        }
    }

    private List<EstimatedVehicleJourney> collectEstimatedTimeTables(Siri siri) {
        List<EstimatedVehicleJourney> results = new ArrayList<>();
        if (siri.getServiceDelivery() != null && siri.getServiceDelivery().getEstimatedTimetableDeliveries() != null) {
            for (EstimatedTimetableDeliveryStructure estimatedTimetableDelivery : siri.getServiceDelivery().getEstimatedTimetableDeliveries()) {
                if (estimatedTimetableDelivery.getEstimatedJourneyVersionFrames() != null) {
                    for (EstimatedVersionFrameStructure estimatedJourneyVersionFrame : estimatedTimetableDelivery.getEstimatedJourneyVersionFrames()) {
                        results.addAll(estimatedJourneyVersionFrame.getEstimatedVehicleJourneies());
                    }
                }
            }
        }
        return results;
    }

    private void checkAndCreateETSubscription(Siri siri, String datasetId, String url) {
        EstimatedVehicleJourney vehJourn = siri.getServiceDelivery().getEstimatedTimetableDeliveries().get(0).getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().get(0);
        String subscriptionId = vehJourn.getEstimatedCalls().getEstimatedCalls().get(0).getStopPointRef().getValue();

        if (!subscriptionManager.isEstimatedTimetableSubscriptionExisting(subscriptionId, datasetId)) {

            //there is no subscription for this stop. Need to create one
            SubscriptionSetup setup = new SubscriptionSetup();
            setup.setDatasetId(datasetId);
            setup.setHeartbeatIntervalSeconds(DEFAULT_HEARTBEAT_SECONDS);
            setup.setRequestorRef("OKINA-EXTERNAL-SIRI");
            setup.setAddress(url);
            setup.setServiceType(SubscriptionSetup.ServiceType.REST);
            setup.setSubscriptionMode(SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE);
            setup.setDurationOfSubscriptionHours(24);
            setup.setVendor("OKINA");
            setup.setContentType("ExternalSiri");
            setup.setActive(true);
            setup.getStopMonitoringRefValues().add(subscriptionId);
            String etSubscriptionId = "ET-" + subscriptionId;
            setup.setName(etSubscriptionId);
            setup.setSubscriptionType(SiriDataType.ESTIMATED_TIMETABLE);
            setup.setSubscriptionId(etSubscriptionId);
            Map<RequestType, String> urlMap = new HashMap<>();
            urlMap.put(RequestType.GET_ESTIMATED_TIMETABLE, url);
            setup.setUrlMap(urlMap);

            subscriptionManager.addSubscription(etSubscriptionId, setup);

        }
    }

    public void processIncomingSiriSX(Exchange e) {
        InputStream xml = e.getIn().getBody(InputStream.class);
        try {
            Siri siri = SiriValueTransformer.parseXml(xml);
            String datasetId = e.getIn().getHeader(DATASET_ID_HEADER_NAME, String.class);
            String url = e.getIn().getHeader(URL_HEADER_NAME, String.class);

            if (StringUtils.isEmpty(datasetId)) {
                logger.error("No datasetId were specified in external SX data");
                return;
            }

            checkAndCreateSXSubscription(siri, datasetId, url);
            List<PtSituationElement> situationsToIngest = collectSituations(siri);

            if (situationsToIngest.size() > 0) {
                situationExchangeInbound.ingestSituations(datasetId, situationsToIngest, true);
            }

        } catch (JAXBException | XMLStreamException jaxbException) {
            logger.error("Error while unmarshalling siri message from external", e);
        }
    }

    public void processIncomingSiriVM(Exchange e) {
        InputStream xml = e.getIn().getBody(InputStream.class);
        try {
            Siri siri = SiriValueTransformer.parseXml(xml);
            String datasetId = e.getIn().getHeader(DATASET_ID_HEADER_NAME, String.class);
            String url = e.getIn().getHeader(URL_HEADER_NAME, String.class);

            if (StringUtils.isEmpty(datasetId)) {
                logger.error("No datasetId were specified in external VM data");
                return;
            }

            checkAndCreateVMSubscription(siri, datasetId, url);
            List<VehicleActivityStructure> vehicleActivitiesToIngest = collectVehicleActivities(siri);

            if (vehicleActivitiesToIngest.size() > 0) {
                vehicleMonitoringInbound.ingestVehicleActivities(datasetId, vehicleActivitiesToIngest);
            }

        } catch (JAXBException | XMLStreamException jaxbException) {
            logger.error("Error while unmarshalling siri message from external", e);
        }
    }

    private void checkAndCreateSXSubscription(Siri siri, String datasetId, String url) {
        String subscriptionId = siri.getServiceDelivery().getSituationExchangeDeliveries().get(0).getSituations().getPtSituationElements().get(0).getSituationNumber().getValue();

        if (!subscriptionManager.isSituationExchangeSubscriptionExisting(subscriptionId, datasetId)) {

            //there is no subscription for this stop. Need to create one
            SubscriptionSetup setup = new SubscriptionSetup();
            setup.setDatasetId(datasetId);
            setup.setHeartbeatIntervalSeconds(DEFAULT_HEARTBEAT_SECONDS);
            setup.setRequestorRef("OKINA-EXTERNAL-SIRI");
            setup.setAddress(url);
            setup.setServiceType(SubscriptionSetup.ServiceType.REST);
            setup.setSubscriptionMode(SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE);
            setup.setDurationOfSubscriptionHours(24);
            setup.setVendor("OKINA");
            setup.setContentType("ExternalSiri");
            setup.setActive(true);

            setup.setName(subscriptionId);
            setup.setSubscriptionType(SiriDataType.SITUATION_EXCHANGE);
            setup.setSubscriptionId(subscriptionId);
            Map<RequestType, String> urlMap = new HashMap<>();
            urlMap.put(RequestType.GET_SITUATION_EXCHANGE, url);
            setup.setUrlMap(urlMap);

            subscriptionManager.addSubscription(subscriptionId, setup);

        }
    }

    private void checkAndCreateVMSubscription(Siri siri, String datasetId, String url) {
        String subscriptionId = siri.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().get(0).getVehicleMonitoringRef().getValue();

        if (!subscriptionManager.isVehicleMonitoringSubscriptionExisting(subscriptionId, datasetId)) {

            //there is no subscription for this stop. Need to create one
            SubscriptionSetup setup = new SubscriptionSetup();
            setup.setDatasetId(datasetId);
            setup.setHeartbeatIntervalSeconds(DEFAULT_HEARTBEAT_SECONDS);
            setup.setRequestorRef("OKINA-EXTERNAL-SIRI");
            setup.setAddress(url);
            setup.setServiceType(SubscriptionSetup.ServiceType.REST);
            setup.setSubscriptionMode(SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE);
            setup.setDurationOfSubscriptionHours(24);
            setup.setVendor("OKINA");
            setup.setContentType("ExternalSiri");
            setup.setActive(true);

            setup.setName(subscriptionId);
            setup.setSubscriptionType(SiriDataType.VEHICLE_MONITORING);
            setup.setSubscriptionId(subscriptionId);
            Map<RequestType, String> urlMap = new HashMap<>();
            urlMap.put(RequestType.GET_VEHICLE_MONITORING, url);
            setup.setUrlMap(urlMap);

            subscriptionManager.addSubscription(subscriptionId, setup);
            discoveryCache.addLine(datasetId, subscriptionId);

        }
    }


    private List<PtSituationElement> collectSituations(Siri siri) {
        List<PtSituationElement> resultList = new ArrayList<>();
        if (siri.getServiceDelivery() != null && siri.getServiceDelivery().getSituationExchangeDeliveries() != null) {

            for (SituationExchangeDeliveryStructure stopMonitoringDelivery : siri.getServiceDelivery().getSituationExchangeDeliveries()) {
                resultList.addAll(stopMonitoringDelivery.getSituations().getPtSituationElements());
            }
        }
        return resultList;
    }

    private List<MonitoredStopVisit> collectStopVisits(Siri siri) {
        List<MonitoredStopVisit> resultList = new ArrayList<>();
        if (siri.getServiceDelivery() != null && siri.getServiceDelivery().getStopMonitoringDeliveries() != null) {

            for (StopMonitoringDeliveryStructure stopMonitoringDelivery : siri.getServiceDelivery().getStopMonitoringDeliveries()) {
                resultList.addAll(stopMonitoringDelivery.getMonitoredStopVisits());
            }
        }
        return resultList;
    }

    private List<MonitoredStopVisitCancellation> collectStopVisitsCancellations(Siri siri) {
        List<MonitoredStopVisitCancellation> resultList = new ArrayList<>();
        if (siri.getServiceDelivery() != null && siri.getServiceDelivery().getStopMonitoringDeliveries() != null) {

            for (StopMonitoringDeliveryStructure stopMonitoringDelivery : siri.getServiceDelivery().getStopMonitoringDeliveries()) {
                resultList.addAll(stopMonitoringDelivery.getMonitoredStopVisitCancellations());
            }
        }
        return resultList;
    }

    private List<VehicleActivityStructure> collectVehicleActivities(Siri siri) {
        List<VehicleActivityStructure> resultList = new ArrayList<>();
        if (siri.getServiceDelivery() != null && siri.getServiceDelivery().getVehicleMonitoringDeliveries() != null) {

            for (VehicleMonitoringDeliveryStructure monitoringDeliveryStructure : siri.getServiceDelivery().getVehicleMonitoringDeliveries()) {
                resultList.addAll(monitoringDeliveryStructure.getVehicleActivities());
            }
        }
        return resultList;
    }


    private void checkAndCreateSMSubscription(Siri siri, String datasetId, String url) {

        String subscriptionId = siri.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().get(0).getMonitoringRef().getValue();


        if (!subscriptionManager.isStopMonitoringSubscriptionExisting(subscriptionId, datasetId)) {

            //there is no subscription for this stop. Need to create one
            SubscriptionSetup setup = new SubscriptionSetup();
            setup.setDatasetId(datasetId);
            setup.setHeartbeatIntervalSeconds(DEFAULT_HEARTBEAT_SECONDS);
            setup.setRequestorRef("OKINA-EXTERNAL-SIRI");
            setup.setAddress(url);
            setup.setServiceType(SubscriptionSetup.ServiceType.REST);
            setup.setSubscriptionMode(SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE);
            setup.setDurationOfSubscriptionHours(24);
            setup.setVendor("OKINA");
            setup.setContentType("ExternalSiri");
            setup.setActive(true);
            setup.getStopMonitoringRefValues().add(subscriptionId);

            setup.setName("SM-" + subscriptionId);
            setup.setSubscriptionType(SiriDataType.STOP_MONITORING);
            String smSubscriptionId = "SM-" + subscriptionId;
            setup.setSubscriptionId(smSubscriptionId);
            Map<RequestType, String> urlMap = new HashMap<>();
            urlMap.put(RequestType.GET_STOP_MONITORING, url);
            setup.setUrlMap(urlMap);
            discoveryCache.addStop(datasetId, subscriptionId);
            subscriptionManager.addSubscription(smSubscriptionId, setup);


        }
    }
}
