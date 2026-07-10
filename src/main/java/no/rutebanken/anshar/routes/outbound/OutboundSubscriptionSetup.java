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
import lombok.Getter;
import lombok.Setter;
import no.rutebanken.anshar.routes.outbound.model.CompressionFormat;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.apache.commons.lang3.StringUtils;
import org.entur.siri.validator.SiriValidator;

import javax.xml.datatype.Duration;
import java.io.Serializable;
import java.text.MessageFormat;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class OutboundSubscriptionSetup implements Serializable {

    @Setter
    @Getter
    private SiriValidator.Version siriVersion = SiriValidator.Version.VERSION_2_0;

    @Getter
    private final ZonedDateTime requestTimestamp;

    @Getter
    private final SiriDataType subscriptionType;

    @Getter
    private final String address;

    @Getter
    private final long heartbeatInterval;

    @Getter
    private final Map<Class, Set<String>> filterMap;

    @Getter
    private final List<ValueAdapter> valueAdapters;

    @Getter
    private final String subscriptionId;

    @Getter
    private final String requestorRef;

    @Getter
    private final ZonedDateTime initialTerminationTime;

    @Getter
    private final List<String> datasetList;

    @Getter
    private final String clientTrackingName;

    @Getter
    private final long changeBeforeUpdates;

    @Getter
    private final boolean incrementalUpdates;

    @Getter
    private final long updateInterval;

    @Setter
    @Getter
    private boolean useOriginalId;

    @Getter
    @Setter
    private Map<String, List<ValueAdapter>> valueAdaptersByDataset = new HashMap<>();

    @Getter
    @Setter
    private Map<String, Map<Class, Set<String>>> filterMapByDataset = new HashMap<>();

    private final Cache<String, String> alreadySentNotifications;

    @Setter
    @Getter
    private OutboundIdMappingPolicy outboundIdMappingPolicy;

    @Setter
    @Getter
    private Duration previewInterval;

    @Getter
    @Setter
    private CompressionFormat compressionFormat = CompressionFormat.NONE;

    @Setter
    @Getter
    private boolean isSOAPSubscription;

    @Getter
    @Setter
    private boolean isSicAQuaySubscription;

    public OutboundSubscriptionSetup(ZonedDateTime requestTimestamp, SiriDataType subscriptionType, String address, long heartbeatInterval,
                                     boolean incrementalUpdates, long changeBeforeUpdates, long updateInterval,
                                     Map<Class, Set<String>> filterMap, List<ValueAdapter> valueAdapters,
                                     String subscriptionId, String requestorRef, ZonedDateTime initialTerminationTime, String datasetId, String clientTrackingName, boolean useOriginalId, SiriValidator.Version siriVersion) {
        this(requestTimestamp, subscriptionType, address, heartbeatInterval, incrementalUpdates, changeBeforeUpdates, updateInterval, filterMap, valueAdapters,
                subscriptionId, requestorRef, initialTerminationTime, datasetId, clientTrackingName, useOriginalId, siriVersion, null, null, 5, CompressionFormat.NONE, false);
    }

    public OutboundSubscriptionSetup(ZonedDateTime requestTimestamp, SiriDataType subscriptionType, String address, long heartbeatInterval,
                                     boolean incrementalUpdates, long changeBeforeUpdates, long updateInterval,
                                     Map<Class, Set<String>> filterMap, List<ValueAdapter> valueAdapters,
                                     String subscriptionId, String requestorRef, ZonedDateTime initialTerminationTime, String datasetId, String clientTrackingName,
                                     boolean useOriginalId, SiriValidator.Version siriVersion, boolean isSicAQuay) {
        this(requestTimestamp, subscriptionType, address, heartbeatInterval, incrementalUpdates, changeBeforeUpdates, updateInterval, filterMap, valueAdapters,
                subscriptionId, requestorRef, initialTerminationTime, datasetId, clientTrackingName, useOriginalId, siriVersion, null, null, 5, CompressionFormat.NONE, isSicAQuay);
    }

    public OutboundSubscriptionSetup(ZonedDateTime requestTimestamp, SiriDataType subscriptionType, String address, long heartbeatInterval,
                                     boolean incrementalUpdates, long changeBeforeUpdates, long updateInterval,
                                     Map<Class, Set<String>> filterMap, List<ValueAdapter> valueAdapters,
                                     String subscriptionId, String requestorRef, ZonedDateTime initialTerminationTime, String datasetId, String clientTrackingName,
                                     boolean useOriginalId, SiriValidator.Version siriVersion, Map<String, List<ValueAdapter>> valueAdaptersByDataset, Map<String, Map<Class, Set<String>>> filterMapByDataset, int cacheTTL,
                                     CompressionFormat compressionFormat, boolean isSicAQuaySubscription) {
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
        this.datasetList = new ArrayList<>();
        if (StringUtils.isNotEmpty(datasetId)) {
            String[] datasets = datasetId.split(",");
            this.datasetList.addAll(Arrays.asList(datasets));
        }

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

        this.compressionFormat = compressionFormat;
        this.isSicAQuaySubscription = isSicAQuaySubscription;
    }

    public List<ValueAdapter> getValueAdaptersForDatasetd(String datasetId) {
        return valueAdaptersByDataset.containsKey(datasetId) ? valueAdaptersByDataset.get(datasetId) : new ArrayList<>();
    }

    public String toString() {
        return MessageFormat.format("[subscriptionId={0}, clientTrackingName={1}, requestorRef={2}, address={3}]", subscriptionId, clientTrackingName, requestorRef, address);
    }

    public boolean hasNotificationBeenAlreadySent(String notificationId) {
        return alreadySentNotifications.getIfPresent(notificationId) != null;
    }

    public void recordNotification(String notificationId) {
        alreadySentNotifications.put(notificationId, "1");
    }
}
