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


import no.rutebanken.anshar.api.GtfsRTApi;
import no.rutebanken.anshar.api.SiriApi;
import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.util.YamlPropertySourceFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@PropertySource(value = "${anshar.subscriptions.config.path}", factory = YamlPropertySourceFactory.class)
@ConfigurationProperties(prefix = "anshar")
@Configuration
public class SubscriptionConfig {

    private List<SubscriptionSetup> subscriptions;

    private List<GtfsRTApi> gtfsRTApis = new ArrayList<>();

    private List<SiriApi> siriApis = new ArrayList<>();

    private List<IdProcessingParameters> idProcessingParametrers = new ArrayList<>();

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

    public List<IdProcessingParameters> getIdProcessingParameters() {
        return idProcessingParametrers;
    }

    public void setSiriApis(List<SiriApi> siriApis) {
        this.siriApis = siriApis;
    }

    public Optional<IdProcessingParameters> getIdParametersForDataset(String datasetId, ObjectType objectType){
        for (IdProcessingParameters idProcessingParametrer : idProcessingParametrers) {
            if (datasetId != null && datasetId.equals(idProcessingParametrer.getDatasetId()) && objectType != null && objectType.equals(idProcessingParametrer.getObjectType())) {
                return Optional.of(idProcessingParametrer);
            }
        }
        return Optional.empty();
    }
}
