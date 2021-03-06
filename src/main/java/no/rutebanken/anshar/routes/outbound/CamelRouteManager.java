/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package no.rutebanken.anshar.routes.outbound;

import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri20.ServiceDelivery;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.SituationExchangeDeliveryStructure;
import uk.org.siri.siri20.VehicleMonitoringDeliveryStructure;

import java.net.SocketException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static no.rutebanken.anshar.routes.siri.transformer.SiriOutputTransformerRoute.OUTPUT_ADAPTERS_HEADER_NAME;

@Service
public class CamelRouteManager {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private SiriHelper siriHelper;

    @Value("${anshar.default.max.elements.per.delivery:1000}")
    private int maximumSizePerDelivery;

    @Autowired
    private ServerSubscriptionManager subscriptionManager;

    @Produce(uri = "direct:send.to.external.subscription")
    protected ProducerTemplate siriSubscriptionProcessor;

    /**
     * Splits SIRI-data if applicable, and pushes data to external subscription
     * @param payload
     * @param subscriptionRequest
     */
    void pushSiriData(Siri payload, OutboundSubscriptionSetup subscriptionRequest) {
        String consumerAddress = subscriptionRequest.getAddress();
        if (consumerAddress == null) {
            logger.info("ConsumerAddress is null - ignoring data.");
            return;
        }

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(() -> {
            try {

                Siri filteredPayload = SiriHelper.filterSiriPayload(payload, subscriptionRequest.getFilterMap());

                int deliverySize = this.maximumSizePerDelivery;
                if (subscriptionRequest.getDatasetId() != null) {
                    deliverySize = Integer.MAX_VALUE;
                }

                List<Siri> splitSiri = siriHelper.splitDeliveries(filteredPayload, deliverySize);

                if (splitSiri.size() > 1) {
                    logger.info("Object split into {} deliveries for subscription.", splitSiri.size(), subscriptionRequest);
                }

                for (Siri siri : splitSiri) {
                    postDataToSubscription(siri, subscriptionRequest);
                }
            } catch (Exception e) {
                logger.info("Failed to push data for subscription {}: {}", subscriptionRequest, e);

                if (e.getCause() instanceof SocketException) {
                    logger.info("Recipient is unreachable - ignoring");
                } else {
                    String msg = e.getMessage();
                    if (e.getCause() != null) {
                        msg = e.getCause().getMessage();
                    }
                    logger.info("Exception caught when pushing SIRI-data: {}", msg);
                }
                subscriptionManager.pushFailedForSubscription(subscriptionRequest.getSubscriptionId());
            } finally {
                executorService.shutdown();
            }

        });
    }

    private void postDataToSubscription(Siri payload, OutboundSubscriptionSetup subscription) {

        if (serviceDeliveryContainsData(payload)) {
            String remoteEndPoint = subscription.getAddress();
            if (remoteEndPoint.startsWith("http://")) {
                //Translating URL to camel-format
                remoteEndPoint = "http4://" + remoteEndPoint.substring("http://".length());
            } else if (remoteEndPoint.startsWith("https://")) {
                //Translating URL to camel-format
                remoteEndPoint = "https4://" + remoteEndPoint.substring("https://".length());
            }

            Map<String, Object> headers = new HashMap<>();
            headers.put("endpoint", remoteEndPoint);
            headers.put("SubscriptionId", subscription.getSubscriptionId());
            headers.put(OUTPUT_ADAPTERS_HEADER_NAME, subscription.getValueAdapters());

            siriSubscriptionProcessor.sendBodyAndHeaders(payload, headers);
        }
    }

    /**
     * Returns false if payload contains an empty ServiceDelivery (i.e. no actual SIRI-data), otherwise it returns false
     * @param payload
     * @return
     */
    private boolean serviceDeliveryContainsData(Siri payload) {
        if (payload.getServiceDelivery() != null) {
            ServiceDelivery serviceDelivery = payload.getServiceDelivery();

            if (SiriHelper.containsValues(serviceDelivery.getSituationExchangeDeliveries())) {
                SituationExchangeDeliveryStructure deliveryStructure = serviceDelivery.getSituationExchangeDeliveries().get(0);
                return deliveryStructure.getSituations() != null &&
                        SiriHelper.containsValues(deliveryStructure.getSituations().getPtSituationElements());
            }

            if (SiriHelper.containsValues(serviceDelivery.getVehicleMonitoringDeliveries())) {
                VehicleMonitoringDeliveryStructure deliveryStructure = serviceDelivery.getVehicleMonitoringDeliveries().get(0);
                return deliveryStructure.getVehicleActivities() != null &&
                        SiriHelper.containsValues(deliveryStructure.getVehicleActivities());
            }

            if (SiriHelper.containsValues(serviceDelivery.getEstimatedTimetableDeliveries())) {
                EstimatedTimetableDeliveryStructure deliveryStructure = serviceDelivery.getEstimatedTimetableDeliveries().get(0);
                return (deliveryStructure.getEstimatedJourneyVersionFrames() != null &&
                        SiriHelper.containsValues(deliveryStructure.getEstimatedJourneyVersionFrames()) &&
                        SiriHelper.containsValues(deliveryStructure.getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies()));
            }
        }
        return true;
    }
}
