package no.rutebanken.anshar.gtfsrt.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import uk.org.siri.siri21.PtSituationElement;

import java.util.List;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class GtfsRtInboundSx extends GtfsRtInboundData {
    private List<PtSituationElement> situations;
}
