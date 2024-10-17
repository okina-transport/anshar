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

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.helpers.DataNotReceivedAction;
import no.rutebanken.anshar.subscription.helpers.FilterMapPresets;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import no.rutebanken.anshar.subscription.helpers.SubscriptionPreset;
import org.json.simple.JSONObject;

import java.io.Serializable;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.*;

@Slf4j
public class SubscriptionSetup implements Serializable {

    @Setter
    @Getter
    private long internalId;
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
    @Setter
    private boolean enrichSiriData = false;

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
                             String subscriptionId, String requestorRef, Duration durationOfSubscription, boolean active) {
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
    }

    public String buildUrl() {
        return buildUrl(true);
    }

    public String buildUrl(boolean includeServerAddress) {
        return (includeServerAddress ? address : "") + MessageFormat.format("/{0}/{1}/{2}/{3}", version, serviceType == ServiceType.REST ? "rs" : "ws", vendor, subscriptionId);
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
        ensureHttpPrefixes(urlMap);
        return urlMap;
    }

    public void setUrlMap(Map<RequestType, String> urlMap) {
        ensureHttpPrefixes(urlMap);

        this.urlMap = urlMap;
    }

    private void ensureHttpPrefixes(Map<RequestType, String> urlMap) {
        if (urlMap != null) {
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
        map.put("activated", isActive());
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

    public boolean enrichSiriData() {
        return enrichSiriData;
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
        if (!(o instanceof SubscriptionSetup)) {
            return false;
        }

        SubscriptionSetup that = (SubscriptionSetup) o;

        if (getInternalId() != that.getInternalId()) {
            log.info("getInternalId() does not match [{}] vs [{}]", getInternalId(), that.getInternalId());
            return false;
        }
        if (getSubscriptionType() != that.getSubscriptionType()) {
            log.info("getSubscriptionType() does not match [{}] vs [{}]", getSubscriptionType(), that.getSubscriptionType());
            return false;
        }
        if (!address.equals(that.address)) {
            log.info("address does not match [{}] vs [{}]", address, that.address);
            return false;
        }
        if (getOperatorNamespace() != null ? !getOperatorNamespace().equals(that.getOperatorNamespace()) : that.getOperatorNamespace() != null) {
            log.info("getOperatorNamespace() does not match [{}] vs [{}]", getOperatorNamespace(), that.getOperatorNamespace());
            return false;
        }
//        if (!getUrlMap().equals(that.getUrlMap())) {
//            log.info("getUrlMap() does not match [{}] vs [{}]", getUrlMap(), that.getUrlMap());
//            return false;
//        }
        if (!getVersion().equals(that.getVersion())) {
            log.info("getVersion() does not match [{}] vs [{}]", getVersion(), that.getVersion());
            return false;
        }
        if (!getVendor().equals(that.getVendor())) {
            log.info("getVendor() does not match [{}] vs [{}]", getVendor(), that.getVendor());
            return false;
        }
        if (!getDatasetId().equals(that.getDatasetId())) {
            log.info("getDatasetId() does not match [{}] vs [{}]", getDatasetId(), that.getDatasetId());
            return false;
        }
        if (getServiceType() != that.getServiceType()) {
            log.info("getServiceType() does not match [{}] vs [{}]", getServiceType(), that.getServiceType());
            return false;
        }
        if (getDurationOfSubscription() != null ? !getDurationOfSubscription().equals(that.getDurationOfSubscription()) : that.getDurationOfSubscription() != null) {
            log.info("getDurationOfSubscription() does not match [{}] vs [{}]", getDurationOfSubscription(), that.getDurationOfSubscription());
            return false;
        }
        if (getSubscriptionMode() != that.getSubscriptionMode()) {
            log.info("getSubscriptionMode() does not match [{}] vs [{}]", getSubscriptionMode(), that.getSubscriptionMode());
            return false;
        }
        if (getIdMappingPrefixes() != null ? !getIdMappingPrefixes().equals(that.getIdMappingPrefixes()) : that.getIdMappingPrefixes() != null) {
            log.info("getIdMappingPrefixes() does not match [{}] vs [{}]", getIdMappingPrefixes(), that.getIdMappingPrefixes());
            return false;
        }
        if (getMappingAdapterId() != null ? !getMappingAdapterId().equals(that.getMappingAdapterId()) : that.getMappingAdapterId() != null) {
            log.info("getMappingAdapterId() does not match [{}] vs [{}]", getMappingAdapterId(), that.getMappingAdapterId());
            return false;
        }
        if (!Arrays.equals(filterMapPresets, that.filterMapPresets)) {
            log.info("filterMapPresets does not match [{}] vs [{}]", filterMapPresets, that.filterMapPresets);
            return false;
        }
        return true;
    }

    public enum ServiceType {SOAP, REST}

    public enum SubscriptionMode {SUBSCRIBE, REQUEST_RESPONSE, POLLING_FETCHED_DELIVERY, FETCHED_DELIVERY, LITE, LITE_XML, WEBSOCKET, BIG_DATA_EXPORT, VM_POSITION_FORWARDING}
}
