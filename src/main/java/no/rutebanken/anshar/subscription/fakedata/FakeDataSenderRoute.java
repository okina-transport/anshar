package no.rutebanken.anshar.subscription.fakedata;

import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.routes.BaseRouteBuilder;
import no.rutebanken.anshar.routes.outbound.OutboundSubscriptionSetup;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Camel route that periodically generates SIRI messages and send them to subscribers.
 * Generates SIRI SM & FM messages ATM.
 */
@Component
@Profile("fake-data-sender")
@Slf4j
public class FakeDataSenderRoute extends BaseRouteBuilder {

    // To generate fake data for a new SiriDataType:
    // 1. Generate a FakeDataProducer for this SiriDataType
    // 2. Add new SiriDataType / FakeDataProducer in this map
    // 3. There is no third step it's ready
    private final static Map<SiriDataType, FakeDataProducer> SDT_TO_FDP = Map.of(
            SiriDataType.STOP_MONITORING, new SiriSMDataProducer(),
            SiriDataType.FACILITY_MONITORING, new SiriFMDataProducer()
    );

    private final ServerSubscriptionManager serverSubscriptionManager;
    private final int fakeDataInterval;
    private final int nbOfMessage;
    @Produce(value = "direct:send.to.external.subscription")
    protected ProducerTemplate sendToExternalConsumer;

    protected FakeDataSenderRoute(AnsharConfiguration config, SubscriptionManager subscriptionManager, ServerSubscriptionManager serverSubscriptionManager, @Value("${anshar.fake.data.interval:60000}") int fakeDataInterval, @Value("${anshar.fake.data.nb.of.messages:10}") int nbOfMessage) {
        super(config, subscriptionManager);
        this.serverSubscriptionManager = serverSubscriptionManager;
        this.fakeDataInterval = fakeDataInterval;
        this.nbOfMessage = nbOfMessage;
    }

    @Override
    public void configure() throws Exception {
        singletonFrom("quartz://anshar/send_fake_data?trigger.repeatInterval=" + fakeDataInterval, "send_fake_data").process(e -> sendFakeDataToSubscribers()).end();
    }

    public void sendFakeDataToSubscribers() {
        log.info("Starting sending fake data. nb of messages: {}", nbOfMessage);
        for (SiriDataType siriDataType : SDT_TO_FDP.keySet()) {
            List<OutboundSubscriptionSetup> subsBySiriDataType = serverSubscriptionManager.getAllSubscriptions(siriDataType);
            if (CollectionUtils.isNotEmpty(subsBySiriDataType)) {
                log.info("Found {} {} subscriptions to send data to", subsBySiriDataType.size(), siriDataType);
                try (ExecutorService executor = Executors.newFixedThreadPool(subsBySiriDataType.size())) {
                    for (OutboundSubscriptionSetup sub : subsBySiriDataType) {
                        executor.submit(new FakeDataSenderRunnable(sendToExternalConsumer, SDT_TO_FDP.get(siriDataType), sub, nbOfMessage));
                    }
                }
            } else {
                log.info("No {} subscription to send data to", siriDataType);
            }
        }
    }

}
