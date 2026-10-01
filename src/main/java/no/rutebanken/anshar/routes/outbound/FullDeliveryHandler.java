package no.rutebanken.anshar.routes.outbound;

import jakarta.xml.bind.JAXBException;
import no.rutebanken.anshar.data.util.CustomSiriXml;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.outbound.model.OutSubscriptionIdentifier;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.util.SiriUtils;
import org.apache.camel.Header;
import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.org.siri.siri21.Siri;

import java.util.HashMap;
import java.util.Map;

@Component
public class FullDeliveryHandler {

    private static final Logger logger = LoggerFactory.getLogger(FullDeliveryHandler.class);


    private final InitialDeliveryGenerator initialDeliveryGenerator;
    private final ServerSubscriptionManager subscriptionManager;
    private final ProducerTemplate producerTemplate;
    private final PrometheusMetricsService metrics;

    @Value("${anshar.outbound.full.delivery.response.queue.name}")
    private String fullDeliveryResponseQueueName;

    public FullDeliveryHandler(InitialDeliveryGenerator initialDeliveryGenerator, ServerSubscriptionManager subscriptionManager, ProducerTemplate producerTemplate, PrometheusMetricsService metrics) {
        this.initialDeliveryGenerator = initialDeliveryGenerator;
        this.subscriptionManager = subscriptionManager;
        this.producerTemplate = producerTemplate;
        this.metrics = metrics;
    }

    public void handleFullDeliveryRequest(@Header("datasetId") String datasetId,
                                          @Header("outboundSubscriptionId") String outboundSubscriptionId,
                                          @Header("inboundTime") Long inboundTime) {
        logger.info("Full delivery request received for datasetId={}, outboundSubscriptionId={}, inboundTime={}",
                datasetId, outboundSubscriptionId, inboundTime);

        OutboundSubscriptionSetup subscriptionRequest = subscriptionManager.findSubscriptionById(outboundSubscriptionId);
        metrics.registerOutboundFullDeliveryRequestReceived(subscriptionRequest != null ? subscriptionRequest.getSubscriptionType() : null, datasetId);
        if (subscriptionRequest == null) {
            logger.error("Unable to find outbound subscription for id:{}", outboundSubscriptionId);
            return;
        }
        Map<String, Siri> completeDelivery = initialDeliveryGenerator.findInitialDeliveriesByDataset(subscriptionRequest);

        for (Map.Entry<String, Siri> deliveryWithDataset : completeDelivery.entrySet()) {
            SiriUtils.setSubscriberAndSubscriptionRef(deliveryWithDataset.getValue(), new OutSubscriptionIdentifier(subscriptionRequest.getSubscriberRef(), subscriptionRequest.getSubscriptionId()));
            sendFullDeliveryResponse(deliveryWithDataset.getKey(), deliveryWithDataset.getValue(), outboundSubscriptionId, inboundTime, subscriptionRequest.getSubscriptionType());
        }
    }

    private void sendFullDeliveryResponse(String datasetId, Siri delivery, String outboundSubscriptionId, Long inboundTime, SiriDataType dataType) {
        Map<String, Object> headers = new HashMap<>();
        headers.put("datasetId", datasetId);
        headers.put("outboundSubscriptionId", outboundSubscriptionId);
        if (inboundTime != null) {
            headers.put("inboundTime", inboundTime);
        }
        try {
            producerTemplate.sendBodyAndHeaders(fullDeliveryResponseQueueName, CustomSiriXml.toXml(delivery), headers);
            metrics.registerOutboundFullDeliveryResponseSent(dataType, datasetId);
        } catch (JAXBException e) {
            logger.error("Unable to convert full delivery to XML for datasetId={}, outboundSubscriptionId={}", datasetId, outboundSubscriptionId, e);
        }
    }
}
