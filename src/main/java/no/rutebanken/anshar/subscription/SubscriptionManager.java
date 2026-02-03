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
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.Siri;

import java.math.BigInteger;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static no.rutebanken.anshar.subscription.SiriDataType.*;

@Service
public class SubscriptionManager {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionManager.class);
    private static final Integer MAX_RESTART_TRIES = 3;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private final Set<String> gtfsSubscriptions = new HashSet<>();
    private final Map<String, String> siriAPISubscriptions = new HashMap<>();
    private Map<String, Class> mappingAdaptersById = new HashMap<>();

    @Autowired
    @Qualifier("getSubscriptionsMap")
    public ReplicatedMap<String, SubscriptionSetup> subscriptions;
    @Autowired
    @Qualifier("getActivatedTimestampMap")
    IMap<String, java.time.Instant> activatedTimestamp;
    @Value("${anshar.healthcheck.interval.factor:12}")
    private int healthcheckIntervalFactor;
    @Autowired
    private AnsharConfiguration configuration;
    @Autowired
    @Qualifier("getLastActivityMap")
    private ReplicatedMap<String, Instant> lastActivity;
    @Autowired
    @Qualifier("getDataReceivedMap")
    private ReplicatedMap<String, java.time.Instant> dataReceived;
    @Autowired
    @Qualifier("getReceivedBytesMap")
    private IMap<String, Long> receivedBytes;
    @Value("${anshar.environment}")
    private String environment;
    @Autowired
    @Qualifier("getHitcountMap")
    private IMap<String, Integer> hitcount;
    @Autowired
    @Qualifier("getForceRestartMap")
    private IMap<String, String> forceRestartMap;
    @Autowired
    private IMap<String, BigInteger> objectCounter;
    @Autowired
    private SiriObjectFactory siriObjectFactory;
    @Autowired
    private HealthManager healthManager;
    @Autowired
    private Situations sx;
    @Autowired
    private EstimatedTimetables et;
    @Autowired
    private VehicleActivities vm;
    @Autowired
    private MonitoredStopVisits sm;
    @Autowired
    private FacilityMonitoring fm;
    @Autowired
    @Qualifier("getSituationChangesMap")
    private IMap<String, Set<SiriObjectStorageKey>> sxChanges;
    @Autowired
    @Qualifier("getEstimatedTimetableChangesMap")
    private IMap<String, Set<SiriObjectStorageKey>> etChanges;
    @Autowired
    @Qualifier("getVehicleChangesMap")
    private IMap<String, Set<SiriObjectStorageKey>> vmChanges;
    @Autowired
    @Qualifier("getMonitoredStopVisitChangesMap")
    private IMap<String, Set<SiriObjectStorageKey>> smChanges;
    @Autowired
    private RequestorRefRepository requestorRefRepository;
    @Autowired
    @Qualifier("getRetryCountMap")
    private IMap<String, Integer> retryCountMap;

    @Autowired
    private DatasetService datasetService;

    @Autowired
    private SubscriptionConfig subscriptionConfig;


    public void addMappingAdapters(Map<String, Class> mappingAdaptersById) {
        this.mappingAdaptersById.putAll(mappingAdaptersById);
    }

    public void addSubscription(String subscriptionId, SubscriptionSetup setup) {
        if (setup.isActive()) {
            subscriptions.put(subscriptionId, setup);
            logger.trace("Added subscription {}", setup);
            activatePendingSubscription(subscriptionId);
        }
        //  logStats();
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
            setup.setActive(false);
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
                forceRestart(subscriptionId);
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

    public boolean isActiveSubscription(String subscriptionId) {
        SubscriptionSetup subscriptionSetup = subscriptions.get(subscriptionId);
        if (subscriptionSetup != null) {
            return subscriptionSetup.isActive();
        }
        return false;
    }

    public boolean activatePendingSubscription(String subscriptionId) {
        SubscriptionSetup subscriptionSetup = subscriptions.get(subscriptionId);

        if (subscriptionSetup != null) {
            subscriptionSetup.setActive(true);
            boolean shouldLogSuccess = !subscriptionSetup.getVendor().contains("AURA-MULTITUD-CITYWAY-SIRI-") && (subscriptionSetup.getContentType() == null || !subscriptionSetup.getContentType().equals("GTFS-RT"));
            // Subscriptions are inserted as immutable - need to replace previous value
            subscriptionSetup.setStartedAt(ZonedDateTime.now());
            subscriptions.put(subscriptionId, subscriptionSetup);
            lastActivity.put(subscriptionId, Instant.now());
            activatedTimestamp.put(subscriptionId, Instant.now());
            retryCountMap.put(subscriptionId, 0);
            if (shouldLogSuccess) {
                logger.info("Pending subscription {} activated", subscriptions.get(subscriptionId));
            }

            if (!dataReceived.containsKey(subscriptionId)) {
                dataReceived(subscriptionId, shouldLogSuccess);
            }
            if (!receivedBytes.containsKey(subscriptionId)) {
                receivedBytes.set(subscriptionId, 0L);
            }
            return true;
        }

        logger.warn("Pending subscriptionId [{}] NOT found", subscriptionId);
        return false;
    }

    public boolean isNewSubscription(String subscriptionId) {
        return lastActivity.get(subscriptionId) == null;
    }

    public Instant getLastDataReceived(String subscriptionId) {
        return dataReceived.get(subscriptionId);
    }

    public void forceRestart(String subscriptionId) {
        forceRestartMap.put(subscriptionId, subscriptionId);
        forceRestartMap.flush();
    }

    public boolean isForceRestart(String subscriptionId) {
        if (subscriptionId == null) {
            logger.warn("Null subscriptionId provided to isForceRestart check");
            return false;
        }

        try {
            String removedValue = forceRestartMap.remove(subscriptionId);

            if (removedValue != null) {
                SubscriptionSetup subscription = subscriptions.get(subscriptionId);
                String subscriptionInfo = (subscription != null)
                        ? subscription.toString()
                        : "unknown subscription";

                logger.info("Forced restart triggered for subscription {} - {}",
                        subscriptionInfo, removedValue);
                return true;
            }
            return false;

        } catch (Exception e) {
            logger.error("Error checking forced restart for subscription {}: {}",
                    subscriptionId, e.getMessage());
            return false;
        }
    }

    public Boolean isSubscriptionHealthy(String subscriptionId) {
        return isSubscriptionHealthy(subscriptionId, healthcheckIntervalFactor);
    }

    private Boolean isSubscriptionHealthy(String subscriptionId, int healthCheckIntervalFactor) {
        Instant instant = lastActivity.get(subscriptionId);

        if (instant == null) {
            //Subscription has not had any activity, and may not have been started yet - flag as healthy
            return true;
        }

        logger.trace("SubscriptionId [{}], last activity {}.", subscriptionId, instant);

        SubscriptionSetup activeSubscription = subscriptions.get(subscriptionId);
        if (activeSubscription != null && activeSubscription.isActive()) {

            Duration heartbeatInterval = activeSubscription.getHeartbeatInterval();
            if (heartbeatInterval == null) {
                heartbeatInterval = Duration.ofMinutes(5);
            }

            long allowedInterval = heartbeatInterval.toMillis() * healthCheckIntervalFactor;

            if (instant.isBefore(Instant.now().minusMillis(allowedInterval))) {
                //Subscription exists, but there has not been any activity recently
                return false;
            }

            if (isRestartTimePassed(subscriptionId)) {
                forceRestart(subscriptionId);
                return false;
            }

        }

        return true;
    }

    /**
     * Checks if the subscription should be restarted, depending on restart time filled in subscription request
     * If current time is after restart time AND subscription has been started before restart time => restart
     *
     * @param subscriptionId
     * @return True : subscription should be restarted
     * false : subscription should not be restarted
     */
    public boolean isRestartTimePassed(String subscriptionId) {
        SubscriptionSetup activeSubscription = subscriptions.get(subscriptionId);

        //If active subscription has existed longer than "initial subscription duration" - restart
        Instant activated = activatedTimestamp.get(subscriptionId);


        if (activeSubscription != null && activated != null) {

            if (activeSubscription.getRestartTime() != null && activeSubscription.getRestartTime().contains(":")) {
                // Allowing subscriptions to be restarted at specified time
                ZonedDateTime restartTime = ZonedDateTime.of(LocalDate.now(), LocalTime.parse(activeSubscription.getRestartTime()), ZoneId.systemDefault());

                //Current time is after restart time AND subscription has been activated before restart time
                if (restartTime.isBefore(ZonedDateTime.now()) && activated.atZone(ZoneId.systemDefault()).isBefore(restartTime)) {
                    logger.info("Subscription [{}] configured for nightly restart at {}.", activeSubscription, restartTime);
                    increaseTryCount(subscriptionId);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if the subscription should try to restart or not, depending on MAX_RESTART_TRIES set in this class
     *
     * @param subscriptionId
     * @return true : current try nb is < MAX_RESTART_TRIES  : should try to restart
     * false : current try nb is > MAX_RESTART_TRIES : no more try should be done
     */
    public boolean shouldTryRestart(String subscriptionId) {
        return !retryCountMap.containsKey(subscriptionId) || retryCountMap.get(subscriptionId) < MAX_RESTART_TRIES;
    }


    /**
     * Increase the number of tries, for the subscription given as parameter
     *
     * @param subscriptionId
     * @return
     */
    public void increaseTryCount(String subscriptionId) {
        Integer currentNbOfTries = retryCountMap.containsKey(subscriptionId) ? retryCountMap.get(subscriptionId) : 0;
        retryCountMap.put(subscriptionId, currentNbOfTries + 1);

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
                .collect(Collectors.toList())
        );
        logger.debug("Built ET stats");

        JSONArray vmSubscriptions = new JSONArray();
        vmSubscriptions.addAll(this.subscriptions.values().stream()
                .filter(subscriptionSetup -> subscriptionSetup.getSubscriptionType() == VEHICLE_MONITORING)
                .map(this::getJsonObject)
                .filter(Objects::nonNull)
                .collect(Collectors.toList())
        );
        logger.debug("Built VM stats");

        JSONArray sxSubscriptions = new JSONArray();
        sxSubscriptions.addAll(this.subscriptions.values().stream()
                .filter(subscriptionSetup -> subscriptionSetup.getSubscriptionType() == SITUATION_EXCHANGE)
                .map(this::getJsonObject)
                .filter(Objects::nonNull)
                .collect(Collectors.toList())
        );
        logger.debug("Built SX stats");

        JSONArray smSubscriptions = new JSONArray();
        smSubscriptions.addAll(this.subscriptions.values().stream()
                .filter(subscriptionSetup -> subscriptionSetup.getSubscriptionType() == STOP_MONITORING)
                .map(this::getJsonObject)
                .filter(Objects::nonNull)
                .collect(Collectors.toList())
        );
        logger.debug("Built SM stats");

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

        stats.add(etType);
        stats.add(vmType);
        stats.add(sxType);
        stats.add(smType);

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

        pollingClients.add(etPolling);
        pollingClients.add(vmPolling);
        pollingClients.add(sxPolling);
        pollingClients.add(smPolling);

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
        Map<String, Integer> fmDatasetSize = fm.getDatasetSize();
        logger.debug("Got FM size");

        count.put("sx", sxDatasetSize.values().stream().mapToInt(Number::intValue).sum());
        count.put("et", etDatasetSize.values().stream().mapToInt(Number::intValue).sum());
        count.put("vm", vmDatasetSize.values().stream().mapToInt(Number::intValue).sum());
        count.put("fm", fmDatasetSize.values().stream().mapToInt(Number::intValue).sum());
        count.put("sm-monitored", smMonitoredDatasetSize.values().stream().mapToInt(Number::intValue).sum());
        count.put("sm-notMonitored", smNotMonitoredDatasetSize.values().stream().mapToInt(Number::intValue).sum());

        Set<String> allDatasetIds = datasetService.getAllDatasetIds();
        for (String datasetId : allDatasetIds) {
            etDatasetSize.putIfAbsent(datasetId, 0);
            vmDatasetSize.putIfAbsent(datasetId, 0);
            sxDatasetSize.putIfAbsent(datasetId, 0);
            fmDatasetSize.putIfAbsent(datasetId, 0);
            smMonitoredDatasetSize.putIfAbsent(datasetId, 0);
            smNotMonitoredDatasetSize.putIfAbsent(datasetId, 0);
        }

        logger.debug("Building distribution stats");
        count.put("distribution", getCountPerDataset(etDatasetSize, vmDatasetSize, sxDatasetSize, smMonitoredDatasetSize, smNotMonitoredDatasetSize, fmDatasetSize));
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

    private JSONArray getCountPerDataset(Map<String, Integer> etDatasetSize, Map<String, Integer> vmDatasetSize, Map<String, Integer> sxDatasetSize, Map<String, Integer> smMonitoredDatasetSize, Map<String, Integer> smNotMonitoredDatasetSize, Map<String, Integer> fmDatasetSize) {
        JSONArray etDatasetCount = new JSONArray();

        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(etDatasetSize.keySet());
        allKeys.addAll(vmDatasetSize.keySet());
        allKeys.addAll(sxDatasetSize.keySet());
        allKeys.addAll(smMonitoredDatasetSize.keySet());
        allKeys.addAll(smNotMonitoredDatasetSize.keySet());
        allKeys.addAll(fmDatasetSize.keySet());

        for (String datasetId : allKeys) {
            JSONObject counter = new JSONObject();
            counter.put("datasetId", datasetId);
            counter.put("etCount", etDatasetSize.getOrDefault(datasetId, 0));
            counter.put("vmCount", vmDatasetSize.getOrDefault(datasetId, 0));
            counter.put("sxCount", sxDatasetSize.getOrDefault(datasetId, 0));
            counter.put("fmCount", fmDatasetSize.getOrDefault(datasetId, 0));
            counter.put("smMonitoredCount", smMonitoredDatasetSize.getOrDefault(datasetId, 0));
            counter.put("smNotMonitoredCount", smNotMonitoredDatasetSize.getOrDefault(datasetId, 0));
            etDatasetCount.add(counter);
        }
        return etDatasetCount;
    }

    private JSONObject getJsonObject(SubscriptionSetup setup) {
        if (setup == null) {
            return null;
        }
        JSONObject obj = setup.toJSON();
        obj.put("activated", formatTimestamp(activatedTimestamp.get(setup.getSubscriptionId())));
        obj.put("lastActivity", formatTimestamp(lastActivity.get(setup.getSubscriptionId())));
        obj.put("lastDataReceived", formatTimestamp(dataReceived.get(setup.getSubscriptionId())));
        if (!setup.isActive()) {
            obj.put("status", "deactivated");
            obj.put("healthy", null);
            obj.put("flagAsNotReceivingData", false);
        } else {
            obj.put("status", "active");
            obj.put("healthy", isSubscriptionHealthy(setup.getSubscriptionId()));
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

    /**
     * Terminating all subscriptions by SiriDataType - to be used before a full restart to
     */
    public void terminateAllSubscriptions(SiriDataType type) {
        logger.warn("Terminating ALL {}subscriptions", (type != null ? type + "-" : ""));
        int counter = 0;
        int inactiveCounter = 0;
        for (SubscriptionSetup subscription : subscriptions.values()) {
            if (type == null || subscription.getSubscriptionType().equals(type)) {
                if (isActiveSubscription(subscription.getSubscriptionId())) {
                    stopSubscription(subscription.getSubscriptionId());
                    counter++;
                } else {
                    inactiveCounter++;
                }
            }
        }
        logger.warn("Stopped {} subscriptions, {} inactive.", counter, inactiveCounter);
    }

    public void terminateSubscription(String subscriptionId) {
        logger.warn("Terminating subscription by id : {}", subscriptionId);
        for (SubscriptionSetup subscription : subscriptions.values()) {
            if (subscription.getSubscriptionId().equals(subscriptionId)) {
                stopSubscription(subscription.getSubscriptionId());
            }
        }
    }


    /**
     * Terminating all subscriptions - to be used before a full restart to
     */
    public void triggerRestartAllActiveSubscriptions(SiriDataType type) {

        logger.warn("Triggering restart of ALL active {}subscriptions", (type != null ? type + "-" : ""));
        int counter = 0;
        int inactiveCounter = 0;
        for (SubscriptionSetup subscription : subscriptions.values()) {
            if (type == null || subscription.getSubscriptionType().equals(type)) {
                if (isActiveSubscription(subscription.getSubscriptionId())) {
                    forceRestart(subscription.getSubscriptionId());
                    counter++;
                } else {
                    inactiveCounter++;
                }
            }
        }
        logger.warn("Restarted {} subscriptions, {} inactive.", counter, inactiveCounter);
    }

    public void stopSubscription(String subscriptionId) {
        if (subscriptionId != null) {
            SubscriptionSetup subscriptionSetup = subscriptions.get(subscriptionId);
            if (subscriptionSetup != null) {
                subscriptionSetup.setActive(false);
                subscriptions.put(subscriptionId, subscriptionSetup);

                removeSubscription(subscriptionId);
                logger.info("Handled request to cancel subscription {}", subscriptionSetup);
            }
        }
    }

    public void startSubscription(String subscriptionId) {
        if (subscriptionId != null) {
            SubscriptionSetup subscriptionSetup = subscriptions.get(subscriptionId);
            if (subscriptionSetup != null) {
                subscriptionSetup.setActive(true);
                activatePendingSubscription(subscriptionId);
                logger.info("Handled request to start subscription {}", subscriptionSetup);
            }
        }
    }

    public Set<String> getAllUnhealthySubscriptions(int allowedInactivitySeconds) {
        Set<String> subscriptionIds = subscriptions.keySet()
                .stream()
                .filter(this::isActiveSubscription)
                .filter(subscriptionId -> !isSubscriptionReceivingData(subscriptionId, allowedInactivitySeconds))
                .collect(Collectors.toSet());
        if (subscriptionIds != null && !subscriptionIds.isEmpty()) {

            return subscriptions.values()
                    .stream()
                    .filter(subscriptionSetup -> subscriptionIds.contains(subscriptionSetup.getSubscriptionId()))
                    .map(SubscriptionSetup::getVendor)
                    .collect(Collectors.toSet());
        }
        return new HashSet<>();
    }

    public boolean isSubscriptionReceivingData(String subscriptionId, long allowedInactivitySeconds) {
        if (!isActiveSubscription(subscriptionId)) {
            return true;
        }
        boolean isReceiving = true;
        Instant lastDataReceived = dataReceived.get(subscriptionId);
        if (lastDataReceived != null) {
            isReceiving = (Instant.now().minusSeconds(allowedInactivitySeconds).isBefore(lastDataReceived));
        }
        return isReceiving;
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


        if (isActiveSubscription(subscriptionId)) {
            dataReceived.put(subscriptionId, Instant.now());

            if (receivedByteCount > 0) {
                receivedBytes.set(subscriptionId,
                        receivedBytes.getOrDefault(subscriptionId, 0L) + receivedByteCount);
            }
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
}
