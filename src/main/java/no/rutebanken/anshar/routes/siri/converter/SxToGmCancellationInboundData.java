package no.rutebanken.anshar.routes.siri.converter;

import lombok.Builder;
import lombok.Getter;
import uk.org.siri.siri21.GeneralMessageCancellation;

import java.util.List;

@Builder
@Getter
public class SxToGmCancellationInboundData {
    private String datasetId;
    private List<GeneralMessageCancellation> incoming;
    private Long inboundTime;
    @Builder.Default
    private boolean publishToOutbound = true;
    @Builder.Default
    private boolean convertGmToSx = false;
}
