package no.rutebanken.anshar.gtfsrt.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import uk.org.siri.siri21.MonitoredStopVisit;
import uk.org.siri.siri21.MonitoredStopVisitCancellation;

import java.util.List;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class GtfsRtInboundSm extends GtfsRtInboundData {
    private List<MonitoredStopVisit> stopVisits;
    private List<MonitoredStopVisitCancellation> stopCancellations;
}
