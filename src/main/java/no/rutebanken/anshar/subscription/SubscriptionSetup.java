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

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.ishtar.model.PublishToDisplayAction;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.helpers.DataNotReceivedAction;
import no.rutebanken.anshar.subscription.helpers.FilterMapPresets;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import no.rutebanken.anshar.subscription.helpers.SubscriptionPreset;
import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONObject;

import java.io.Serializable;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;

@Slf4j
public class SubscriptionSetup implements Serializable {

    @Setter
    @Getter
    private long internalId;
    @Setter
    @Getter
    private List<ValueAdapter> mappingAdapters = new ArrayList<>();
    @Setter
    @Getter
    private SiriDataType subscriptionType;
    private String address;
    @Getter
    private Duration heartbeatInterval;
    @Getter
    private Duration updateInterval;
    @Getter
    private Duration previewInterval;
    @Getter
    private Duration changeBeforeUpdates;
    @Setter
    @Getter
    private String operatorNamespace;
    private Map<RequestType, String> urlMap;
    @Setter
    @Getter
    private String subscriptionId;
    @Setter
    @Getter
    private String version;
    @Setter
    @Getter
    private String vendor;
    @Setter
    @Getter
    private String name;
    @Setter
    @Getter
    private String datasetId;
    @Setter
    @Getter
    private ServiceType serviceType;
    @Getter
    private Duration durationOfSubscription;
    @Setter
    @Getter
    private String requestorRef;
    @Setter
    @Getter
    private boolean active;
    @Setter
    @Getter
    private boolean validated;
    @Setter
    @Getter
    private boolean dataSupplyRequestForInitialDelivery;
    @Setter
    @Getter
    private SubscriptionMode subscriptionMode;
    @Setter
    @Getter
    private Map<Class, Set<Object>> filterMap;
    @Setter
    @Getter
    private Map<String, Object> customHeaders;
    @Setter
    @Getter
    private List<String> idMappingPrefixes;
    @Setter
    @Getter
    private String mappingAdapterId;
    private SubscriptionPreset[] filterMapPresets;
    @Setter
    private String addressFieldName;
    @Setter
    private String soapenvNamespace;
    @Setter
    @Getter
    private Boolean incrementalUpdates;
    @Setter
    @Getter
    private String contentType;
    @Setter
    @Getter
    private String vehicleMonitoringRefValue;
    @Setter
    private List<String> lineRefValues;
    private List<String> stopMonitoringRefValues;
    @Setter
    private List<String> siteRefValues;
    @Getter
    private boolean validation;
    @Setter
    @Getter
    private String restartTime;
    @Setter
    @Getter
    private Boolean revertIds;
    @Setter
    @Getter
    private Map<OAuthConfigElement, String> oauth2Config;
    @Getter
    @Setter
    private DataNotReceivedAction dataNotReceivedAction;
    @Setter
    @Getter
    private String validationFilter;
    @Setter
    private boolean forwardPositionData;
    @Getter
    @Setter
    private boolean useProvidedCodespaceId = false;
    @Getter
    @Setter
    private ZonedDateTime startedAt;
    @Getter
    @Setter
    private String consumerAddress;
    @Getter
    @Setter
    private String parentSubscriptionId;
    @Getter
    @Setter
    private boolean overrideDestinationName = false;

    @Getter
    @Setter
    @EqualsAndHashCode.Exclude
    private String lastRequest;
    @Getter
    @Setter
    @EqualsAndHashCode.Exclude
    private String lastResponse;

    @EqualsAndHashCode.Exclude
    private SubscriptionStatus status = SubscriptionStatus.WAITING_FOR_START;

    @Getter
    @Setter
    private Boolean generateSX;

    @Getter
    @Setter
    private PublishToDisplayAction publishToDisplayAction;

    @Getter
    @Setter
    private Boolean skipHeader;

    @Getter
    @Setter
    private Boolean useNamespaceForAnswerParsing;

    public SubscriptionSetup() {
    }

    /**
     * @param subscriptionType       SX, VM, ET, SM
     * @param address                Base-URL for receiving incoming data
     * @param heartbeatInterval      Requested heartbeatinterval for subscriptions, Request-interval for Request/Response "subscriptions"
     * @param operatorNamespace      Namespace
     * @param urlMap                 Operation-names and corresponding URL's
     * @param version                SIRI-version to use
     * @param vendor                 Vendorname - information only
     * @param serviceType            SOAP/REST
     * @param filterMap
     * @param subscriptionId         Sets the subscriptionId to use
     * @param requestorRef           Requestor ref
     * @param durationOfSubscription Initial duration of subscription
     * @param active                 Activates/deactivates subscription
     */
    public SubscriptionSetup(SiriDataType subscriptionType, SubscriptionMode subscriptionMode, String address, Duration heartbeatInterval, Duration updateInterval, String operatorNamespace, Map<RequestType, String> urlMap,
                             String version, String vendor, String datasetId, ServiceType serviceType, List<ValueAdapter> mappingAdapters, Map<Class, Set<Object>> filterMap, List<String> idMappingPrefixes,
                             String subscriptionId, String requestorRef, Duration durationOfSubscription, boolean active, ZonedDateTime startedAt) {
        this.subscriptionType = subscriptionType;
        this.subscriptionMode = subscriptionMode;
        this.address = address;
        this.heartbeatInterval = heartbeatInterval;
        this.updateInterval = updateInterval;
        this.operatorNamespace = operatorNamespace;
        this.urlMap = urlMap;
        this.version = version;
        this.vendor = vendor;
        this.datasetId = datasetId;
        this.serviceType = serviceType;
        this.mappingAdapters = mappingAdapters;
        this.filterMap = filterMap;
        this.idMappingPrefixes = idMappingPrefixes;
        this.subscriptionId = subscriptionId;
        this.requestorRef = requestorRef;
        this.durationOfSubscription = durationOfSubscription;
        this.active = active;
        this.startedAt = startedAt;

        if (!SubscriptionMode.SUBSCRIBE.equals(subscriptionMode)) {
            this.status = SubscriptionStatus.RUNNING;
        }

        if (!isActive()) {
            this.status = SubscriptionStatus.STOPPED;
        }
    }

    public void initConsumerAdressFromParent(String parentVendor, String parentSubscriptionId) {
        this.parentSubscriptionId = parentSubscriptionId;
        this.consumerAddress = MessageFormat.format("/{0}/{1}/{2}/{3}", sanitizeVersion(version), serviceType == ServiceType.REST ? "rs" : "ws", parentVendor, parentSubscriptionId);
    }

    public String buildUrl() {
        return StringUtils.isEmpty(consumerAddress) ? buildUrl(true) : address + consumerAddress;
    }

    public String buildUrl(boolean includeServerAddress) {
        return (includeServerAddress ? address : "") + MessageFormat.format("/{0}/{1}/{2}/{3}", sanitizeVersion(version), serviceType == ServiceType.REST ? "rs" : "ws", vendor, subscriptionId);
    }

    private String sanitizeVersion(String version) {
        if (version == null) {
            return "2.1";
        }
        return version.replace("[", "_").replace("]", "_");
    }

    public String getStartSubscriptionRouteName() {
        return getRouteName("start");
    }

    public String getCancelSubscriptionRouteName() {
        return getRouteName("cancel");
    }

    public String getCheckStatusRouteName() {
        return getRouteName("checkstatus");
    }

    public String getRequestResponseRouteName() {
        return getRouteName("request_response");
    }

    public String getServiceRequestRouteName() {
        return getRouteName("execute_request_response");
    }

    private String getRouteName(String prefix) {
        return prefix + subscriptionId;
    }

    private void setHeartbeatInterval(Duration heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    public Map<RequestType, String> getUrlMap() {
        if (urlMap == null) {
            urlMap = new HashMap<>();
        }
        ensureHttpPrefixes(urlMap);
        return urlMap;
    }

    public void setUrlMap(Map<RequestType, String> urlMap) {
        ensureHttpPrefixes(urlMap);

        this.urlMap = urlMap;
    }

    private void ensureHttpPrefixes(Map<RequestType, String> urlMap) {
        if (urlMap != null && !urlMap.isEmpty()) {
            for (Map.Entry<RequestType, String> entry : urlMap.entrySet()) {
                final String url = entry.getValue();
                if (!url.startsWith("http") && !url.startsWith("https")) {
                    if (!url.isEmpty()) {
                        entry.setValue("http://" + url);
                        //   log.warn("Prefixing url with 'http://': ", entry.getValue());
                    }
                } else if (url.startsWith("https4")) {
                    entry.setValue(url.replaceFirst("https4://", "https://"));
                }
            }
        }
    }

    public String toString() {
        return MessageFormat.format("[vendor={0}, subscriptionId={1}, internalId={2}]", vendor, subscriptionId, internalId);
    }

    public JSONObject toJSON() {
        Map<String, Object> map = new HashMap<>();
        map.put("internalId", getInternalId());
        map.put("vendor", getVendor());
        map.put("name", getName());
        map.put("description", createDescription());
        map.put("datasetId", getDatasetId());
        map.put("subscriptionId", getSubscriptionId());
        map.put("serviceType", getServiceType().toString());
        map.put("subscriptionType", getSubscriptionType().toString());
        map.put("subscriptionMode", getSubscriptionMode().toString());
        map.put("heartbeatInterval", getHeartbeatInterval() != null ? getHeartbeatInterval().toString() : "");
        map.put("previewInterval", getPreviewInterval() != null ? getPreviewInterval().toString() : "");
        map.put("updateInterval", getUpdateInterval() != null ? getUpdateInterval().toString() : "");
        map.put("changeBeforeUpdates", getChangeBeforeUpdates() != null ? getChangeBeforeUpdates().toString() : "");
        map.put("incrementalUpdates", getIncrementalUpdates() != null ? getIncrementalUpdates().toString() : "");
        map.put("durationOfSubscription", getDurationOfSubscription().toString());
        map.put("requestorRef", getRequestorRef());
        map.put("inboundUrl", buildUrl(true));
        map.put("validation", isValidation());
        map.put("validationFilter", getValidationFilter());
        map.put("contentType", getContentType());
        map.put("restartTime", getRestartTime());
        map.put("forwardPositionData", forwardPositionData());
        return new JSONObject(map);
    }

    private String createDescription() {
        String description = "";
        if (subscriptionMode.equals(SubscriptionMode.SUBSCRIBE)) {
            description = urlMap.getOrDefault(RequestType.SUBSCRIBE, "");
        } else {
            if (subscriptionType.equals(SiriDataType.ESTIMATED_TIMETABLE)) {
                description = urlMap.getOrDefault(RequestType.GET_ESTIMATED_TIMETABLE, "");
            }
            if (subscriptionType.equals(SiriDataType.VEHICLE_MONITORING)) {
                description = urlMap.getOrDefault(RequestType.GET_VEHICLE_MONITORING, "");
            }
            if (subscriptionType.equals(SiriDataType.SITUATION_EXCHANGE)) {
                description = urlMap.getOrDefault(RequestType.GET_SITUATION_EXCHANGE, "");
            }
        }
        if (description.contains("?")) {
            description = description.substring(0, description.indexOf("?"));
        }
        return description;
    }

    public void setFilterPresets(SubscriptionPreset[] presets) {
        this.filterMapPresets = presets;
        filterMap = new HashMap<>();
        for (SubscriptionPreset preset : presets) {
            addFilterMap(new FilterMapPresets().get(preset));
        }
    }

    private void addFilterMap(Map<Class, Set<Object>> filters) {
        if (this.filterMap == null) {
            this.filterMap = new HashMap<>();
        }
        this.filterMap.putAll(filters);
    }

    private void setPreviewInterval(Duration previewIntervalSeconds) {
        this.previewInterval = previewIntervalSeconds;
    }

    public String getAddressFieldName() {
        if (addressFieldName != null && addressFieldName.isEmpty()) {
            return null;
        }
        return addressFieldName;
    }

    public String getSoapenvNamespace() {
        if (soapenvNamespace != null && soapenvNamespace.isEmpty()) {
            return null;
        }
        return soapenvNamespace;
    }

    public List<String> getLineRefValues() {
        if (lineRefValues == null) {
            lineRefValues = new ArrayList<>();
        }
        return lineRefValues;
    }

    public List<String> getStopMonitoringRefValues() {

        if (stopMonitoringRefValues == null) {
            stopMonitoringRefValues = new ArrayList<>();
        }
        return stopMonitoringRefValues;
    }

    public List<String> getSiteRefValues() {
        if (siteRefValues == null) {
            siteRefValues = new ArrayList<>();
        }
        return siteRefValues;
    }

    public void setStopMonitoringRefValue(List<String> stopMonitoringRefValues) {
        this.stopMonitoringRefValues = stopMonitoringRefValues;
    }

    private void setChangeBeforeUpdates(Duration changeBeforeUpdates) {
        this.changeBeforeUpdates = changeBeforeUpdates;
    }

    public void setValidation(boolean validation) {
        this.validation = validation;
        if (!this.validation) {
            //Reset validationFilter when validation is disabled
            setValidationFilter(null);
        }
    }

    public void setAddress(String address) {
        if (address.endsWith("/")) {
            address = address.substring(0, address.length() - 1);
        }
        this.address = address;
    }

    public void setHeartbeatIntervalSeconds(long seconds) {
        if (seconds > 0) {
            setHeartbeatInterval(Duration.ofSeconds(seconds));
        }
    }

    private void setUpdateInterval(Duration updateInterval) {
        this.updateInterval = updateInterval;
    }

    public void setUpdateIntervalSeconds(long seconds) {
        setUpdateInterval(Duration.ofSeconds(seconds));
    }

    public void setPreviewIntervalSeconds(long seconds) {
        setPreviewInterval(Duration.ofSeconds(seconds));
    }

    public void setChangeBeforeUpdatesSeconds(long seconds) {
        if (seconds > 0) {
            setChangeBeforeUpdates(Duration.ofSeconds(seconds));
        }
    }

    public void setDurationOfSubscriptionHours(long hours) {
        this.durationOfSubscription = Duration.ofHours(hours);
    }

    public void setDurationOfSubscriptionMinutes(int minutes) {
        this.durationOfSubscription = Duration.ofMinutes(minutes);
    }


    public String getBaseRouteId() {
        String customServiceType = serviceType != null ? serviceType.name() : "emptyServiceType";
        String customSubscriptionMode = subscriptionMode != null ? subscriptionMode.name() : "emptySubscriptionMode";
        String customVersion = version != null ? version : "emptyVersion";
        String customVendor = vendor != null ? vendor : "emptyVendor";
        return customServiceType + "." + customSubscriptionMode + "." + customVendor + "." + customVersion + "." + subscriptionId;
    }

    public boolean forwardPositionData() {
        return forwardPositionData;
    }

    /**
     * Variant of equals that only compares fields crucial to detect updated subscription-config
     * NOTE: e.g. subscriptionId is NOT compared
     *
     * @param o other object
     * @return true if crucial config-elements are equal
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SubscriptionSetup that)) {
            return false;
        }

        return this.subscriptionId.equals(that.subscriptionId);
    }

    public enum ServiceType {SOAP, REST}

    public enum SubscriptionMode {SUBSCRIBE, REQUEST_RESPONSE, POLLING_FETCHED_DELIVERY, FETCHED_DELIVERY, LITE, LITE_XML, WEBSOCKET, BIG_DATA_EXPORT, VM_POSITION_FORWARDING}

    public SubscriptionStatus getStatus() {
        return status;
    }

    public void setStatus(SubscriptionStatus status) {
        this.status = status;
    }
}
