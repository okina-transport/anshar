package no.rutebanken.anshar.subscription;

import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.routes.BaseRouteBuilder;
import no.rutebanken.anshar.routes.outbound.OutboundSubscriptionSetup;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.org.siri.siri21.*;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static no.rutebanken.anshar.routes.HttpParameter.SIRI_VERSION_HEADER_NAME;
import static no.rutebanken.anshar.routes.siri.Siri20RequestHandlerRoute.TRANSFORM_SOAP;
import static no.rutebanken.anshar.routes.validation.validators.Constants.DATASET_ID_HEADER_NAME;


/**
 *
 */
@Component
@Profile("fake-data-sender")
public class FakeDataSender extends BaseRouteBuilder {

    @Autowired
    ServerSubscriptionManager subscriptionManager;

    private static final Logger logger = LoggerFactory.getLogger(FakeDataSender.class);


    @Value("${anshar.fake.data.interval:60000}")
    private int fakeDataInterval;

    @Value("${anshar.fake.data.nb.of.messages:10}")
    private int nbOfMessage;


    @Produce(value = "direct:send.to.external.subscription")
    protected ProducerTemplate sendToExternalConsumer;


    protected FakeDataSender(AnsharConfiguration config, SubscriptionManager subscriptionManager) {
        super(config, subscriptionManager);
    }

    @Override
    public void configure() throws Exception {


        singletonFrom("quartz://anshar/send_fake_data?trigger.repeatInterval=" + fakeDataInterval, "send_fake_data")
                .bean(this, "sendFakeData")
                .end();

    }

    public void sendFakeData() throws InterruptedException {
        logger.info("Starting sending fake data. nb of messages:" + nbOfMessage);
        sendFakeSMData();
        logger.info("Fake data sent");
    }

    private void sendFakeSMData() throws InterruptedException {
        List<OutboundSubscriptionSetup> smSubscriptions = subscriptionManager.getAllSubscriptions(SiriDataType.STOP_MONITORING);

        ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

        logger.info("Nb of subscriptions:" + smSubscriptions.size());

        for (OutboundSubscriptionSetup smSubscription : smSubscriptions) {
            executorService.submit(() -> {
                launchFakeDataToClient(smSubscription);
            });
        }

//        while (executorService.getActiveCount() > 0) {
//            logger.info("Waiting for subscriptions to finish:" + executorService.getActiveCount());
//            Thread.sleep(2000);
//        }
    }

    private void launchFakeDataToClient(OutboundSubscriptionSetup smSubscription) {
        for (int i = 0; i < nbOfMessage; i++) {

            Set<String> stopRefFilter = getStopRefFilter(smSubscription);

            logger.info("nb of stop in filters:" + stopRefFilter.size());


            for (String stopRef : stopRefFilter) {
                Siri siriToSend = createFakeSiriMessage(stopRef);

                Map<String, Object> headers = new HashMap<>();
                headers.put(DATASET_ID_HEADER_NAME, "DAT1");
                headers.put(SIRI_VERSION_HEADER_NAME, smSubscription.getSiriVersion());
                headers.put("endpoint", smSubscription.getAddress());
                headers.put("SubscriptionId", smSubscription.getSubscriptionId());
                if (smSubscription.isSOAPSubscription()) {
                    headers.put(TRANSFORM_SOAP, TRANSFORM_SOAP);
                }
                sendToExternalConsumer.asyncRequestBodyAndHeaders(sendToExternalConsumer.getDefaultEndpoint(), siriToSend, headers);
            }
        }
    }

    private Set<String> getStopRefFilter(OutboundSubscriptionSetup smSubscription) {
        Set<String> stopRefs = smSubscription.getFilterMap().get(MonitoringRefStructure.class);

        for (Map.Entry<String, Map<Class, Set<String>>> stringMapEntry : smSubscription.getFilterMapByDataset().entrySet()) {
            if (stringMapEntry.getValue().containsKey(MonitoringRefStructure.class)) {
                stopRefs.addAll(stringMapEntry.getValue().get(MonitoringRefStructure.class));
            }
        }
        return stopRefs;
    }

    private Siri createFakeSiriMessage(String stopRef) {

        Siri siri = new Siri();
        ServiceDelivery servDel = new ServiceDelivery();
        StopMonitoringDeliveryStructure stopMonDel = new StopMonitoringDeliveryStructure();
        stopMonDel.setVersion("2.1");

        MonitoredStopVisit stopVisit1 = createFakeMonitoredStopVisit(stopRef);
        stopMonDel.getMonitoredStopVisits().add(stopVisit1);

        MonitoredStopVisit stopVisit2 = createFakeMonitoredStopVisit(stopRef);
        stopMonDel.getMonitoredStopVisits().add(stopVisit2);

        MonitoredStopVisit stopVisit3 = createFakeMonitoredStopVisit(stopRef);
        stopMonDel.getMonitoredStopVisits().add(stopVisit3);

        servDel.getStopMonitoringDeliveries().add(stopMonDel);
        siri.setServiceDelivery(servDel);


        return siri;
    }

    private MonitoredStopVisit createFakeMonitoredStopVisit(String stopRef) {
        String randomUUID = UUID.randomUUID().toString();
        MonitoredStopVisit stopVisit = new MonitoredStopVisit();
        MonitoringRefStructure monRefStruc = new MonitoringRefStructure();
        monRefStruc.setValue(stopRef);
        stopVisit.setMonitoringRef(monRefStruc);

        stopVisit.setRecordedAtTime(ZonedDateTime.now());
        stopVisit.setItemIdentifier(randomUUID);
        MonitoredVehicleJourneyStructure vehicleJourney = new MonitoredVehicleJourneyStructure();
        LineRef lineRef = new LineRef();
        lineRef.setValue(randomUUID);
        vehicleJourney.setLineRef(lineRef);
        FramedVehicleJourneyRefStructure frameVJ = new FramedVehicleJourneyRefStructure();
        frameVJ.setDatedVehicleJourneyRef(randomUUID);
        vehicleJourney.setFramedVehicleJourneyRef(frameVJ);
        vehicleJourney.setMonitored(true);

        MonitoredCallStructure monitoredCallStructure = new MonitoredCallStructure();
        StopPointRefStructure stopPointRefStructure = new StopPointRefStructure();
        stopPointRefStructure.setValue(stopRef);
        monitoredCallStructure.setStopPointRef(stopPointRefStructure);
        Random random = new Random();
        ZonedDateTime now = ZonedDateTime.now();
        int randomArrivalDelay = random.nextInt(60) + 1;
        ZonedDateTime randomArrival = now.minusMinutes(randomArrivalDelay);

        monitoredCallStructure.setAimedArrivalTime(randomArrival);
        monitoredCallStructure.setAimedDepartureTime(randomArrival);

        int randomDelay = random.nextInt(60) + 1;
        monitoredCallStructure.setExpectedArrivalTime(randomArrival.plusMinutes(randomDelay));
        monitoredCallStructure.setExpectedDepartureTime(randomArrival.plusMinutes(randomDelay));

        vehicleJourney.setMonitoredCall(monitoredCallStructure);


        stopVisit.setMonitoredVehicleJourney(vehicleJourney);


        return stopVisit;
    }
}
