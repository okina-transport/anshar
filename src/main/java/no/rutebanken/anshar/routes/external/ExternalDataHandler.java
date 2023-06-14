package no.rutebanken.anshar.routes.external;

import jakarta.xml.bind.JAXBException;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
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


import javax.xml.stream.XMLStreamException;
import java.io.InputStream;
import java.util.*;

import static no.rutebanken.anshar.routes.validation.validators.Constants.DATASET_ID_HEADER_NAME;
import static no.rutebanken.anshar.routes.validation.validators.Constants.URL_HEADER_NAME;

@Service
public class ExternalDataHandler {

    private final Logger logger = LoggerFactory.getLogger(ExternalDataHandler.class);

    private static final int DEFAULT_HEARTBEAT_SECONDS = 300;

    @Autowired
    private SiriHandler handler;

    @Autowired
    private SubscriptionManager subscriptionManager;

    public void processIncomingSiriSM(Exchange e) {
        InputStream xml = e.getIn().getBody(InputStream.class);
        try {
            Siri siri = SiriValueTransformer.parseXml(xml);
            String datasetId = e.getIn().getHeader(DATASET_ID_HEADER_NAME, String.class);
            String url = e.getIn().getHeader(URL_HEADER_NAME, String.class);

            if (StringUtils.isEmpty(datasetId)){
                logger.error("No datasetId were specified in external SM data");
                return;
            }

            String subscriptionId = checkAndCreateSMSubscription(siri, datasetId, url);

            List<MonitoredStopVisit> stopVisitToIngest = collectStopVisits(siri);

            if (stopVisitToIngest.size() > 0){
                handler.ingestStopVisits(datasetId,stopVisitToIngest);
            }

        } catch (JAXBException | XMLStreamException jaxbException) {
            logger.error("Error while unmarshalling siri message from external", e);
        }
    }

    public void processIncomingSiriSX(Exchange e) {
        InputStream xml = e.getIn().getBody(InputStream.class);
        try {
            Siri siri = SiriValueTransformer.parseXml(xml);
            String datasetId = e.getIn().getHeader(DATASET_ID_HEADER_NAME, String.class);
            String url = e.getIn().getHeader(URL_HEADER_NAME, String.class);

            if (StringUtils.isEmpty(datasetId)){
                logger.error("No datasetId were specified in external SM data");
                return;
            }

            String subscriptionId = checkAndCreateSXSubscription(siri, datasetId, url);
            List<PtSituationElement> situationsToIngest = collectSituations(siri);

            if (situationsToIngest.size() > 0){
                handler.ingestSituations(datasetId, situationsToIngest);
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

            if (StringUtils.isEmpty(datasetId)){
                logger.error("No datasetId were specified in external VM data");
                return;
            }

            String subscriptionId = checkAndCreateVMSubscription(siri, datasetId, url);
            List<VehicleActivityStructure> vehicleActivitiesToIngest = collectVehicleActivities(siri);

            if (vehicleActivitiesToIngest.size() > 0){
                handler.ingestVehicleActivities(datasetId, vehicleActivitiesToIngest);
            }

        } catch (JAXBException | XMLStreamException jaxbException) {
            logger.error("Error while unmarshalling siri message from external", e);
        }
    }

    private String checkAndCreateSXSubscription(Siri siri, String datasetId, String url) {
        String subscriptionId = siri.getServiceDelivery().getSituationExchangeDeliveries().get(0).getSituations().getPtSituationElements().get(0).getSituationNumber().getValue();

        if (!subscriptionManager.isSituationExchangeSubscriptionExisting(subscriptionId, datasetId)){

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
            urlMap.put(RequestType.GET_SITUATION_EXCHANGE,url);
            setup.setUrlMap(urlMap);

            subscriptionManager.addSubscription(subscriptionId,setup);

        }
        return subscriptionId;
    }

    private String checkAndCreateVMSubscription(Siri siri, String datasetId, String url) {
        String subscriptionId = siri.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().get(0).getVehicleMonitoringRef().getValue();

        if (!subscriptionManager.isVehicleMonitoringSubscriptionExisting(subscriptionId, datasetId)){

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

        }
        return subscriptionId;
    }



    private List<PtSituationElement> collectSituations(Siri siri) {
        List<PtSituationElement> resultList = new ArrayList<>();
        if (siri.getServiceDelivery() != null && siri.getServiceDelivery().getSituationExchangeDeliveries() != null){

            for ( SituationExchangeDeliveryStructure stopMonitoringDelivery : siri.getServiceDelivery().getSituationExchangeDeliveries()) {
                resultList.addAll(stopMonitoringDelivery.getSituations().getPtSituationElements());
            }
        }
        return resultList;
    }

    private List<MonitoredStopVisit> collectStopVisits(Siri siri) {
        List<MonitoredStopVisit> resultList = new ArrayList<>();
        if (siri.getServiceDelivery() != null && siri.getServiceDelivery().getStopMonitoringDeliveries() != null){

            for (StopMonitoringDeliveryStructure stopMonitoringDelivery : siri.getServiceDelivery().getStopMonitoringDeliveries()) {
                resultList.addAll(stopMonitoringDelivery.getMonitoredStopVisits());
            }
        }
        return resultList;
    }

    private List<VehicleActivityStructure> collectVehicleActivities(Siri siri) {
        List<VehicleActivityStructure> resultList = new ArrayList<>();
        if (siri.getServiceDelivery() != null && siri.getServiceDelivery().getVehicleMonitoringDeliveries() != null){

            for (VehicleMonitoringDeliveryStructure monitoringDeliveryStructure : siri.getServiceDelivery().getVehicleMonitoringDeliveries()) {
                resultList.addAll(monitoringDeliveryStructure.getVehicleActivities());
            }
        }
        return resultList;
    }



    private String checkAndCreateSMSubscription(Siri siri, String datasetId, String url){

        String subscriptionId = siri.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().get(0).getMonitoringRef().getValue();


        if (!subscriptionManager.isStopMonitoringSubscriptionExisting(subscriptionId, datasetId)){

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
            setup.setStopMonitoringRefValue(subscriptionId);

            setup.setName(subscriptionId);
            setup.setSubscriptionType(SiriDataType.STOP_MONITORING);
            setup.setSubscriptionId(subscriptionId);
            Map<RequestType, String> urlMap = new HashMap<>();
            urlMap.put(RequestType.GET_STOP_MONITORING, url);
            setup.setUrlMap(urlMap);

            subscriptionManager.addSubscription(subscriptionId,setup);

        }
        return subscriptionId;
    }
}
