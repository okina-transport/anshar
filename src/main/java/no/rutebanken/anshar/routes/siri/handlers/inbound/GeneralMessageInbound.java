package no.rutebanken.anshar.routes.siri.handlers.inbound;

import no.rutebanken.anshar.data.GeneralMessages;
import no.rutebanken.anshar.data.GeneralMessagesCancellations;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.routes.siri.handlers.Utils;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.GeneralMessage;
import uk.org.siri.siri21.GeneralMessageCancellation;
import uk.org.siri.siri21.GeneralMessageDeliveryStructure;
import uk.org.siri.siri21.Siri;

import java.util.ArrayList;
import java.util.List;

@Service
public class GeneralMessageInbound {

    private static final Logger logger = LoggerFactory.getLogger(GeneralMessageInbound.class);

    @Autowired
    private GeneralMessages generalMessages;

    @Autowired
    private ServerSubscriptionManager serverSubscriptionManager;

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Autowired
    private GeneralMessagesCancellations generalMessageCancellations;

    @Autowired
    private Utils utils;

    public boolean ingestGeneralMessage(SubscriptionSetup subscriptionSetup, Siri incoming) {
        List<GeneralMessageDeliveryStructure> generalDeliveries = incoming.getServiceDelivery().getGeneralMessageDeliveries();
        logger.debug("Got GM-delivery: Subscription [{}] ", subscriptionSetup);

        List<GeneralMessage> addedOrUpdated = new ArrayList<>();
        List<GeneralMessageCancellation> cancellationsAddedOrUpdated = new ArrayList<>();

        for (GeneralMessageDeliveryStructure generalDelivery : generalDeliveries) {
            addedOrUpdated.addAll(generalMessages.addAll(subscriptionSetup.getDatasetId(), generalDelivery.getGeneralMessages()));
            cancellationsAddedOrUpdated.addAll(generalMessageCancellations.addAll(subscriptionSetup.getDatasetId(), generalDelivery.getGeneralMessageCancellations()));
        }


        serverSubscriptionManager.pushUpdatesAsync(subscriptionSetup.getSubscriptionType(), addedOrUpdated, subscriptionSetup.getDatasetId());
        if (!cancellationsAddedOrUpdated.isEmpty()) {
            serverSubscriptionManager.pushUpdatesAsync(subscriptionSetup.getSubscriptionType(), cancellationsAddedOrUpdated, subscriptionSetup.getDatasetId());
        }
        subscriptionManager.incrementObjectCounter(subscriptionSetup, addedOrUpdated.size());
        logger.debug("Active GM-elements: {}, current delivery: {}, {}", generalMessages.getSize(), addedOrUpdated.size(), subscriptionSetup);

        return (!addedOrUpdated.isEmpty() || !cancellationsAddedOrUpdated.isEmpty());
    }

    public boolean ingestGeneralMessageFromApi(SiriDataType dataFormat, String datasetId, Siri incoming, List<SubscriptionSetup> subscriptionSetupList) {
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

        serverSubscriptionManager.pushUpdatesAsync(dataFormat, addedOrUpdated, datasetId);


        deliveryContainsData = !addedOrUpdated.isEmpty();

        for (SubscriptionSetup subscriptionSetup : subscriptionSetupList) {
            subscriptionManager.incrementObjectCounter(subscriptionSetup, 1);
//                        logger.info("Active GM-elements: {}, current delivery: {}, {}", situations.getSize(), addedOrUpdated.size(), subscriptionSetup);
        }
        return deliveryContainsData;
    }
}
