package no.rutebanken.anshar.routes.siri.converter;

import lombok.Builder;
import lombok.Getter;
import uk.org.siri.siri21.GeneralMessage;

import java.util.List;

@Builder
@Getter
public class SxToGmInboundData {
    private String datasetId;
    private List<GeneralMessage> incoming;
    private Long inboundTime;
    @Builder.Default
    private boolean publishToOutbound = true;
    @Builder.Default
    private boolean convertGmToSx = false;
}
