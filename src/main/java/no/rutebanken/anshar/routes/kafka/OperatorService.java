package no.rutebanken.anshar.routes.kafka;

import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.routes.mapping.ParkingIdsService;
import no.rutebanken.anshar.routes.mapping.PoiIdsService;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;
import uk.org.siri.siri21.PtSituationElement;
import uk.org.siri.siri21.Siri;

import java.util.HashSet;
import java.util.Set;

@Component
@Slf4j
public class OperatorService {

    private final ParkingIdsService parkingIdsService;

    private final PoiIdsService poiIdsService;

    public OperatorService(ParkingIdsService parkingIdsService, PoiIdsService poiIdsService) {
        this.parkingIdsService = parkingIdsService;
        this.poiIdsService = poiIdsService;
    }

    public Set<String> getSxOperators(Siri siri) {
        Set<String> operators = new HashSet<>();
        if (siri == null
                || siri.getServiceDelivery() == null
                || CollectionUtils.isEmpty(siri.getServiceDelivery().getSituationExchangeDeliveries())
                || siri.getServiceDelivery().getSituationExchangeDeliveries().getFirst().getSituations() == null
                || CollectionUtils.isEmpty(siri.getServiceDelivery().getSituationExchangeDeliveries().getFirst().getSituations().getPtSituationElements())
        ) {
           return operators;
        }
        PtSituationElement ptSituationElement = siri.getServiceDelivery().getSituationExchangeDeliveries().getFirst().getSituations().getPtSituationElements().getFirst();
        if (ptSituationElement.getAffects() == null) {
           return operators;
        }
        if (ptSituationElement.getAffects().getPlaces() != null && CollectionUtils.isNotEmpty(ptSituationElement.getAffects().getPlaces().getAffectedPlaces())) {
            for (var affectedPlace : ptSituationElement.getAffects().getPlaces().getAffectedPlaces()) {
                parkingIdsService.getOperatorByNetexId(affectedPlace.getPlaceRef()).ifPresentOrElse(
                        operators::add,
                        () -> poiIdsService.getOperatorByNetexId(affectedPlace.getPlaceRef()).ifPresentOrElse(
                                operators::add,
                                () -> log.info("PlaceRef {} not a known parking or POI id", affectedPlace.getPlaceRef())
                        )
                );
            }
        }
        if (ptSituationElement.getAffects().getNetworks() != null && CollectionUtils.isNotEmpty(ptSituationElement.getAffects().getNetworks().getAffectedNetworks())) {
            operators.add("Semitan");
        }
        return operators;
    }

}
