package no.rutebanken.anshar.routes.siri.handlers.inbound;

import no.rutebanken.anshar.data.Situations;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.routes.siri.converter.SxInboundData;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.PtSituationElement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service
public class SituationExchangeWriter {

    private final Situations situations;
    private final ServerSubscriptionManager serverSubscriptionManager;

    public SituationExchangeWriter(Situations situations, ServerSubscriptionManager serverSubscriptionManager) {
        this.situations = situations;
        this.serverSubscriptionManager = serverSubscriptionManager;
    }

    public Collection<PtSituationElement> write(SxInboundData sxInboundData) {
        String datasetId = sxInboundData.getDatasetId();
        List<PtSituationElement> incomingSituations = sxInboundData.getIncomingSituations();
        boolean publishToOutbound = sxInboundData.isPublishToOutbound();
        Long inboundTime = sxInboundData.getInboundTime();
        Collection<PtSituationElement> result = situations.addAll(datasetId, incomingSituations);
        if (publishToOutbound && CollectionUtils.isNotEmpty(result)) {
            serverSubscriptionManager.pushUpdatesAsync(SiriDataType.SITUATION_EXCHANGE, new ArrayList<>(result), datasetId, inboundTime);
        }
        return result;
    }
}
