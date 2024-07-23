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

package no.rutebanken.anshar.data.collections;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.hazelcast.cluster.Cluster;
import com.hazelcast.cluster.Member;
import com.hazelcast.collection.ISet;
import com.hazelcast.config.CacheDeserializedValues;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.SerializerConfig;
import com.hazelcast.core.DistributedObject;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.LifecycleEvent;
import com.hazelcast.map.IMap;
import com.hazelcast.replicatedmap.ReplicatedMap;
import no.rutebanken.anshar.data.RequestorRefStats;
import no.rutebanken.anshar.data.SiriObjectStorageKey;
import no.rutebanken.anshar.routes.outbound.OutboundSubscriptionSetup;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.rutebanken.hazelcasthelper.service.HazelCastService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.*;

import javax.annotation.PreDestroy;
import java.math.BigInteger;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;


@Service
@Configuration
public class ExtendedHazelcastService extends HazelCastService {

    private static final Logger logger = LoggerFactory.getLogger(ExtendedHazelcastService.class);


    public ExtendedHazelcastService(@Value("${entur.hazelcast.backup.count.sync:2}") int backupCountSync, @Value("${anshar.hazelcast.members:}") List<String> members) throws UnknownHostException {
        super(null);
        // setBackupCount(backupCountSync);
    }

    public void addBeforeShuttingDownHook(Runnable destroyFunction) {
        hazelcast.getLifecycleService().addLifecycleListener(lifecycleEvent -> {
            logger.info("Lifecycle: Event triggered: {}", lifecycleEvent);
            if (lifecycleEvent.getState().equals(LifecycleEvent.LifecycleState.SHUTTING_DOWN)) {
                logger.info("Lifecycle: Shutting down - committing all changes.");
                destroyFunction.run();
            } else {
                logger.info("Lifecycle: Ignoring event {}", lifecycleEvent);
            }
        });
        logger.info("Lifecycle: Shutdownhook added.");
    }

    @PreDestroy
    private void customShutdown() {
        logger.info("Attempting to shutdown through LifecycleService");
        hazelcast.getLifecycleService().shutdown();
        logger.info("Shutdown through LifecycleService");
    }

    public HazelcastInstance getHazelcastInstance() {
        return hazelcast;
    }

    @Override
    public void updateDefaultMapConfig(MapConfig defaultMapConfig) {
        defaultMapConfig.setAsyncBackupCount(0);
        defaultMapConfig.setBackupCount(0);
        defaultMapConfig.setCacheDeserializedValues(CacheDeserializedValues.NEVER);

    }

    @Override
    public List<SerializerConfig> getSerializerConfigs() {

        return Arrays.asList(
                new SerializerConfig()
                        .setTypeClass(EstimatedVehicleJourney.class)
                        .setImplementation(new KryoSerializer()),
                new SerializerConfig()
                        .setTypeClass(PtSituationElement.class)
                        .setImplementation(new KryoSerializer()),
                new SerializerConfig()
                        .setTypeClass(VehicleActivityStructure.class)
                        .setImplementation(new KryoSerializer()),

                new SerializerConfig()
                        .setTypeClass(MonitoredStopVisit.class)
                        .setImplementation(new KryoSerializer()),
                new SerializerConfig()
                        .setTypeClass(JSONObject.class)
                        .setImplementation(new KryoSerializer())

        );
    }

    @Bean
    public IMap<SiriObjectStorageKey, PtSituationElement> getSituationsMap() {
        return hazelcast.getMap("anshar.sx");
    }

    @Bean
    public IMap<String, Set<SiriObjectStorageKey>> getSituationChangesMap() {
        return hazelcast.getMap("anshar.sx.changes");
    }

    @Bean
    public IMap<SiriObjectStorageKey, EstimatedVehicleJourney> getEstimatedTimetablesMap() {
        return hazelcast.getMap("anshar.et");
    }

    @Bean
    public IMap<String, Set<SiriObjectStorageKey>> getEstimatedTimetableChangesMap() {
        return hazelcast.getMap("anshar.et.changes");
    }

    @Bean
    public IMap<SiriObjectStorageKey, MonitoredStopVisit> getMonitoredStopVisits() {
        return hazelcast.getMap("anshar.sm");
    }

    @Bean
    public IMap<String, Set<String>> getDiscoveryStops() {
        return hazelcast.getMap("anshar.discovery.stops");
    }

    @Bean
    public IMap<String, Set<String>> getDiscoveryLines() {
        return hazelcast.getMap("anshar.discovery.lines");
    }

    @Bean
    public IMap<SiriObjectStorageKey, GeneralMessage> getGeneralMessages() {
        return hazelcast.getMap("anshar.gm");
    }

    @Bean
    public IMap<SiriObjectStorageKey, GeneralMessageCancellation> getGeneralMessageCancellations() {
        return hazelcast.getMap("anshar.gm.canc");
    }


    @Bean
    public IMap<SiriObjectStorageKey, FacilityConditionStructure> getFacilityMonitoring() {
        return hazelcast.getMap("anshar.fm");
    }

    @Bean
    public IMap<String, Set<SiriObjectStorageKey>> getMonitoredStopVisitChangesMap() {
        return hazelcast.getMap("anshar.sm.changes");
    }

    @Bean
    public IMap<String, Set<SiriObjectStorageKey>> getGeneralMessagesChangesMap() {
        return hazelcast.getMap("anshar.gm.changes");
    }

    @Bean
    public IMap<String, Set<SiriObjectStorageKey>> getGeneralMessageCancellationChangesMap() {
        return hazelcast.getMap("anshar.gm.canc.changes");
    }


    @Bean
    public IMap<String, Set<SiriObjectStorageKey>> getFacilityMonitoringChangesMap() {
        return hazelcast.getMap("anshar.fm.changes");
    }

    @Bean
    public IMap<SiriObjectStorageKey, String> getIdForPatternChangesMap() {
        return hazelcast.getMap("anshar.et.index.pattern");
    }

    @Bean
    public IMap<SiriObjectStorageKey, String> getSxChecksumMap() {
        return hazelcast.getMap("anshar.sx.checksum.cache");
    }

    @Bean
    public IMap<SiriObjectStorageKey, String> getEtChecksumMap() {
        return hazelcast.getMap("anshar.et.checksum.cache");
    }

    @Bean
    public IMap<SiriObjectStorageKey, String> getVmChecksumMap() {
        return hazelcast.getMap("anshar.vm.checksum.cache");
    }

    @Bean
    public ReplicatedMap<SiriObjectStorageKey, String> getSmChecksumMap() {
        return hazelcast.getReplicatedMap("anshar.sm.checksum.cache");
    }

    @Bean
    public IMap<SiriObjectStorageKey, ZonedDateTime> getIdStartTimeMap() {
        return hazelcast.getMap("anshar.et.index.startTime");
    }

    @Bean
    public IMap<SiriObjectStorageKey, VehicleActivityStructure> getVehiclesMap() {
        return hazelcast.getMap("anshar.vm");
    }

    @Bean
    public IMap<String, Set<SiriObjectStorageKey>> getVehicleChangesMap() {
        return hazelcast.getMap("anshar.vm.changes");
    }

    @Bean
    public ReplicatedMap<String, SubscriptionSetup> getSubscriptionsMap() {
        return hazelcast.getReplicatedMap("anshar.subscriptions.active");
    }

    @Bean
    public ReplicatedMap<String, String> getValidationFilterMap() {
        return hazelcast.getReplicatedMap("anshar.validation.filter");
    }

    @Bean
    public ReplicatedMap<String, Instant> getLastActivityMap() {
        return hazelcast.getReplicatedMap("anshar.activity.last");
    }

    @Bean
    public ReplicatedMap<String, Instant> getDataReceivedMap() {
        return hazelcast.getReplicatedMap("anshar.subscriptions.data.received");
    }


    @Bean
    public IMap<String, Long> getReceivedBytesMap() {
        return hazelcast.getMap("anshar.subscriptions.data.received.bytes");
    }


    @Bean
    public IMap<String, Instant> getLastEtUpdateRequest() {
        return hazelcast.getMap("anshar.activity.last.et.update.request");
    }

    @Bean
    public IMap<String, Instant> getLastSxUpdateRequest() {
        return hazelcast.getMap("anshar.activity.last.sx.update.request");
    }

    @Bean
    public IMap<String, Instant> getLastVmUpdateRequest() {
        return hazelcast.getMap("anshar.activity.last.vm.update.request");
    }

    @Bean
    public IMap<String, Instant> getLastSmUpdateRequest() {
        return hazelcast.getMap("anshar.activity.last.sm.update.request");
    }

    @Bean
    public IMap<String, Instant> getLastGmUpdateRequest() {
        return hazelcast.getMap("anshar.activity.last.gm.update.request");
    }

    @Bean
    public IMap<String, Instant> getLastFmUpdateRequest() {
        return hazelcast.getMap("anshar.activity.last.fm.update.request");
    }

    @Bean
    public IMap<String, Instant> getActivatedTimestampMap() {
        return hazelcast.getMap("anshar.activity.activated");
    }

    @Bean
    public IMap<String, Integer> getHitcountMap() {
        return hazelcast.getMap("anshar.activity.hitcount");
    }

    @Bean
    public IMap<String, String> getForceRestartMap() {
        return hazelcast.getMap("anshar.subscriptions.restart");
    }

    @Bean
    public IMap<String, Instant> getFailTrackerMap() {
        return hazelcast.getMap("anshar.activity.failtracker");
    }

    @Bean
    public IMap<String, Instant> getLockMap() {
        return hazelcast.getMap("anshar.locks");
    }

    @Bean
    public IMap<Enum<HealthCheckKey>, Instant> getHealthCheckMap() {
        return hazelcast.getMap("anshar.admin.health");
    }

    @Bean
    public IMap<String, OutboundSubscriptionSetup> getOutboundSubscriptionMap() {
        return hazelcast.getMap("anshar.subscriptions.outbound");
    }

    @Bean
    public IMap<String, Instant> getHeartbeatTimestampMap() {
        return hazelcast.getMap("anshar.subscriptions.outbound.heartbeat");
    }

    @Bean
    public ISet<String> getUnhealthySubscriptionsSet() {
        return hazelcast.getSet("anshar.subscriptions.unhealthy.notified");
    }

    @Bean
    public IMap<String, Map<SiriDataType, Set<String>>> getUnmappedIds() {
        return hazelcast.getMap("anshar.mapping.unmapped");
    }

    @Bean
    public IMap<String, List<String>> getValidationResultRefMap() {
        return hazelcast.getMap("anshar.validation.results.ref");
    }

    @Bean
    public IMap<String, byte[]> getValidationResultSiriMap() {
        return hazelcast.getMap("anshar.validation.results.siri");
    }

    @Bean
    public IMap<String, JSONObject> getValidationResultJsonMap() {
        return hazelcast.getMap("anshar.validation.results.json");
    }

    @Bean
    public IMap<String, Long> getValidationSizeTracker() {
        return hazelcast.getMap("anshar.validation.results.size");
    }

    @Bean
    public IMap<String, BigInteger> getObjectCounterMap() {
        return hazelcast.getMap("anshar.activity.objectcount");
    }

    @Bean
    public IMap<String[], RequestorRefStats> getRequestorRefs() {
        return hazelcast.getMap("anshar.activity.requestorref");
    }

    @Bean
    public IMap<String, Integer> getRetryCountMap() {
        return hazelcast.getMap("anshar.subscriptions.retry.count");
    }

    public String listNodes(boolean includeStats) {
        JsonMapper jsonMapper = new JsonMapper();
        JSONObject root = new JSONObject();
        JSONArray clusterMembers = new JSONArray();
        Cluster cluster = hazelcast.getCluster();

        Set<Member> members = cluster.getMembers();
        if (!members.isEmpty()) {
            for (Member member : members) {

                JSONObject obj = new JSONObject();
                obj.put("uuid", member.getUuid().toString());
                obj.put("host", member.getAddress().getHost());
                obj.put("port", member.getAddress().getPort());
                obj.put("local", member.localMember());

                if (includeStats) {
                    JSONObject stats = new JSONObject();
                    Collection<DistributedObject> distributedObjects = hazelcast.getDistributedObjects();
                    for (DistributedObject distributedObject : distributedObjects) {

                        try {
                            String jsonValue = jsonMapper.writeValueAsString(hazelcast.getMap(distributedObject.getName()).getLocalMapStats());
                            stats.put(distributedObject.getName(), new org.json.JSONObject(jsonValue));
                        } catch (JsonProcessingException e) {
                            e.printStackTrace();
                        }
                    }

                    obj.put("localmapstats", stats);
                }
                clusterMembers.add(obj);
            }
        }

        root.put("members", clusterMembers);
        return root.toString();

    }
}
