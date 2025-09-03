package no.rutebanken.anshar.gtfsrt.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import uk.org.siri.siri21.EstimatedVehicleJourney;

import java.util.List;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class GtfsRtInboundEt extends GtfsRtInboundData {
    private List<EstimatedVehicleJourney> estimatedVehicleJourneys;
}
