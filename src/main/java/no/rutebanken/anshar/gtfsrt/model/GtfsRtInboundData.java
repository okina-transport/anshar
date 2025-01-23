package no.rutebanken.anshar.gtfsrt.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class GtfsRtInboundData {
    protected String url;
    protected String dataSet;
}
