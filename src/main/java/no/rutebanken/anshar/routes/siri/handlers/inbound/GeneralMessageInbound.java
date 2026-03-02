package no.rutebanken.anshar.routes.siri.handlers.inbound;

import no.rutebanken.anshar.data.GeneralMessages;
import no.rutebanken.anshar.data.GeneralMessagesCancellations;
import no.rutebanken.anshar.routes.kafka.KafkaConfig;
import no.rutebanken.anshar.routes.kafka.KafkaRouteBuilder;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.routes.siri.handlers.Utils;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static no.rutebanken.anshar.routes.validation.validators.Constants.DATASET_ID_HEADER_NAME;

@Service
public class GeneralMessageInbound {

    private static final Logger logger = LoggerFactory.getLogger(GeneralMessageInbound.class);


    private final GeneralMessages generalMessages;
    private final ServerSubscriptionManager serverSubscriptionManager;
    private final SubscriptionManager subscriptionManager;
    private final GeneralMessagesCancellations generalMessageCancellations;
    private final Utils utils;
    private final KafkaConfig kafkaConfig;

    @Produce(KafkaRouteBuilder.SEND_GM_IN_TO_KAFKA)
    protected ProducerTemplate sendGmInToKafka;


    @Autowired
    public GeneralMessageInbound(GeneralMessages generalMessages, ServerSubscriptionManager serverSubscriptionManager, SubscriptionManager subscriptionManager, GeneralMessagesCancellations generalMessageCancellations, Utils utils, KafkaConfig kafkaConfig) {
        this.generalMessages = generalMessages;
        this.serverSubscriptionManager = serverSubscriptionManager;
        this.subscriptionManager = subscriptionManager;
        this.generalMessageCancellations = generalMessageCancellations;
        this.utils = utils;
        this.kafkaConfig = kafkaConfig;
    }

    public boolean ingestGeneralMessage(SubscriptionSetup subscriptionSetup, Siri incoming, Long inboundTime) {
        List<GeneralMessageDeliveryStructure> generalDeliveries = incoming.getServiceDelivery().getGeneralMessageDeliveries();
        logger.debug("Got GM-delivery: Subscription [{}] ", subscriptionSetup);

        if (kafkaConfig.isKafkaEnabled() && kafkaConfig.isSendSiriGmInToKafka()) {
            sendGmInToKafka.asyncRequestBodyAndHeader(sendGmInToKafka.getDefaultEndpoint(), incoming,
                    DATASET_ID_HEADER_NAME, subscriptionSetup.getDatasetId());
        }


        List<GeneralMessage> addedOrUpdated = new ArrayList<>();
        List<GeneralMessageCancellation> cancellationsAddedOrUpdated = new ArrayList<>();

        for (GeneralMessageDeliveryStructure generalDelivery : generalDeliveries) {
            addedOrUpdated.addAll(generalMessages.addAll(subscriptionSetup.getDatasetId(), generalDelivery.getGeneralMessages()));
            cancellationsAddedOrUpdated.addAll(generalMessageCancellations.addAll(subscriptionSetup.getDatasetId(), generalDelivery.getGeneralMessageCancellations()));
        }


        serverSubscriptionManager.pushUpdatesAsync(subscriptionSetup.getSubscriptionType(), addedOrUpdated, subscriptionSetup.getDatasetId(), inboundTime);
        if (!cancellationsAddedOrUpdated.isEmpty()) {
            serverSubscriptionManager.pushUpdatesAsync(subscriptionSetup.getSubscriptionType(), cancellationsAddedOrUpdated, subscriptionSetup.getDatasetId(), inboundTime);
        }
        subscriptionManager.incrementObjectCounter(subscriptionSetup, addedOrUpdated.size());
        logger.debug("Active GM-elements: {}, current delivery: {}, {}", generalMessages.getSize(), addedOrUpdated.size(), subscriptionSetup);

        return (!addedOrUpdated.isEmpty() || !cancellationsAddedOrUpdated.isEmpty());
    }

    public boolean ingestGeneralMessageFromApi(SiriDataType dataFormat, String datasetId, Siri incoming, List<SubscriptionSetup> subscriptionSetupList, Long inboundTime) {
        boolean deliveryContainsData;
        List<GeneralMessageDeliveryStructure> generalMessageDeliveries = incoming.getServiceDelivery().getGeneralMessageDeliveries();
        logger.info("Got GM-delivery: Subscription [{}]", subscriptionSetupList);

        List<GeneralMessage> addedOrUpdated = new ArrayList<>();
        if (generalMessageDeliveries != null) {
            generalMessageDeliveries.forEach(gm -> {
                        if (gm != null) {
                            if (gm.isStatus() != null && !gm.isStatus()) {
                                logger.info(utils.getErrorContents(gm.getErrorCondition()));
                            } else {
                                if (gm.getGeneralMessages() != null && gm.getGeneralMessages() != null) {
                                    addedOrUpdated.addAll(generalMessages.addAll(datasetId, gm.getGeneralMessages()));
                                }
                            }
                        }
                    }
            );
        }

        serverSubscriptionManager.pushUpdatesAsync(dataFormat, addedOrUpdated, datasetId, inboundTime);


        deliveryContainsData = !addedOrUpdated.isEmpty();

        for (SubscriptionSetup subscriptionSetup : subscriptionSetupList) {
            subscriptionManager.incrementObjectCounter(subscriptionSetup, 1);
//                        logger.info("Active GM-elements: {}, current delivery: {}, {}", situations.getSize(), addedOrUpdated.size(), subscriptionSetup);
        }
        return deliveryContainsData;
    }


    public void ingestGeneralMessages(String datasetId, List<GeneralMessage> incomingSituations, boolean publishToOutbound) {
        ingestGeneralMessages(datasetId, incomingSituations, publishToOutbound, null);

    }

    public void ingestGeneralMessages(String datasetId, List<GeneralMessage> incomingGeneralMessages, boolean publishToOutbound, Long inboundTime) {
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

    public void ingestGeneralMessagesCancellations(String datasetId, List<GeneralMessageCancellation> cancellations, boolean publishToOutbound, Long inboundTime) {
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
