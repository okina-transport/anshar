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

package no.rutebanken.anshar.subscription;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.map.IMap;
import com.hazelcast.replicatedmap.ReplicatedMap;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.config.DiscoverySubscription;
import no.rutebanken.anshar.data.*;
import no.rutebanken.anshar.routes.health.HealthManager;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import no.rutebanken.anshar.util.SiriUtils;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.ExchangeBuilder;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.Siri;

import java.math.BigInteger;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static no.rutebanken.anshar.routes.validation.validators.Constants.*;
import static no.rutebanken.anshar.subscription.SiriDataType.*;

@Service
@EnableScheduling
public class SubscriptionManager implements CamelContextAware {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionManager.class);
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private final Set<String> gtfsSubscriptions = new HashSet<>();
    private final Map<String, String> siriAPISubscriptions = new HashMap<>();
    private final ReplicatedMap<String, SubscriptionSetup> subscriptions;
    private final IMap<String, java.time.Instant> activatedTimestamp;
    private final AnsharConfiguration configuration;
    private final ReplicatedMap<String, Instant> lastActivity;
    private final ReplicatedMap<String, java.time.Instant> dataReceived;
    private final IMap<String, Long> receivedBytes;
    private final String environment;
    private final IMap<String, Integer> hitcount;
    private final IMap<String, BigInteger> objectCounter;
    private final SiriObjectFactory siriObjectFactory;
    private final HealthManager healthManager;
    private final Situations sx;
    private final EstimatedTimetables et;
    private final VehicleActivities vm;
    private final MonitoredStopVisits sm;
    private final GeneralMessages gm;
    private final FacilityMonitoring fm;
    private final IMap<String, Set<SiriObjectStorageKey>> sxChanges;
    private final IMap<String, Set<SiriObjectStorageKey>> etChanges;
    private final IMap<String, Set<SiriObjectStorageKey>> vmChanges;
    private final IMap<String, Set<SiriObjectStorageKey>> smChanges;
    private final IMap<String, Set<SiriObjectStorageKey>> gmChanges;
    private final RequestorRefRepository requestorRefRepository;
    private final DatasetService datasetService;
    private final SubscriptionConfig subscriptionConfig;
    private final int unresponsiveDelay;
    private final boolean disableUnresponsiveCheckSx;
    private final Map<String, Class> mappingAdaptersById = new HashMap<>();
    private CamelContext camelContext;

    public SubscriptionManager(@Qualifier("getSubscriptionsMap") ReplicatedMap<String, SubscriptionSetup> subscriptions, @Qualifier("getActivatedTimestampMap") IMap<String, Instant> activatedTimestamp,
                               AnsharConfiguration configuration, @Qualifier("getLastActivityMap") ReplicatedMap<String, Instant> lastActivity,
                               @Qualifier("getDataReceivedMap") ReplicatedMap<String, Instant> dataReceived, @Qualifier("getReceivedBytesMap") IMap<String, Long> receivedBytes, @Value("${anshar.environment}") String environment,
                               @Qualifier("getHitcountMap") IMap<String, Integer> hitcount, IMap<String, BigInteger> objectCounter,
                               SiriObjectFactory siriObjectFactory, HealthManager healthManager, Situations sx, EstimatedTimetables et, VehicleActivities vm, MonitoredStopVisits sm, GeneralMessages gm, FacilityMonitoring fm,
                               @Qualifier("getSituationChangesMap") IMap<String, Set<SiriObjectStorageKey>> sxChanges, @Qualifier("getEstimatedTimetableChangesMap") IMap<String, Set<SiriObjectStorageKey>> etChanges,
                               @Qualifier("getVehicleChangesMap") IMap<String, Set<SiriObjectStorageKey>> vmChanges, @Qualifier("getMonitoredStopVisitChangesMap") IMap<String, Set<SiriObjectStorageKey>> smChanges,
                               @Qualifier("getGeneralMessagesChangesMap") IMap<String, Set<SiriObjectStorageKey>> gmChanges,
                               RequestorRefRepository requestorRefRepository, DatasetService datasetService, SubscriptionConfig subscriptionConfig,
                               @Value("${anshar.subscription.unresponsive.delay.min:15}") int unresponsiveDelay,
                               @Value("${disable.check.unresponsive.subscription.sx:false}") boolean disableUnresponsiveCheckSx) {
        this.subscriptions = subscriptions;
        this.activatedTimestamp = activatedTimestamp;
        this.configuration = configuration;
        this.lastActivity = lastActivity;
        this.dataReceived = dataReceived;
        this.receivedBytes = receivedBytes;
        this.environment = environment;
        this.hitcount = hitcount;
        this.objectCounter = objectCounter;
        this.siriObjectFactory = siriObjectFactory;
        this.healthManager = healthManager;
        this.sx = sx;
        this.et = et;
        this.vm = vm;
        this.sm = sm;
        this.gm = gm;
        this.fm = fm;
        this.sxChanges = sxChanges;
        this.etChanges = etChanges;
        this.vmChanges = vmChanges;
        this.smChanges = smChanges;
        this.gmChanges = gmChanges;
        this.requestorRefRepository = requestorRefRepository;
        this.datasetService = datasetService;
        this.subscriptionConfig = subscriptionConfig;
        this.unresponsiveDelay = unresponsiveDelay;
        this.disableUnresponsiveCheckSx = disableUnresponsiveCheckSx;
    }


    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
        logger.info("ShutdownStrategy: {}", camelContext.getShutdownStrategy());
    }


    public void addMappingAdapters(Map<String, Class> mappingAdaptersById) {
        this.mappingAdaptersById.putAll(mappingAdaptersById);
    }

    public void addSubscription(String subscriptionId, SubscriptionSetup setup) {

        subscriptions.computeIfAbsent(subscriptionId, k -> {
            logger.info("Subscription added to manager:{}", subscriptionId);
            return setup;
        });
    }

    public boolean removeSubscription(String subscriptionId) {
        return removeSubscription(subscriptionId, false);
    }

    public boolean removeSubscription(String subscriptionId, boolean force) {
        SubscriptionSetup setup = subscriptions.remove(subscriptionId);

        boolean found = (setup != null);

        if (force) {
            logger.info("Completely deleting subscription by request.");
            activatedTimestamp.remove(subscriptionId);
            lastActivity.remove(subscriptionId);
            hitcount.remove(subscriptionId);
            objectCounter.remove(subscriptionId);
        } else if (found) {
            addSubscription(subscriptionId, setup);
        }

        //   logStats();

        logger.info("Removed subscription {}, found: {}", (setup != null ? setup.toString() : subscriptionId), found);
        return found;
    }

    public boolean touchSubscription(String subscriptionId) {
        return touchSubscription(subscriptionId, null);
    }

    public boolean touchSubscription(String subscriptionId, boolean shouldLogSuccess) {
        return touchSubscription(subscriptionId, null, shouldLogSuccess);
    }


    public boolean touchSubscription(String subscriptionId, String monitoredRef) {
        return touchSubscription(subscriptionId, monitoredRef, true);
    }

    public boolean touchSubscription(String subscriptionId, String monitoredRef, boolean shouldLogSuccess) {

        Optional<DiscoverySubscription> discoverySubscriptionOpt = getDiscoverySubscription(subscriptionId);
        if (discoverySubscriptionOpt.isPresent()) {
            return touchDiscoverySubscription(discoverySubscriptionOpt.get(), monitoredRef);
        }

        SubscriptionSetup setup = subscriptions.get(subscriptionId);
        hit(subscriptionId);

        boolean success = (setup != null);

        if (shouldLogSuccess) {
            if (monitoredRef != null) {
                logger.debug("Touched monitoredObjects:{}", monitoredRef);
            } else {
                logger.debug("Touched subscription {}, success:{}", setup, success);
            }
        }

        if (success) {
            lastActivity.put(subscriptionId, Instant.now());
        }

        // logStats();
        return success;
    }

    private boolean touchDiscoverySubscription(DiscoverySubscription discoverySubscription, String monitoredRef) {
        Optional<SubscriptionSetup> childSubscriptionOpt = Optional.empty();

        if (STOP_MONITORING.equals(discoverySubscription.getDiscoveryType())) {
            childSubscriptionOpt = getChildSubscriptionIdFromMonitoringRefs(discoverySubscription, monitoredRef != null ? List.of(monitoredRef) : new ArrayList<>());

        } else if (VEHICLE_MONITORING.equals(discoverySubscription.getDiscoveryType())) {
            childSubscriptionOpt = getChildSubscriptionIdFromLineRefs(discoverySubscription, monitoredRef != null ? List.of(monitoredRef) : new ArrayList<>());
        }

        childSubscriptionOpt.ifPresent(childSusbcription -> touchSubscription(childSusbcription.getSubscriptionId()));

        return false;
    }

    /**
     * Touches subscription if reported serviceStartedTime is BEFORE last activity.
     * If not, subscription is removed to trigger reestablishing subscription
     *
     * @param subscriptionId
     * @param serviceStartedTime
     * @param monitoredRef
     * @return
     */
    public boolean touchSubscription(String subscriptionId, ZonedDateTime serviceStartedTime, String monitoredRef) {
        SubscriptionSetup setup = subscriptions.get(subscriptionId);
        if (setup != null && serviceStartedTime != null) {
            Instant lastSubscriptionActivity = lastActivity.get(subscriptionId);
            if (lastSubscriptionActivity == null || serviceStartedTime.toInstant().isBefore(lastSubscriptionActivity)) {
                logger.info("Remote Service startTime ({}) is before lastSubscriptionActivity ({}) for subscription [{}]", serviceStartedTime, lastSubscriptionActivity, setup);
                return touchSubscription(subscriptionId, monitoredRef);
            } else {
                logger.info("Remote service has been restarted, forcing subscription to be restarted [{}]", setup);
            }
        }
        return false;
    }

    private void logStats() {
        String stats = "Active subscriptions: " + subscriptions.size();
        logger.debug(stats);
    }

    public SubscriptionSetup get(String subscriptionId) {

        return subscriptions.get(subscriptionId);
    }

    public List<SubscriptionSetup> getAll(List<String> subscriptionIds) {
        List<SubscriptionSetup> subscriptionSetupList = new ArrayList<>();
        for (String subscriptionId : subscriptionIds) {
            subscriptionSetupList.add(subscriptions.get(subscriptionId));
        }
        return subscriptionSetupList;
    }


    /**
     * Returns all subscriptions matching the type given as parameter     *
     *
     * @param type the data type to search
     * @return the list of subscriptions matching the data type
     */
    public List<SubscriptionSetup> getAllSubscriptions(SiriDataType type) {

        if (type == null) {
            throw new IllegalArgumentException("Type must be specified to search in subscriptions");
        }

        return subscriptions.values().stream()
                .filter(subscription -> type.equals(subscription.getSubscriptionType()))
                .collect(Collectors.toList());
    }

    public JSONObject getSubscriptionsForCodespace(String codespace) {
        JSONObject jsonSubscriptions = new JSONObject();
        JSONArray filteredSubscriptions = new JSONArray();

        filteredSubscriptions.addAll(subscriptions.values().stream()
                .filter(subscription -> subscription.getDatasetId().equalsIgnoreCase(codespace))
                .map(this::getJsonObject)
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));

        jsonSubscriptions.put("subscriptions", filteredSubscriptions);
        JSONObject configObject = new JSONObject();

        configObject.put("persistPeriodHours", configuration.getNumberOfHoursToKeepValidation());
        jsonSubscriptions.put("config", configObject);

        return jsonSubscriptions;
    }

    private void hit(String subscriptionId) {
        int counter = (hitcount.get(subscriptionId) != null ? hitcount.get(subscriptionId) : 0);
        hitcount.put(subscriptionId, counter + 1);
    }

    public void incrementObjectCounter(SubscriptionSetup subscriptionSetup, int size) {

        String subscriptionId = subscriptionSetup.getSubscriptionId();
        if (subscriptionId != null) {
            BigInteger counter = (objectCounter.get(subscriptionId) != null ? objectCounter.get(subscriptionId) : BigInteger.valueOf(0));
            objectCounter.put(subscriptionId, counter.add(BigInteger.valueOf(size)));
        }
    }


    public Instant getLastDataReceived(String subscriptionId) {
        return dataReceived.get(subscriptionId);
    }


    public boolean isSubscriptionRegistered(String subscriptionId) {
        return subscriptions.containsKey(subscriptionId);
    }


    /**
     * Indicates if a lineRef is available to request or not
     *
     * @param lineRef
     * @return true: lineRef can be requested
     * false : lineRef is not existing
     */
    public boolean isLineRefExistingInSubscriptions(String lineRef) {
        for (SubscriptionSetup subscription : subscriptions.values()) {
            if (subscription.getLineRefValues().contains(lineRef))
                return true;
        }
        return false;
    }


    public boolean isGTFSRTSubscriptionExisting(String subscriptionId) {
        return gtfsSubscriptions.contains(subscriptionId);
    }

    public void addGTFSRTSubscription(String subscriptionId) {
        gtfsSubscriptions.add(subscriptionId);
    }

    public boolean isSiriAPISubscriptionExisting(String monitoringRef) {
        return siriAPISubscriptions.containsKey(monitoringRef);
    }

    public void addSiriAPISubscription(String monitoringRef, String subcriptionId) {
        siriAPISubscriptions.put(monitoringRef, subcriptionId);
    }

    public String getSubscriptionForMonitoringRef(String monitoringRef) {
        return siriAPISubscriptions.get(monitoringRef);
    }

    public boolean isStopMonitoringSubscriptionExisting(String stopMonitoringRef, String datasetId) {
        return getAllSubscriptions(SiriDataType.STOP_MONITORING).stream()
                .anyMatch(subscription -> subscription.getStopMonitoringRefValues().contains(stopMonitoringRef) && datasetId.equals(subscription.getDatasetId()));
    }

    public boolean isEstimatedTimetableSubscriptionExisting(String stopMonitoringRef, String datasetId) {
        return getAllSubscriptions(ESTIMATED_TIMETABLE).stream()
                .anyMatch(subscription -> subscription.getStopMonitoringRefValues() != null && subscription.getStopMonitoringRefValues().contains(stopMonitoringRef) && datasetId.equals(subscription.getDatasetId()));
    }

    public boolean isSituationExchangeSubscriptionExisting(String situationNumber, String datasetId) {
        return getAllSubscriptions(SITUATION_EXCHANGE).stream()
                .anyMatch(subscription -> subscription.getSubscriptionId().equals(situationNumber) && datasetId.equals(subscription.getDatasetId()));
    }

    public boolean isVehicleMonitoringSubscriptionExisting(String vehicleMonitoringRef, String datasetId) {
        return getAllSubscriptions(VEHICLE_MONITORING).stream()
                .anyMatch(subscription -> subscription.getSubscriptionId().equals(vehicleMonitoringRef) && datasetId.equals(subscription.getDatasetId()));
    }

    public Optional<SubscriptionSetup> findStopMonitoringSubscription(String stopMonitoringRef, String datasetId) {
        return getAllSubscriptions(SiriDataType.STOP_MONITORING).stream()
                .filter(subscription -> subscription.getStopMonitoringRefValues().contains(stopMonitoringRef) && datasetId.equals(subscription.getDatasetId()))
                .findFirst();
    }

    /**
     * Indicates if a subscription is available to request or not
     *
     * @param subscriptionId
     * @return true: subscription is existing
     * false : subscription is not existing
     */
    public boolean isSubscriptionExisting(String subscriptionId) {
        for (SubscriptionSetup subscription : subscriptions.values()) {
            if (subscription.getSubscriptionId() != null && subscription.getSubscriptionId().equals(subscriptionId))
                return true;
        }
        return false;
    }

    public JSONObject buildStats() {
        logger.debug("Start building stats");
        JSONObject result = new JSONObject();
        JSONArray stats = new JSONArray();

        JSONArray etSubscriptions = new JSONArray();
        etSubscriptions.addAll(this.subscriptions.values().stream()
                .filter(subscriptionSetup -> subscriptionSetup.getSubscriptionType() == ESTIMATED_TIMETABLE)
                .map(this::getJsonObject)
                .filter(Objects::nonNull)
                .toList()
        );
        logger.debug("Built ET stats");

        JSONArray vmSubscriptions = new JSONArray();
        vmSubscriptions.addAll(this.subscriptions.values().stream()
                .filter(subscriptionSetup -> subscriptionSetup.getSubscriptionType() == VEHICLE_MONITORING)
                .map(this::getJsonObject)
                .filter(Objects::nonNull)
                .toList()
        );
        logger.debug("Built VM stats");

        JSONArray sxSubscriptions = new JSONArray();
        sxSubscriptions.addAll(this.subscriptions.values().stream()
                .filter(subscriptionSetup -> subscriptionSetup.getSubscriptionType() == SITUATION_EXCHANGE)
                .map(this::getJsonObject)
                .filter(Objects::nonNull)
                .toList()
        );
        logger.debug("Built SX stats");

        JSONArray smSubscriptions = new JSONArray();
        smSubscriptions.addAll(this.subscriptions.values().stream()
                .filter(subscriptionSetup -> subscriptionSetup.getSubscriptionType() == STOP_MONITORING)
                .map(this::getJsonObject)
                .filter(Objects::nonNull)
                .toList()
        );
        logger.debug("Built SM stats");

        JSONArray gmSubscriptions = new JSONArray();
        gmSubscriptions.addAll(this.subscriptions.values().stream()
                .filter(subscriptionSetup -> subscriptionSetup.getSubscriptionType() == GENERAL_MESSAGE)
                .map(this::getJsonObject)
                .filter(Objects::nonNull)
                .toList()
        );
        logger.debug("Built GM stats");

        JSONObject etType = new JSONObject();
        etType.put("typeName", "" + ESTIMATED_TIMETABLE);
        etType.put("subscriptions", etSubscriptions);
        JSONObject vmType = new JSONObject();
        vmType.put("typeName", "" + VEHICLE_MONITORING);
        vmType.put("subscriptions", vmSubscriptions);
        JSONObject sxType = new JSONObject();
        sxType.put("typeName", "" + SITUATION_EXCHANGE);
        sxType.put("subscriptions", sxSubscriptions);
        JSONObject smType = new JSONObject();
        smType.put("typeName", "" + STOP_MONITORING);
        smType.put("subscriptions", smSubscriptions);
        JSONObject gmType = new JSONObject();
        gmType.put("typeName", "" + GENERAL_MESSAGE);
        gmType.put("subscriptions", gmSubscriptions);

        stats.add(etType);
        stats.add(vmType);
        stats.add(sxType);
        stats.add(smType);
        stats.add(gmType);

        result.put("types", stats);

        JSONArray pollingClients = new JSONArray();
        logger.debug("Build polling stats");

        JSONObject etPolling = new JSONObject();
        etPolling.put("typeName", "" + ESTIMATED_TIMETABLE);
        etPolling.put("polling", getIdAndCount(etChanges, ESTIMATED_TIMETABLE));
        logger.debug("Built ET polling stats");
        JSONObject vmPolling = new JSONObject();
        vmPolling.put("typeName", "" + VEHICLE_MONITORING);
        vmPolling.put("polling", getIdAndCount(vmChanges, VEHICLE_MONITORING));
        logger.debug("Built VM polling stats");
        JSONObject sxPolling = new JSONObject();
        sxPolling.put("typeName", "" + SITUATION_EXCHANGE);
        sxPolling.put("polling", getIdAndCount(sxChanges, SITUATION_EXCHANGE));
        logger.debug("Built SX polling stats");
        JSONObject smPolling = new JSONObject();
        smPolling.put("typeName", "" + STOP_MONITORING);
        smPolling.put("polling", getIdAndCount(smChanges, STOP_MONITORING));
        logger.debug("Built SM polling stats");
        JSONObject gmPolling = new JSONObject();
        gmPolling.put("typeName", "" + GENERAL_MESSAGE);
        gmPolling.put("polling", getIdAndCount(gmChanges, GENERAL_MESSAGE));
        logger.debug("Built GM polling stats");

        pollingClients.add(etPolling);
        pollingClients.add(vmPolling);
        pollingClients.add(sxPolling);
        pollingClients.add(smPolling);
        pollingClients.add(gmPolling);

        result.put("polling", pollingClients);

        result.put("environment", environment);
        result.put("serverStarted", formatTimestamp(siriObjectFactory.serverStartTime));
        result.put("secondsSinceDataReceived", healthManager.getSecondsSinceDataReceived());
        JSONObject count = new JSONObject();

        logger.debug("Getting dataset sizes");
        Map<String, Integer> etDatasetSize = et.getDatasetSize();
        logger.debug("Got ET size");
        Map<String, Integer> vmDatasetSize = vm.getDatasetSize();
        logger.debug("Got VM size");
        Map<String, Integer> sxDatasetSize = sx.getDatasetSize();
        logger.debug("Got SX size");
        Map<String, Integer> smMonitoredDatasetSize = sm.getMonitoredDatasetSize();
        Map<String, Integer> smNotMonitoredDatasetSize = sm.getNotMonitoredDatasetSize();
        logger.debug("Got SM size");
        Map<String, Integer> gmDatasetSize = gm.getDatasetSize();
        Map<String, Integer> fmDatasetSize = fm.getDatasetSize();
        logger.debug("Got FM size");

        count.put("sx", sxDatasetSize.values().stream().mapToInt(Number::intValue).sum());
        count.put("et", etDatasetSize.values().stream().mapToInt(Number::intValue).sum());
        count.put("vm", vmDatasetSize.values().stream().mapToInt(Number::intValue).sum());
        count.put("gm", gmDatasetSize.values().stream().mapToInt(Number::intValue).sum());
        count.put("fm", fmDatasetSize.values().stream().mapToInt(Number::intValue).sum());
        count.put("sm-monitored", smMonitoredDatasetSize.values().stream().mapToInt(Number::intValue).sum());
        count.put("sm-notMonitored", smNotMonitoredDatasetSize.values().stream().mapToInt(Number::intValue).sum());

        Set<String> allDatasetIds = datasetService.getAllDatasetIds();
        for (String datasetId : allDatasetIds) {
            etDatasetSize.putIfAbsent(datasetId, 0);
            vmDatasetSize.putIfAbsent(datasetId, 0);
            sxDatasetSize.putIfAbsent(datasetId, 0);
            gmDatasetSize.putIfAbsent(datasetId, 0);
            fmDatasetSize.putIfAbsent(datasetId, 0);
            smMonitoredDatasetSize.putIfAbsent(datasetId, 0);
            smNotMonitoredDatasetSize.putIfAbsent(datasetId, 0);
        }

        logger.debug("Building distribution stats");
        List<DatasetCountDTO> distribution = getCountPerDataset(etDatasetSize, vmDatasetSize, sxDatasetSize, smMonitoredDatasetSize, smNotMonitoredDatasetSize, gmDatasetSize, fmDatasetSize);
        JSONArray distributionArray = new JSONArray();
        distributionArray.addAll(new ObjectMapper().convertValue(distribution, List.class));
        count.put("distribution", distributionArray);
        logger.debug("Built distribution stats");

        result.put("elements", count);

        logger.debug("Done building stats");
        return result;
    }

    private JSONArray getIdAndCount(Map<String, Set<SiriObjectStorageKey>> map, SiriDataType dataType) {
        JSONArray count = new JSONArray();
        for (String key : map.keySet()) {
            JSONObject keyValue = new JSONObject();
            keyValue.put("id", key);
            keyValue.put("count", map.getOrDefault(key, new HashSet<>()).size());

            RequestorRefStats stats = requestorRefRepository.getStats(key, dataType);
            String clientTrackingName = "";
            String datasetId = "";
            String firstRequestTimestamp = "";
            int requestCount = 0;
            double requestsPerSecond = 0.0;
            List<String> lastRequests = new ArrayList<>();
            if (stats != null) {
                if (stats.clientName != null) {
                    clientTrackingName = stats.clientName;
                }
                if (stats.datasetId != null) {
                    datasetId = stats.datasetId;
                }
                if (stats.lastRequests != null) {
                    lastRequests = stats.lastRequests;
                }

                firstRequestTimestamp = formatter.format(stats.firstRequestTimestamp);
                requestCount = stats.requestCount;

                long trackingDurationSeconds = ZonedDateTime.now().toEpochSecond() - stats.firstRequestTimestamp.toEpochSecond();
                if (trackingDurationSeconds >= 1) {
                    requestsPerSecond = (double) requestCount / trackingDurationSeconds;
                }
            }

            if (lastRequests.isEmpty()) {
                lastRequests.add("");
            }

            keyValue.put("clientTrackingName", clientTrackingName);
            keyValue.put("datasetId", datasetId);
            keyValue.put("lastRequests", lastRequests);
            keyValue.put("firstRequest", firstRequestTimestamp);
            keyValue.put("requestCount", requestCount);

            double requestsPerMinute = requestsPerSecond * 60;
            keyValue.put("requestsPerMinute", ((double) Math.round(requestsPerMinute * 10)) / 10); // rounding frequency to one decimal

            count.add(keyValue);
        }
        return count;
    }

    private List<DatasetCountDTO> getCountPerDataset(Map<String, Integer> etDatasetSize, Map<String, Integer> vmDatasetSize, Map<String, Integer> sxDatasetSize, Map<String, Integer> smMonitoredDatasetSize, Map<String, Integer> smNotMonitoredDatasetSize, Map<String, Integer> gmDatasetSize, Map<String, Integer> fmDatasetSize) {
        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(etDatasetSize.keySet());
        allKeys.addAll(vmDatasetSize.keySet());
        allKeys.addAll(sxDatasetSize.keySet());
        allKeys.addAll(smMonitoredDatasetSize.keySet());
        allKeys.addAll(smNotMonitoredDatasetSize.keySet());
        allKeys.addAll(gmDatasetSize.keySet());
        allKeys.addAll(fmDatasetSize.keySet());

        List<DatasetCountDTO> datasetCounts = new ArrayList<>();
        for (String datasetId : allKeys) {
            datasetCounts.add(new DatasetCountDTO(
                    datasetId,
                    etDatasetSize.getOrDefault(datasetId, 0),
                    vmDatasetSize.getOrDefault(datasetId, 0),
                    sxDatasetSize.getOrDefault(datasetId, 0),
                    gmDatasetSize.getOrDefault(datasetId, 0),
                    fmDatasetSize.getOrDefault(datasetId, 0),
                    smMonitoredDatasetSize.getOrDefault(datasetId, 0),
                    smNotMonitoredDatasetSize.getOrDefault(datasetId, 0)
            ));
        }
        return datasetCounts;
    }

    private JSONObject getJsonObject(SubscriptionSetup setup) {
        if (setup == null) {
            return null;
        }
        JSONObject obj = setup.toJSON();
        obj.put("activated", formatTimestamp(activatedTimestamp.get(setup.getSubscriptionId())));
        obj.put("lastActivity", formatTimestamp(lastActivity.get(setup.getSubscriptionId())));
        obj.put("lastDataReceived", formatTimestamp(dataReceived.get(setup.getSubscriptionId())));
        if (!SubscriptionStatus.RUNNING.equals(setup.getStatus())) {
            obj.put("status", "not running");
            obj.put("healthy", null);
            obj.put("flagAsNotReceivingData", false);
        } else {
            obj.put("status", "running");
            obj.put("flagAsNotReceivingData", (dataReceived.get(setup.getSubscriptionId()) != null && (dataReceived.get(setup.getSubscriptionId())).isBefore(Instant.now().minusSeconds(1800))));
        }
        obj.put("hitcount", hitcount.get(setup.getSubscriptionId()));
        obj.put("objectcount", objectCounter.get(setup.getSubscriptionId()));

        Long byteCount = receivedBytes.get(setup.getSubscriptionId());
        obj.put("bytecount", byteCount);
        obj.put("bytecountLabel", byteCount != null ? FileUtils.byteCountToDisplaySize(byteCount) : null);

        JSONObject urllist = new JSONObject();
        for (RequestType s : setup.getUrlMap().keySet()) {
            urllist.put(s.name(), setup.getUrlMap().get(s));
        }
        obj.put("urllist", urllist);
        obj.put("validationUrl", configuration.getInboundUrl() + "validation/" + setup.getDatasetId());

        return obj;
    }

    private String formatTimestamp(Instant instant) {
        if (instant != null) {
            return formatter.format(instant);
        }
        return "";
    }

    public SubscriptionSetup getSubscriptionByInternalId(long internalId) {
        for (SubscriptionSetup setup : subscriptions.values()) {
            if (setup.getInternalId() == internalId) {
                return setup;
            }
        }
        return null;
    }

    public SubscriptionSetup getSubscriptionBySubscriptionId(String subscriptionId) {
        for (SubscriptionSetup setup : subscriptions.values()) {
            if (setup.getSubscriptionId().equals(subscriptionId)) {
                return setup;
            }
        }
        return null;
    }


    public void dataReceived(String subscriptionId) {
        dataReceived(subscriptionId, 0);
    }

    public void dataReceived(String subscriptionId, boolean shouldLogSuccess) {
        dataReceived(subscriptionId, 0, null, shouldLogSuccess);
    }

    public void dataReceived(String subscriptionId, int receivedByteCount, String monitoredRef) {
        dataReceived(subscriptionId, receivedByteCount, monitoredRef, true);
    }

    public void dataReceived(String subscriptionId, int receivedByteCount, String monitoredRef, boolean shouldLogSuccess) {

        touchSubscription(subscriptionId, monitoredRef, shouldLogSuccess);

        Optional<DiscoverySubscription> discoverySubsOpt = getDiscoverySubscription(subscriptionId);

        if (discoverySubsOpt.isPresent()) {
            dataReceivedFromDiscovery(discoverySubsOpt.get(), monitoredRef);
            return;
        }


        dataReceived.put(subscriptionId, Instant.now());

        if (receivedByteCount > 0) {
            receivedBytes.set(subscriptionId,
                    receivedBytes.getOrDefault(subscriptionId, 0L) + receivedByteCount);
        }
    }

    private void dataReceivedFromDiscovery(DiscoverySubscription discoverySubscription, String monitoredRef) {
        if (StringUtils.isEmpty(monitoredRef)) {
            return;
        }

        Optional<SubscriptionSetup> childSubscriptionOpt = Optional.empty();

        if (STOP_MONITORING.equals(discoverySubscription.getDiscoveryType())) {
            childSubscriptionOpt = getChildSubscriptionIdFromMonitoringRefs(discoverySubscription, List.of(monitoredRef));

        } else if (VEHICLE_MONITORING.equals(discoverySubscription.getDiscoveryType())) {
            childSubscriptionOpt = getChildSubscriptionIdFromLineRefs(discoverySubscription, List.of(monitoredRef));
        }

        if (childSubscriptionOpt.isPresent()) {
            SubscriptionSetup childSubcription = childSubscriptionOpt.get();
            dataReceived(childSubcription.getSubscriptionId(), 0, monitoredRef);
        }
    }


    public void dataReceived(String subscriptionId, int receivedByteCount) {
        dataReceived(subscriptionId, receivedByteCount, null);
    }

    /**
     * Silently updates subscription
     *
     * @param subscriptionSetup
     */
    public void updateSubscription(SubscriptionSetup subscriptionSetup) {
        subscriptions.put(subscriptionSetup.getSubscriptionId(), subscriptionSetup);
    }

    public Set<String> getAllDatasetIds() {
        return subscriptions.values()
                .stream()
                .map(SubscriptionSetup::getDatasetId)
                .filter(StringUtils::isNotEmpty)
                .collect(Collectors.toSet());
    }

    /**
     * TEST USAGE ONLY
     * Remove all subscriptions
     */
    public void clearAllSubscriptions() {
        subscriptions.clear();
        siriAPISubscriptions.clear();
    }

    public Optional<DiscoverySubscription> getDiscoverySubscription(String subscriptionId) {
        return subscriptionConfig.getDiscoverySubscriptions().stream()
                .filter(discoverySubscription -> discoverySubscription.getSubscriptionIdBase().equals(subscriptionId))
                .findFirst();
    }

    public List<ValueAdapter> getValueAdaptersFromId(SubscriptionSetup subscriptionSetup, String mappingAdapterId) {
        List<ValueAdapter> valueAdapters = new ArrayList<>();

        if (mappingAdaptersById.containsKey(mappingAdapterId)) {
            Class adapterClass = mappingAdaptersById.get(mappingAdapterId);
            try {
                valueAdapters.addAll((List<ValueAdapter>) adapterClass.getMethod("getValueAdapters", SubscriptionSetup.class).invoke(adapterClass.newInstance(), subscriptionSetup));

            } catch (Exception e) {
                throw new ServiceConfigurationError("Invalid mappingAdapterId for subscription " + subscriptionSetup, e);
            }
        }
        return valueAdapters;
    }

    public Optional<SubscriptionSetup> getChildSubscriptionId(DiscoverySubscription parentDiscoverySubscription, Siri incomingSiri) {
        if (SiriDataType.STOP_MONITORING.equals(parentDiscoverySubscription.getDiscoveryType())) {
            List<String> monitoringRefs = SiriUtils.extractMonitoringRefs(incomingSiri);
            return getChildSubscriptionIdFromMonitoringRefs(parentDiscoverySubscription, monitoringRefs);
        } else if (SiriDataType.VEHICLE_MONITORING.equals(parentDiscoverySubscription.getDiscoveryType())) {
            List<String> lineRefs = SiriUtils.extractLineRefs(incomingSiri);
            return getChildSubscriptionIdFromLineRefs(parentDiscoverySubscription, lineRefs);
        }

        logger.warn("This discovery type is not handled : " + parentDiscoverySubscription.getDiscoveryType());
        return Optional.empty();
    }

    public Optional<SubscriptionSetup> getChildSubscriptionIdFromMonitoringRefs(DiscoverySubscription parentDiscoverySubscription, List<String> monitoringRefs) {
        return getAllSubscriptions(SiriDataType.STOP_MONITORING).stream()
                .filter(subscription -> subscription.getParentSubscriptionId() != null && subscription.getParentSubscriptionId().equals(parentDiscoverySubscription.getSubscriptionIdBase())
                        && containsMonitoringRefs(subscription, monitoringRefs))
                .findFirst();

    }

    public List<SubscriptionSetup> getChildSubscriptions(DiscoverySubscription parentDiscoverySubscription) {
        return getAllSubscriptions(parentDiscoverySubscription.getDiscoveryType()).stream()
                .filter(subscription -> subscription.getParentSubscriptionId() != null && subscription.getParentSubscriptionId().equals(parentDiscoverySubscription.getSubscriptionIdBase()))
                .toList();
    }

    public Optional<SubscriptionSetup> getChildSubscriptionIdFromLineRefs(DiscoverySubscription parentDiscoverySubscription, List<String> lineRefs) {
        return getAllSubscriptions(SiriDataType.VEHICLE_MONITORING).stream()
                .filter(subscription -> subscription.getParentSubscriptionId() != null && subscription.getParentSubscriptionId().equals(parentDiscoverySubscription.getSubscriptionIdBase())
                        && containsLineRefs(subscription, lineRefs))
                .findFirst();

    }


    private boolean containsMonitoringRefs(SubscriptionSetup subscriptionSetup, List<String> monitoringRefs) {
        return monitoringRefs.stream().anyMatch(subscriptionSetup.getStopMonitoringRefValues()::contains);
    }

    private boolean containsLineRefs(SubscriptionSetup subscriptionSetup, List<String> monitoringRefs) {
        return monitoringRefs.stream().anyMatch(subscriptionSetup.getLineRefValues()::contains);
    }

    public IMap<String, Instant> getActivatedTimestamp() {
        return activatedTimestamp;
    }

    public ReplicatedMap<String, SubscriptionSetup> getSubscriptions() {
        return subscriptions;
    }


    /**
     * Main function that check subscriptions and launch restart if needed
     */
    @Scheduled(fixedRateString = "${scheduler.subscription-check.rate:PT20M}")
    public void launchSubscriptionsLifeCycleCheck() {
        logger.info("Starting subscriptions lifecycle check");
        long startTime = System.currentTimeMillis();

        List<SubscriptionSetup> subscriptionsToRestart = getSubscriptionsToStop();
        logger.info("Subscriptions to restart : {}", subscriptionsToRestart.size());
        launchTerminateRequest(subscriptionsToRestart);
        launchSubscriptionRequest(subscriptionsToRestart);

        List<SubscriptionSetup> newSubscriptions = subscriptions.values()
                .stream().filter(subscription -> subscription.isActive() && SubscriptionSetup.SubscriptionMode.SUBSCRIBE.equals(subscription.getSubscriptionMode()) && SubscriptionStatus.WAITING_FOR_START.equals(subscription.getStatus()))
                .toList();

        logger.info("New subscriptions to start : {}", newSubscriptions.size());
        launchSubscriptionRequest(newSubscriptions);

        logger.info("Subscriptions lifecycle check completed in {} ms", System.currentTimeMillis() - startTime);
    }

    /**
     * Send a terminateSubscription request for subscriptions that need to be stopped
     *
     * @param subscriptionsToStop subscriptions that need to be stopped
     */
    public void launchTerminateRequest(List<SubscriptionSetup> subscriptionsToStop) {
        if (CollectionUtils.isEmpty(subscriptionsToStop)) {
            return;
        }
        subscriptionsToStop.forEach(this::sendTerminateRequest);
    }

    /**
     * Find subscriptions that need to be stopped :
     * - unresponsive subscriptions with no activity (lastActivity > limit)
     * - out of date subscriptions (restart time passed)
     *
     * @return List of subscriptions that need to be stopped
     */
    private List<SubscriptionSetup> getSubscriptionsToStop() {
        List<SubscriptionSetup> subscriptionsToStop = new ArrayList<>();
        subscriptionsToStop.addAll(getUnresponsiveSubscriptions());
        subscriptionsToStop.addAll(getOutOfDateSubscriptions());
        return subscriptionsToStop;
    }


    /**
     * Find out of date subscriptions
     * - was started before restart time
     * - now > restart time
     *
     * @return the list of out to date subscriptions
     */
    private List<SubscriptionSetup> getOutOfDateSubscriptions() {
        List<SubscriptionSetup> outOfDateSubs = subscriptions.values().stream()
                .filter(SubscriptionPredicates.IS_RUNNING)
                .filter(SubscriptionPredicates.IS_OUT_OF_DATE)
                .toList();

        List<String> outOfDateSubsIds = outOfDateSubs.stream().map(SubscriptionSetup::getSubscriptionId).toList();
        if (CollectionUtils.isNotEmpty(outOfDateSubsIds)) {
            logger.info("Out of date subs:{}", String.join(",", outOfDateSubsIds));
        }
        return outOfDateSubs;
    }


    /**
     * Find unresponsive subscriptions (subscriptions that did not receive data since a long time)
     *
     * @return list of unresponsive subscriptions
     */
    public List<SubscriptionSetup> getUnresponsiveSubscriptions() {
        List<SubscriptionSetup> unresponsiveSubs = subscriptions.values().stream()
                .filter(SubscriptionPredicates.isApplicableToUnresponsiveTest(disableUnresponsiveCheckSx))
                .filter(SubscriptionPredicates.IS_RUNNING)
                .filter(SubscriptionPredicates.isUnresponsive(lastActivity, unresponsiveDelay))
                .toList();

        List<String> unresponsiveSubsIds = unresponsiveSubs.stream().map(SubscriptionSetup::getSubscriptionId).toList();
        if (CollectionUtils.isNotEmpty(unresponsiveSubs)) {
            logger.info("Unresponsive subs:{}", String.join(",", unresponsiveSubsIds));
        }
        return unresponsiveSubs;
    }


    /**
     * Send a SubscriptionRequest for subscriptions that need to be started
     *
     * @param subscriptionsToStart subscriptions that need to be started
     */
    public void launchSubscriptionRequest(List<SubscriptionSetup> subscriptionsToStart) {
        if (CollectionUtils.isEmpty(subscriptionsToStart)) {
            return;
        }
        subscriptionsToStart.forEach(this::sendSubscriptionRequest);
    }


    /**
     * Save the last request into the subscription (monitoring purpose)
     * Request can be SubscriptionRequest or TerminateSubscriptionRequest
     *
     * @param exchange exchange that contains request and parameters
     */
    public void recordRequest(Exchange exchange) {
        String subscriptionId = exchange.getIn().getHeader(RECORDED_SUBSCRIPTION_HEADER_NAME, String.class);
        SubscriptionSetup foundSubscription = subscriptions.get(subscriptionId);
        if (foundSubscription != null) {
            foundSubscription.setLastRequest(exchange.getIn().getBody(String.class));

            String action = exchange.getIn().getHeader(RECORDED_SUBSCRIPTION_ACTION, String.class);
            if (RECORDED_SUBSCRIPTION_ACTION_SUBSCRIBE.equals(action)) {
                foundSubscription.setStatus(SubscriptionStatus.RUNNING);
                foundSubscription.setStartedAt(ZonedDateTime.now());
            } else if (RECORDED_SUBSCRIPTION_ACTION_TERMINATE.equals(action)) {
                foundSubscription.setStatus(SubscriptionStatus.STOPPED);
            }
            subscriptions.put(subscriptionId, foundSubscription);
        }
    }

    /**
     * Save the last response into the subscription (monitoring purpose)
     * Response can be from SubscriptionRequest or TerminateSubscriptionRequest
     *
     * @param exchange exchange that contains response and parameters
     */
    public void recordResponse(Exchange exchange) {
        String subscriptionId = exchange.getIn().getHeader(RECORDED_SUBSCRIPTION_HEADER_NAME, String.class);
        SubscriptionSetup foundSubscription = subscriptions.get(subscriptionId);
        if (foundSubscription != null) {
            foundSubscription.setLastResponse(exchange.getIn().getBody(String.class));
            subscriptions.put(subscriptionId, foundSubscription);
        }
    }

    /**
     * Launch a subscriptionRequest for subscription given as parameter
     *
     * @param subscriptionSetup Subscription for which a subscriptionRequest must be sent
     */
    public void sendSubscriptionRequest(SubscriptionSetup subscriptionSetup) {
        Exchange exchange = ExchangeBuilder.anExchange(camelContext).withBody(subscriptionSetup).build();
        try (ProducerTemplate producer = camelContext.createProducerTemplate()) {
            producer.requestBody("direct:" + subscriptionSetup.getStartSubscriptionRouteName(), exchange);
        } catch (Exception e) {
            throw new RuntimeException("Error while sending subscriptionRequest : ", e);
        }
    }

    /**
     * Launch a TerminateSubscription for subscription given as parameter
     *
     * @param subscriptionSetup Subscription for which a TerminateSubscription must be sent
     */
    public void sendTerminateRequest(SubscriptionSetup subscriptionSetup) {
        Exchange exchange = ExchangeBuilder.anExchange(camelContext).withBody(subscriptionSetup).build();
        try (ProducerTemplate producer = camelContext.createProducerTemplate()) {
            producer.send("direct:" + subscriptionSetup.getCancelSubscriptionRouteName(), exchange);
        } catch (Exception e) {
            throw new RuntimeException("Error while sending TerminateSubscriptionRequest  : ", e);
        }
    }

    /**
     * Read the subscription to recover the last request and last response (monitoring purpose)
     *
     * @param subscriptionId subscription for which data must be read
     * @return a json object with last request and last response
     */
    public JSONObject getSubscriptionLastRequestResponse(String subscriptionId) {

        JSONObject lastRequestResponse = new JSONObject();
        SubscriptionSetup foundSubscription = subscriptions.get(subscriptionId);
        if (foundSubscription != null) {
            lastRequestResponse.put("lastRequest", foundSubscription.getLastRequest());
            lastRequestResponse.put("lastResponse", foundSubscription.getLastResponse());
        }

        return lastRequestResponse;
    }

    /**
     * Get the statuses of all subscriptions (monitoring purpose)
     *
     * @return all status data of the subscriptions
     */
    public List<SubscriptionInfo> getSubscriptionStatuses() {
        return subscriptions.values().stream()
                .map(subscription -> new SubscriptionInfo(
                        subscription,
                        lastActivity.get(subscription.getSubscriptionId()),
                        receivedBytes.get(subscription.getSubscriptionId())
                ))
                .toList();
    }

    public boolean isSubscriptionReceivingData(String subscriptionId, long allowedInactivitySeconds) {
        boolean isReceiving = true;
        Instant lastDataReceived = dataReceived.get(subscriptionId);
        if (lastDataReceived != null) {
            isReceiving = (Instant.now().minusSeconds(allowedInactivitySeconds).isBefore(lastDataReceived));
        }
        return isReceiving;
    }

    public void setLastActivity(String subscriptionId, Instant instant) {
        lastActivity.put(subscriptionId, instant);
    }

    /**
     * Subscriptions previously stopped by user needs to be re-activated
     *
     * @param activeSubscriptions subscriptions that need to be reactivated
     */
    public void resetPreviouslyStoppedSubscriptions(List<SubscriptionSetup> activeSubscriptions) {
        activeSubscriptions.stream().filter(SubscriptionSetup::isActive)
                .forEach(subscription -> {
                    SubscriptionSetup existingSubscription = subscriptions.get(subscription.getSubscriptionId());
                    if (existingSubscription != null && SubscriptionStatus.STOPPED.equals(existingSubscription.getStatus())) {
                        existingSubscription.setStatus(SubscriptionStatus.WAITING_FOR_START);
                        existingSubscription.setActive(true);
                        subscriptions.put(existingSubscription.getSubscriptionId(), existingSubscription);
                    }
                });
    }

    public void updateChildSubscriptionsFromParent(List<DiscoverySubscription> discoverySubscriptions) {
        for (DiscoverySubscription discoverySubscription : discoverySubscriptions) {
            List<SubscriptionSetup> childs = getChildSubscriptions(discoverySubscription);
            for (SubscriptionSetup child : childs) {
                child.setRestartTime(discoverySubscription.getRestartTime());
                subscriptions.put(child.getSubscriptionId(), child);
            }
        }
    }
}
