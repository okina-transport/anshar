package no.rutebanken.anshar.routes.siri.handlers.inbound;

import no.rutebanken.anshar.data.GeneralMessages;
import no.rutebanken.anshar.data.GeneralMessagesCancellations;
import no.rutebanken.anshar.routes.kafka.KafkaConfig;
import no.rutebanken.anshar.routes.kafka.KafkaRouteBuilder;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.routes.siri.converter.SxToGmCancellationInboundData;
import no.rutebanken.anshar.routes.siri.converter.SxToGmInboundData;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.GeneralMessage;
import uk.org.siri.siri21.GeneralMessageCancellation;
import uk.org.siri.siri21.GeneralMessageDeliveryStructure;
import uk.org.siri.siri21.ServiceDelivery;
import uk.org.siri.siri21.Siri;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static no.rutebanken.anshar.routes.validation.validators.Constants.DATASET_ID_HEADER_NAME;

@Service
public class GeneralMessageWriter {

    private final GeneralMessages generalMessages;
    private final GeneralMessagesCancellations generalMessageCancellations;
    private final ServerSubscriptionManager serverSubscriptionManager;
    private final KafkaConfig kafkaConfig;

    @Produce(KafkaRouteBuilder.SEND_GM_IN_TO_KAFKA)
    protected ProducerTemplate sendGmInToKafka;

    public GeneralMessageWriter(GeneralMessages generalMessages,
                                GeneralMessagesCancellations generalMessageCancellations,
                                ServerSubscriptionManager serverSubscriptionManager,
                                KafkaConfig kafkaConfig) {
        this.generalMessages = generalMessages;
        this.generalMessageCancellations = generalMessageCancellations;
        this.serverSubscriptionManager = serverSubscriptionManager;
        this.kafkaConfig = kafkaConfig;
    }

    public void ingestGeneralMessages(SxToGmInboundData sxToGmInboundData) {
        String datasetId = sxToGmInboundData.getDatasetId();
        List<GeneralMessage> incomingGeneralMessages = sxToGmInboundData.getIncoming();
        boolean publishToOutbound = sxToGmInboundData.isPublishToOutbound();
        Long inboundTime = sxToGmInboundData.getInboundTime();
        Collection<GeneralMessage> result = generalMessages.addAll(datasetId, incomingGeneralMessages);

        if (kafkaConfig.isKafkaEnabled() && kafkaConfig.isSendSiriGmInToKafka() && CollectionUtils.isNotEmpty(incomingGeneralMessages)) {
            Siri delivery = new Siri();
            ServiceDelivery serviceDel = new ServiceDelivery();
            GeneralMessageDeliveryStructure generalMessageDeliveryStructure = new GeneralMessageDeliveryStructure();
            generalMessageDeliveryStructure.getGeneralMessages().addAll(incomingGeneralMessages);
            serviceDel.getGeneralMessageDeliveries().add(generalMessageDeliveryStructure);
            delivery.setServiceDelivery(serviceDel);
            sendGmInToKafka.asyncRequestBodyAndHeader(sendGmInToKafka.getDefaultEndpoint(), delivery, DATASET_ID_HEADER_NAME, datasetId);
        }

        if (publishToOutbound && CollectionUtils.isNotEmpty(result)) {
            serverSubscriptionManager.pushUpdatesAsync(SiriDataType.GENERAL_MESSAGE, new ArrayList<>(result), datasetId, inboundTime);
        }
    }

    public void ingestGeneralMessagesCancellations(SxToGmCancellationInboundData sxToGmCancellationInboundData) {
        String datasetId = sxToGmCancellationInboundData.getDatasetId();
        List<GeneralMessageCancellation> cancellations = sxToGmCancellationInboundData.getIncoming();
        boolean publishToOutbound = sxToGmCancellationInboundData.isPublishToOutbound();
        Long inboundTime = sxToGmCancellationInboundData.getInboundTime();
        Collection<GeneralMessageCancellation> result = generalMessageCancellations.addAll(datasetId, cancellations);
        generalMessages.cancelGeneralMessages(datasetId, cancellations);
        if (kafkaConfig.isKafkaEnabled() && kafkaConfig.isSendSiriGmInToKafka() && CollectionUtils.isNotEmpty(cancellations)) {
            Siri delivery = new Siri();
            ServiceDelivery serviceDel = new ServiceDelivery();
            GeneralMessageDeliveryStructure generalMessageDeliveryStructure = new GeneralMessageDeliveryStructure();
            generalMessageDeliveryStructure.getGeneralMessageCancellations().addAll(cancellations);
            serviceDel.getGeneralMessageDeliveries().add(generalMessageDeliveryStructure);
            delivery.setServiceDelivery(serviceDel);
            sendGmInToKafka.asyncRequestBodyAndHeader(sendGmInToKafka.getDefaultEndpoint(), delivery, DATASET_ID_HEADER_NAME, datasetId);
        }
        if (publishToOutbound && CollectionUtils.isNotEmpty(result)) {
            serverSubscriptionManager.pushUpdatesAsync(SiriDataType.GENERAL_MESSAGE, new ArrayList<>(result), datasetId, inboundTime);
        }
    }
}
