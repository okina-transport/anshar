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

package no.rutebanken.anshar.metrics;

import com.hazelcast.core.ReplicatedMap;
import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import no.rutebanken.anshar.data.EstimatedTimetables;
import no.rutebanken.anshar.data.Situations;
import no.rutebanken.anshar.data.VehicleActivities;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.org.siri.siri20.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri20.EstimatedVersionFrameStructure;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.SituationExchangeDeliveryStructure;
import uk.org.siri.siri20.VehicleMonitoringDeliveryStructure;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PrometheusMetricsService extends PrometheusMeterRegistry {
    private static final Logger logger = LoggerFactory.getLogger(PrometheusMetricsService.class);

    private final String METRICS_PREFIX = "app.anshar.";

    @Autowired
    protected SubscriptionManager manager;

    private final String DATA_COUNTER_NAME = METRICS_PREFIX + "data";
    private final String DATA_TOTAL_COUNTER_NAME = METRICS_PREFIX + "data.total";
    private final String DATA_SUCCESS_COUNTER_NAME = METRICS_PREFIX + "data.success";
    private final String DATA_EXPIRED_COUNTER_NAME = METRICS_PREFIX + "data.expired";
    private final String DATA_IGNORED_COUNTER_NAME = METRICS_PREFIX + "data.ignored";
    private final String DATA_OUTBOUND_COUNTER_NAME = METRICS_PREFIX + "data.outbound";

    public PrometheusMetricsService() {
        super(PrometheusConfig.DEFAULT);
    }

    @PreDestroy
    public void shutdown() throws IOException {
        this.close();
    }

    public void registerIncomingData(SiriDataType dataType, String agencyId, long total, long updated, long expired, long ignored) {

        List<Tag> counterTags = new ArrayList<>();
        counterTags.add(new ImmutableTag("dataType", dataType.name()));
        counterTags.add(new ImmutableTag("agency", agencyId));

        counter(DATA_TOTAL_COUNTER_NAME,   counterTags).increment(total);
        counter(DATA_SUCCESS_COUNTER_NAME, counterTags).increment(updated);
        counter(DATA_EXPIRED_COUNTER_NAME, counterTags).increment(expired);
        counter(DATA_IGNORED_COUNTER_NAME, counterTags).increment(ignored);
    }

    public void countOutgoingData(Siri siri, SubscriptionSetup.SubscriptionMode mode) {
        SiriDataType dataType = null;
        int count = 0;
        if (siri != null && siri.getServiceDelivery() != null) {
            if (siri.getServiceDelivery().getEstimatedTimetableDeliveries() != null &&
                    !siri.getServiceDelivery().getEstimatedTimetableDeliveries().isEmpty()) {
                EstimatedTimetableDeliveryStructure timetableDeliveryStructure = siri.getServiceDelivery().getEstimatedTimetableDeliveries().get(0);
                if (timetableDeliveryStructure != null && timetableDeliveryStructure.getEstimatedJourneyVersionFrames() != null &&
                        !timetableDeliveryStructure.getEstimatedJourneyVersionFrames().isEmpty()) {
                    EstimatedVersionFrameStructure estimatedVersionFrameStructure = timetableDeliveryStructure.getEstimatedJourneyVersionFrames().get(0);
                    if (estimatedVersionFrameStructure != null &&  estimatedVersionFrameStructure.getEstimatedVehicleJourneies() != null) {

                        dataType = SiriDataType.ESTIMATED_TIMETABLE;
                        count = estimatedVersionFrameStructure.getEstimatedVehicleJourneies().size();
                    }
                }
            } else if (siri.getServiceDelivery().getVehicleMonitoringDeliveries() != null &&
                        !siri.getServiceDelivery().getVehicleMonitoringDeliveries().isEmpty()) {
                VehicleMonitoringDeliveryStructure deliveryStructure = siri.getServiceDelivery().getVehicleMonitoringDeliveries().get(0);
                if (deliveryStructure != null) {
                    dataType = SiriDataType.VEHICLE_MONITORING;
                    count = deliveryStructure.getVehicleActivities().size();
                }
            } else if (siri.getServiceDelivery().getSituationExchangeDeliveries() != null &&
                        !siri.getServiceDelivery().getSituationExchangeDeliveries().isEmpty()) {
                SituationExchangeDeliveryStructure deliveryStructure = siri.getServiceDelivery().getSituationExchangeDeliveries().get(0);
                if (deliveryStructure != null && deliveryStructure.getSituations() != null) {
                    dataType = SiriDataType.SITUATION_EXCHANGE;
                    count = deliveryStructure.getSituations().getPtSituationElements().size();
                }
            }
        }

        countOutgoingData(dataType, mode, count);
    }

    private void countOutgoingData(SiriDataType dataType, SubscriptionSetup.SubscriptionMode mode, long objectCount) {
        if (dataType != null && objectCount > 0) {
            List<Tag> counterTags = new ArrayList<>();
            counterTags.add(new ImmutableTag("dataType", dataType.name()));
            counterTags.add(new ImmutableTag("mode", mode.name()));

            counter(DATA_OUTBOUND_COUNTER_NAME, counterTags).increment(objectCount);
        }
    }

    final Map<String, Integer> gaugeValues = new HashMap<>();

    public void gaugeDataset(SiriDataType subscriptionType, String agencyId, Integer count) {

        List<Tag> counterTags = new ArrayList<>();
        counterTags.add(new ImmutableTag("dataType", subscriptionType.name()));
        counterTags.add(new ImmutableTag("agency", agencyId));

        String key = "" + subscriptionType + agencyId;
        gaugeValues.put(key, count);

        gauge(DATA_COUNTER_NAME, counterTags, key, value -> gaugeValues.get(key));
    }

    @Override
    public String scrape() {
        update();
        return super.scrape();
    }

    public void update() {

        for (Meter meter : getMeters()) {
            if (DATA_COUNTER_NAME.equals(meter.getId().getName())) {
                this.remove(meter);
            }
        }

        EstimatedTimetables estimatedTimetables = ApplicationContextHolder.getContext().getBean(EstimatedTimetables.class);
        Map<String, Integer> datasetSize = estimatedTimetables.getLocalDatasetSize();
        for (String codespaceId : datasetSize.keySet()) {
            gaugeDataset(SiriDataType.ESTIMATED_TIMETABLE, codespaceId, datasetSize.get(codespaceId));
        }

        Situations situations = ApplicationContextHolder.getContext().getBean(Situations.class);
        datasetSize = situations.getLocalDatasetSize();
        for (String codespaceId : datasetSize.keySet()) {
            gaugeDataset(SiriDataType.SITUATION_EXCHANGE, codespaceId, datasetSize.get(codespaceId));
        }

        VehicleActivities vehicleActivities = ApplicationContextHolder.getContext().getBean(VehicleActivities.class);
        datasetSize = vehicleActivities.getLocalDatasetSize();
        for (String codespaceId : datasetSize.keySet()) {
            gaugeDataset(SiriDataType.VEHICLE_MONITORING, codespaceId, datasetSize.get(codespaceId));
        }

        long t1 = System.currentTimeMillis();
        ReplicatedMap<String, SubscriptionSetup> subscriptions = manager.subscriptions;
        for (SubscriptionSetup subscription : subscriptions.values()) {

            SiriDataType subscriptionType = subscription.getSubscriptionType();

            String gauge_baseName = METRICS_PREFIX + "subscription";

            String gauge_failing = gauge_baseName + ".failing";
            String gauge_data_failing = gauge_baseName + ".data_failing" ;


            List<Tag> counterTags = new ArrayList<>();
            counterTags.add(new ImmutableTag("dataType", subscriptionType.name()));
            counterTags.add(new ImmutableTag("agency", subscription.getDatasetId()));
            counterTags.add(new ImmutableTag("vendor", subscription.getVendor()));


            //Flag as failing when ACTIVE, and NOT HEALTHY
            gauge(gauge_failing, getTagsWithTimeLimit(counterTags, "now"), subscription.getSubscriptionId(), value ->
                    (manager.isActiveSubscription(subscription.getSubscriptionId()) &&
                            !manager.isSubscriptionHealthy(subscription.getSubscriptionId())) ? 1:0);

            //Set flag as data failing when ACTIVE, and NOT receiving data

            gauge(gauge_data_failing, getTagsWithTimeLimit(counterTags, "5min"), subscription.getSubscriptionId(), value ->
                    isSubscriptionFailing(manager, subscription, 5*60));

            gauge(gauge_data_failing, getTagsWithTimeLimit(counterTags, "15min"), subscription.getSubscriptionId(), value ->
                    isSubscriptionFailing(manager, subscription, 15*60));

            gauge(gauge_data_failing, getTagsWithTimeLimit(counterTags, "30min"), subscription.getSubscriptionId(), value ->
                    isSubscriptionFailing(manager, subscription, 30*60));
        }
    }

    private List<Tag> getTagsWithTimeLimit(List<Tag> counterTags, String timeLimit) {
        List<Tag> counterTagsClone = new ArrayList<>(counterTags);
        counterTagsClone.add(new ImmutableTag("timelimit", timeLimit));
        return counterTagsClone;
    }

    private double isSubscriptionFailing(SubscriptionManager manager, SubscriptionSetup subscription, int allowedSeconds) {
        if (manager.isActiveSubscription(subscription.getSubscriptionId()) &&
                !manager.isSubscriptionReceivingData(subscription.getSubscriptionId(), allowedSeconds)) {
            return 1;
        }
        return 0;
    }
}
