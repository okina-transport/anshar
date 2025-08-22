package no.rutebanken.anshar.subscription.fakedata;

import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.routes.outbound.OutboundSubscriptionSetup;
import org.apache.camel.ProducerTemplate;
import uk.org.siri.siri21.Siri;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static no.rutebanken.anshar.routes.HttpParameter.SIRI_VERSION_HEADER_NAME;
import static no.rutebanken.anshar.routes.siri.Siri20RequestHandlerRoute.TRANSFORM_SOAP;
import static no.rutebanken.anshar.routes.validation.validators.Constants.DATASET_ID_HEADER_NAME;

/**
 * Runnable class that produces then sends SIRI data to a subscriber
 */
@Slf4j
public class FakeDataSenderRunnable implements Runnable {

    private final ProducerTemplate sendToExternalConsumer;
    private final FakeDataProducer producer;
    private final OutboundSubscriptionSetup setup;
    private final int nbIterations;

    public FakeDataSenderRunnable(ProducerTemplate sendToExternalConsumer, FakeDataProducer producer, OutboundSubscriptionSetup setup, int nbIterations) {
        this.sendToExternalConsumer = sendToExternalConsumer;
        this.producer = producer;
        this.setup = setup;
        this.nbIterations = nbIterations;
    }

    @Override
    public void run() {
        for (int i = 0; i < nbIterations; i++) {
            List<Siri> msgs = producer.produce(setup);
            for (Siri msg : msgs) {
                sendToExternalConsumer(msg);
            }
        }
    }

    private void sendToExternalConsumer(Siri siriToSend) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(DATASET_ID_HEADER_NAME, "DAT1");
        headers.put(SIRI_VERSION_HEADER_NAME, setup.getSiriVersion());
        headers.put("endpoint", setup.getAddress());
        headers.put("SubscriptionId", setup.getSubscriptionId());
        if (setup.isSOAPSubscription()) {
            headers.put(TRANSFORM_SOAP, TRANSFORM_SOAP);
        }
        log.info("Send SIRI to subscriber {}", setup);
        sendToExternalConsumer.asyncRequestBodyAndHeaders(sendToExternalConsumer.getDefaultEndpoint(), siriToSend, headers);
    }

}
