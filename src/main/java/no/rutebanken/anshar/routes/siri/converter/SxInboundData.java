package no.rutebanken.anshar.routes.siri.converter;

import lombok.Builder;
import lombok.Getter;
import uk.org.siri.siri21.PtSituationElement;

import java.util.List;

@Builder
@Getter
public class SxInboundData {
    private String datasetId;
    private List<PtSituationElement> incomingSituations;
    private Long inboundTime;
    @Builder.Default
    private boolean publishToOutbound = true;
    @Builder.Default
    private boolean convertSxToGm = true;
}
