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

package no.rutebanken.anshar.routes.siri.transformer.impl;


import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.routes.mapping.ParkingIdsService;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import org.apache.commons.lang3.StringUtils;
import uk.org.siri.siri21.FacilityRef;

import java.util.Optional;


@Slf4j
public class FacilityRefOutboundIdAdapter extends OutboundIdAdapter {

    private transient ParkingIdsService parkingIdsService;

    public FacilityRefOutboundIdAdapter(OutboundIdMappingPolicy outboundIdMappingPolicy) {
        super(FacilityRef.class, outboundIdMappingPolicy, true);
    }

    @Override
    protected String convertToNetexId(String text) {
        String netexId = text;
        if (parkingIdsService == null) {
            parkingIdsService = ApplicationContextHolder.getContext().getBean(ParkingIdsService.class);
        }
        if (!StringUtils.isEmpty(text)) {
            Optional<String> siteRef = parkingIdsService.getNetexParkingId(text);
            netexId = siteRef.orElse(text);
            if (siteRef.isEmpty()) {
                log.warn("SiteRef id {} not in parking mapping file", text);
            }
        }
        log.debug("originalId: {}, netexId: {}", text, netexId);
        return netexId;
    }

    @Override
    protected String convertToAltId(String datasetId, String text, ObjectType objectType) {
       return text;
    }

}
