package no.rutebanken.anshar.routes.siri.handlers.inbound;

import no.rutebanken.anshar.data.GeneralMessages;
import no.rutebanken.anshar.data.GeneralMessagesCancellations;
import no.rutebanken.anshar.routes.kafka.KafkaConfig;
import no.rutebanken.anshar.routes.kafka.KafkaRouteBuilder;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.routes.siri.converter.GeneralMessageConverter;
import no.rutebanken.anshar.routes.siri.converter.GmFeed;
import no.rutebanken.anshar.routes.siri.converter.SxInboundData;
import no.rutebanken.anshar.routes.siri.handlers.Utils;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.commons.lang3.BooleanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.*;

import java.util.ArrayList;
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
    private final GeneralMessageConverter generalMessageConverter;
    private final SituationExchangeWriter situationExchangeWriter;

    @Produce(KafkaRouteBuilder.SEND_GM_IN_TO_KAFKA)
    protected ProducerTemplate sendGmInToKafka;


    public GeneralMessageInbound(GeneralMessages generalMessages,
                                 ServerSubscriptionManager serverSubscriptionManager,
                                 SubscriptionManager subscriptionManager,
                                 GeneralMessagesCancellations generalMessageCancellations,
                                 Utils utils,
                                 KafkaConfig kafkaConfig,
                                 GeneralMessageConverter generalMessageConverter,
                                 SituationExchangeWriter situationExchangeWriter) {
        this.generalMessages = generalMessages;
        this.serverSubscriptionManager = serverSubscriptionManager;
        this.subscriptionManager = subscriptionManager;
        this.generalMessageCancellations = generalMessageCancellations;
        this.utils = utils;
        this.kafkaConfig = kafkaConfig;
        this.generalMessageConverter = generalMessageConverter;
        this.situationExchangeWriter = situationExchangeWriter;
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

        if (BooleanUtils.isTrue(subscriptionSetup.getGenerateSX())) {
            List<PtSituationElement> generatedSx = generalMessageConverter.convertGeneralMessageToSx(new GmFeed(subscriptionSetup.getDatasetId(), addedOrUpdated, cancellationsAddedOrUpdated, subscriptionSetup.getPublishToDisplayAction()));
            situationExchangeWriter.write(SxInboundData.builder()
                    .datasetId(subscriptionSetup.getDatasetId())
                    .incomingSituations(generatedSx)
                    .convertSxToGm(false)
                    .build());
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


}
