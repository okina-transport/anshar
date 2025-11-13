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

package no.rutebanken.anshar.routes.siri.processor;

import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import org.apache.commons.collections4.CollectionUtils;
import uk.org.siri.siri21.PtSituationElement;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.SituationExchangeDeliveryStructure;

import java.util.List;

@Slf4j
public class SXPlannedFeedingProcessor extends ValueAdapter implements PostProcessor {


    public SXPlannedFeedingProcessor() {
    }

    @Override
    protected String apply(String text) {
        return null;
    }

    @Override
    public void process(Siri siri) {

        if (siri == null || siri.getServiceDelivery() == null || siri.getServiceDelivery().getSituationExchangeDeliveries() == null || siri.getServiceDelivery().getSituationExchangeDeliveries().isEmpty()) {
            return;
        }

        for (SituationExchangeDeliveryStructure situationExchangeDelivery : siri.getServiceDelivery().getSituationExchangeDeliveries()) {
            if (CollectionUtils.isEmpty(situationExchangeDelivery.getSituations().getPtSituationElements())) {
                continue;
            }

            for (PtSituationElement ptSituationElement : situationExchangeDelivery.getSituations().getPtSituationElements()) {
                ptSituationElement.setPlanned(CollectionUtils.isEmpty(ptSituationElement.getKeywords()) || !containsIgnoreCase(ptSituationElement.getKeywords(), "inopinée"));
            }
        }
    }

    private boolean containsIgnoreCase(List<String> values, String textToSearch) {
        return values.stream()
                .map(String::toLowerCase)
                .anyMatch(s -> s.equals(textToSearch.toLowerCase()));

    }
}
