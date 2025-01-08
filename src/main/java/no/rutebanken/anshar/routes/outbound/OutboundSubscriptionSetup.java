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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.entur.siri.validator.SiriValidator;

import java.io.Serializable;
import java.text.MessageFormat;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class OutboundSubscriptionSetup implements Serializable {

    private SiriValidator.Version siriVersion = SiriValidator.Version.VERSION_2_0;
    private ZonedDateTime requestTimestamp;
    private final SiriDataType subscriptionType;
    private final String address;
    private long heartbeatInterval;
    private int timeToLive;
    private Map<Class, Set<String>> filterMap;
    private final List<ValueAdapter> valueAdapters;
    private final String subscriptionId;
    private String requestorRef;
    private ZonedDateTime initialTerminationTime;
    private String datasetId;
    private String clientTrackingName;
    private long changeBeforeUpdates;
    private boolean incrementalUpdates;
    private long updateInterval;
    private boolean useOriginalId;
    private Map<String, List<ValueAdapter>> valueAdaptersByDataset = new HashMap<>();
    private Map<String, Map<Class, Set<String>>> filterMapByDataset = new HashMap<>();
    private Cache<String, String> alreadySentNotifications;


    private boolean isSOAPSubscription;

    public OutboundSubscriptionSetup(ZonedDateTime requestTimestamp, SiriDataType subscriptionType, String address, long heartbeatInterval,
                                     boolean incrementalUpdates, long changeBeforeUpdates, long updateInterval,
                                     Map<Class, Set<String>> filterMap, List<ValueAdapter> valueAdapters,
                                     String subscriptionId, String requestorRef, ZonedDateTime initialTerminationTime, String datasetId, String clientTrackingName, boolean useOriginalId, SiriValidator.Version siriVersion) {
        this(requestTimestamp, subscriptionType, address, heartbeatInterval, incrementalUpdates, changeBeforeUpdates, updateInterval, filterMap, valueAdapters,
                subscriptionId, requestorRef, initialTerminationTime, datasetId, clientTrackingName, useOriginalId, siriVersion, null, null, 5);
    }

    public OutboundSubscriptionSetup(ZonedDateTime requestTimestamp, SiriDataType subscriptionType, String address, long heartbeatInterval,
                                     boolean incrementalUpdates, long changeBeforeUpdates, long updateInterval,
                                     Map<Class, Set<String>> filterMap, List<ValueAdapter> valueAdapters,
                                     String subscriptionId, String requestorRef, ZonedDateTime initialTerminationTime, String datasetId, String clientTrackingName,
                                     boolean useOriginalId, SiriValidator.Version siriVersion, Map<String, List<ValueAdapter>> valueAdaptersByDataset, Map<String, Map<Class, Set<String>>> filterMapByDataset, int cacheTTL) {
        this.requestTimestamp = requestTimestamp;
        this.subscriptionType = subscriptionType;
        this.address = address;
        this.heartbeatInterval = heartbeatInterval;
        this.incrementalUpdates = incrementalUpdates;
        this.changeBeforeUpdates = changeBeforeUpdates;
        this.updateInterval = updateInterval;
        this.filterMap = filterMap;
        this.valueAdapters = valueAdapters;
        this.subscriptionId = subscriptionId;
        this.requestorRef = requestorRef;
        this.initialTerminationTime = initialTerminationTime;
        this.datasetId = datasetId;
        this.clientTrackingName = clientTrackingName;
        this.useOriginalId = useOriginalId;
        this.siriVersion = siriVersion;

        if (valueAdaptersByDataset != null) {
            this.valueAdaptersByDataset = valueAdaptersByDataset;
        }

        if (filterMapByDataset != null) {
            this.filterMap.clear();
            this.filterMapByDataset = filterMapByDataset;
        }

        alreadySentNotifications = CacheBuilder.newBuilder()
                .expireAfterWrite(cacheTTL, TimeUnit.HOURS)  // by default, already sent notifications are deleted after 5 hours to avoid huge data in memory
                .build();
    }

    OutboundSubscriptionSetup(SiriDataType subscriptionType, String address, int timeToLive, List<ValueAdapter> outboundAdapters, String subscriptionId) {
        this.subscriptionType = subscriptionType;
        this.address = address;
        this.timeToLive = timeToLive;
        this.valueAdapters = outboundAdapters;
        this.subscriptionId = subscriptionId;
    }

    public String createRouteId() {
        return "outbound." + subscriptionType + "." + subscriptionId;
    }

    public ZonedDateTime getRequestTimestamp() {
        return requestTimestamp;
    }

    public SiriDataType getSubscriptionType() {
        return subscriptionType;
    }

    public String getAddress() {
        return address;
    }

    public long getHeartbeatInterval() {
        return heartbeatInterval;
    }

    int getTimeToLive() {
        return timeToLive;
    }

    public long getChangeBeforeUpdates() {
        return changeBeforeUpdates;
    }

    public boolean getIncrementalUpdates() {
        return incrementalUpdates;
    }

    public long getUpdateInterval() {
        return updateInterval;
    }

    public Map<Class, Set<String>> getFilterMap() {
        return filterMap;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public String getRequestorRef() {
        return requestorRef;
    }

    public ZonedDateTime getInitialTerminationTime() {
        return initialTerminationTime;
    }

    public String getDatasetId() {
        return datasetId;
    }

    public List<ValueAdapter> getValueAdapters() {
        return valueAdapters;
    }

    public String getClientTrackingName() {
        return clientTrackingName;
    }

    public boolean isSOAPSubscription() {
        return isSOAPSubscription;
    }

    public void setSOAPSubscription(boolean SOAPSubscription) {
        isSOAPSubscription = SOAPSubscription;
    }

    public boolean isUseOriginalId() {
        return useOriginalId;
    }


    public void setValueAdaptersByDataset(Map<String, List<ValueAdapter>> valueAdaptersByDataset) {
        this.valueAdaptersByDataset = valueAdaptersByDataset;
    }

    public List<ValueAdapter> getValueAdaptersForDatasetd(String datasetId) {
        return valueAdaptersByDataset.containsKey(datasetId) ? valueAdaptersByDataset.get(datasetId) : new ArrayList<>();
    }

    public void setFilterMapByDataset(Map<String, Map<Class, Set<String>>> filterMapByDataset) {
        this.filterMapByDataset = filterMapByDataset;
    }

    public Map<String, Map<Class, Set<String>>> getFilterMapByDataset() {
        return filterMapByDataset;
    }

    public Map<String, List<ValueAdapter>> getValueAdaptersByDataset() {
        return valueAdaptersByDataset;
    }

    public String toString() {
        return MessageFormat.format("[subscriptionId={0}, clientTrackingName={1}, requestorRef={2}, address={3}]", subscriptionId, clientTrackingName, requestorRef, address);
    }

    public SiriValidator.Version getSiriVersion() {
        return siriVersion;
    }

    public void setSiriVersion(SiriValidator.Version siriVersion) {
        this.siriVersion = siriVersion;
    }

    public boolean hasNotificationBeenAlreadySent(String notificationId) {
        return alreadySentNotifications.getIfPresent(notificationId) != null;
    }

    public void recordNotification(String notificationId) {
        alreadySentNotifications.put(notificationId, "1");
    }
}
