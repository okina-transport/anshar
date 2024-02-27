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


import io.micrometer.core.instrument.util.StringUtils;
import no.rutebanken.anshar.api.GtfsRTApi;
import no.rutebanken.anshar.api.SiriApi;
import no.rutebanken.anshar.config.DiscoverySubscription;
import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.util.YamlPropertySourceFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@PropertySource(value = "${anshar.subscriptions.config.path}", factory = YamlPropertySourceFactory.class)
@ConfigurationProperties(prefix = "anshar")
@Configuration
public class SubscriptionConfig {

    private List<SubscriptionSetup> subscriptions = new CopyOnWriteArrayList();

    private List<GtfsRTApi> gtfsRTApis = new CopyOnWriteArrayList<>();

    private List<SiriApi> siriApis = new CopyOnWriteArrayList<>();

    private List<DiscoverySubscription> discoverySubscriptions = new ArrayList<>();

    private List<IdProcessingParameters> idProcessingParameters = new CopyOnWriteArrayList<>();

    @Value("${anshar.subscriptions.datatypes.filter:}")
    List<SiriDataType> dataTypes;


    public List<SubscriptionSetup> getSubscriptions() {
        if (dataTypes != null && !dataTypes.isEmpty()) {
            return subscriptions.stream()
                    .filter(sub -> dataTypes.contains(sub.getSubscriptionType()))
                    .collect(Collectors.toList());
        }
        return subscriptions;
    }

    public void setSubscriptions(List<SubscriptionSetup> subscriptions) {
        this.subscriptions = subscriptions;
    }

    public List<GtfsRTApi> getGtfsRTApis() {
        return gtfsRTApis;
    }

    public void setGtfsRTApis(List<GtfsRTApi> gtfsRTApis) {
        this.gtfsRTApis = gtfsRTApis;
    }

    public List<SiriApi> getSiriApis() {
        return siriApis;
    }

    public List<DiscoverySubscription> getDiscoverySubscriptions() {
        return discoverySubscriptions;
    }

    public List<IdProcessingParameters> getIdProcessingParameters() {
        return idProcessingParameters;
    }

    public void setSiriApis(List<SiriApi> siriApis) {
        this.siriApis = siriApis;
    }

    public void setDiscoverySubscriptions(List<DiscoverySubscription> discoverySubscriptions) {
        this.discoverySubscriptions = discoverySubscriptions;
    }

    public void mergeIdProcessingParams(List<IdProcessingParameters> incomingParams) {
        for (IdProcessingParameters incomingParam : incomingParams) {
            Optional<IdProcessingParameters> existingOpt = getExistingIdProc(incomingParam);
            if (existingOpt.isPresent()) {
                //idProc is already existing in the list. Updating
                IdProcessingParameters existingIdProc = existingOpt.get();
                existingIdProc.setInputPrefixToRemove(incomingParam.getInputPrefixToRemove());
                existingIdProc.setInputSuffixToRemove(incomingParam.getInputSuffixToRemove());
                existingIdProc.setOutputPrefixToAdd(incomingParam.getOutputPrefixToAdd());
                existingIdProc.setOutputSuffixToAdd(incomingParam.getOutputSuffixToAdd());
            } else {
                idProcessingParameters.add(incomingParam);
            }
        }
    }

    private Optional<IdProcessingParameters> getExistingIdProc(IdProcessingParameters incomingParam) {
        for (IdProcessingParameters idProcessingParameter : idProcessingParameters) {
            if (idProcessingParameter.getDatasetId().equals(incomingParam.getDatasetId()) &&
                    idProcessingParameter.getObjectType().equals(incomingParam.getObjectType())) {
                return Optional.of(idProcessingParameter);
            }
        }
        return Optional.empty();
    }


    public void mergeSiriApis(List<SiriApi> incomingSiriApis) {
        for (SiriApi incomingAPI : incomingSiriApis) {
            Optional<SiriApi> existingOpt = getExistingSiriAPI(incomingAPI);
            if (existingOpt.isPresent()) {
                //API is already existing in the list. Updatig the status
                existingOpt.get().setActive(incomingAPI.getActive());
            } else {
                siriApis.add(incomingAPI);
            }
        }


    }

    private Optional<SiriApi> getExistingSiriAPI(SiriApi incomingAPI) {
        for (SiriApi existingSiriApi : siriApis) {
            // same API = same URL & same datasetId & same type
            if (existingSiriApi.getUrl().equals(incomingAPI.getUrl()) && existingSiriApi.getDatasetId().equals(incomingAPI.getDatasetId())
                    && existingSiriApi.getType().equals(incomingAPI.getType())) {
                return Optional.of(existingSiriApi);
            }
        }
        return Optional.empty();

    }


    public void mergeGTFSRTApis(List<GtfsRTApi> incomingGtfsRTApis) {
        for (GtfsRTApi incomingAPI : incomingGtfsRTApis) {
            Optional<GtfsRTApi> existingOpt = getExistingGtfsAPI(incomingAPI);
            if (existingOpt.isPresent()) {
                //API is already existing in the list. Updatig the status
                existingOpt.get().setActive(incomingAPI.getActive());
            } else {
                gtfsRTApis.add(incomingAPI);
            }
        }
    }

    public void mergeSubscriptions(List<SubscriptionSetup> incomingSubscriptions) {
        for (SubscriptionSetup incomingSubscription : incomingSubscriptions) {

            Optional<SubscriptionSetup> existingSubscription = getExistingSubscription(incomingSubscription);
            if (existingSubscription.isPresent()) {
                existingSubscription.get().setActive(incomingSubscription.isActive());
            } else {
                subscriptions.add(incomingSubscription);
            }
        }


    }

    private Optional<SubscriptionSetup> getExistingSubscription(SubscriptionSetup incomingSubscription) {
        for (SubscriptionSetup subscription : subscriptions) {
            if (incomingSubscription.getSubscriptionId().equals(subscription.getSubscriptionId())) {
                return Optional.of(subscription);
            }
        }
        return Optional.empty();
    }

    private Optional<GtfsRTApi> getExistingGtfsAPI(GtfsRTApi incomingAPI) {

        for (GtfsRTApi existingGtfsRTApi : gtfsRTApis) {
            // same API = same URL & same datasetId
            if (existingGtfsRTApi.getUrl().equals(incomingAPI.getUrl()) && existingGtfsRTApi.getDatasetId().equals(incomingAPI.getDatasetId())) {
                return Optional.of(existingGtfsRTApi);
            }
        }
        return Optional.empty();
    }

    public Optional<IdProcessingParameters> getIdParametersForDataset(String datasetId, ObjectType objectType) {
        for (IdProcessingParameters idProcessingParametrer : idProcessingParameters) {
            if (datasetId != null && datasetId.equalsIgnoreCase(idProcessingParametrer.getDatasetId()) && objectType != null && objectType.equals(idProcessingParametrer.getObjectType())) {
                return Optional.of(idProcessingParametrer);
            }
        }
        return Optional.empty();
    }

    public Map<ObjectType, Optional<IdProcessingParameters>> buildIdProcessingParams(String datasetId, Set<String> originalMonitoringRefs, ObjectType objectType) {

        if (StringUtils.isEmpty(datasetId)) {
            datasetId = findDatasetFromSearch(originalMonitoringRefs, objectType).orElse(null);
        }

        return buildIdProcessingParamsFromDataset(datasetId);
    }

    public Optional<String> findDatasetFromSearch(Set<String> searchedIds, ObjectType objectType) {

        Set<String> guessedDatasets = new HashSet<>();

        for (String searchedId : searchedIds) {

            for (IdProcessingParameters idProcessingParameter : getIdProcessingParameters()) {

                if (StringUtils.isEmpty(idProcessingParameter.getOutputPrefixToAdd()) && StringUtils.isEmpty(idProcessingParameter.getOutputSuffixToAdd())) {
                    continue;
                }

                if (objectType != null && objectType.equals(idProcessingParameter.getObjectType()) &&
                        (StringUtils.isEmpty(idProcessingParameter.getOutputPrefixToAdd()) || searchedId.startsWith(idProcessingParameter.getOutputPrefixToAdd()))
                        &&
                        (StringUtils.isEmpty(idProcessingParameter.getOutputSuffixToAdd()) || searchedId.endsWith(idProcessingParameter.getOutputSuffixToAdd()))) {
                    guessedDatasets.add(idProcessingParameter.getDatasetId());
                }
            }
        }

        return guessedDatasets.size() == 1 ? guessedDatasets.stream().findFirst() : Optional.empty();

    }

    public Map<ObjectType, Optional<IdProcessingParameters>> buildIdProcessingParamsFromDataset(String datasetId) {
        Map<ObjectType, Optional<IdProcessingParameters>> resultmap = new HashMap<>();
        resultmap.put(ObjectType.STOP, getIdParametersForDataset(datasetId, ObjectType.STOP));
        resultmap.put(ObjectType.LINE, getIdParametersForDataset(datasetId, ObjectType.LINE));
        resultmap.put(ObjectType.VEHICLE_JOURNEY, getIdParametersForDataset(datasetId, ObjectType.VEHICLE_JOURNEY));
        resultmap.put(ObjectType.OPERATOR, getIdParametersForDataset(datasetId, ObjectType.OPERATOR));
        resultmap.put(ObjectType.NETWORK, getIdParametersForDataset(datasetId, ObjectType.NETWORK));
        return resultmap;
    }

    public Set<String> getSXDatasetIds() {
        Set<String> sxDatasetIds = subscriptions.stream()
                .filter(subscription -> subscription.getSubscriptionType().equals(SiriDataType.SITUATION_EXCHANGE))
                .map(SubscriptionSetup::getDatasetId)
                .collect(Collectors.toSet());


        for (GtfsRTApi gtfsRTApi : gtfsRTApis) {
            sxDatasetIds.add(gtfsRTApi.getDatasetId());
        }
        return sxDatasetIds;
    }
}
