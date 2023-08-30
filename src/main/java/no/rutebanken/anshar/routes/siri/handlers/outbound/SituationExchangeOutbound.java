package no.rutebanken.anshar.routes.siri.handlers.outbound;

import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.Situations;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.subscription.helpers.MappingAdapterPresets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.Siri;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SituationExchangeOutbound {

    @Autowired
    private SubscriptionConfig subscriptionConfig;

    public List<ValueAdapter> getValueAdapters(String datasetId, OutboundIdMappingPolicy outboundIdMappingPolicy) {
        List<ValueAdapter> valueAdapters;
        Map<ObjectType, Optional<IdProcessingParameters>> idMap = subscriptionConfig.buildIdProcessingParamsFromDataset(datasetId);
        valueAdapters = MappingAdapterPresets.getOutboundAdapters(SiriDataType.SITUATION_EXCHANGE, outboundIdMappingPolicy, idMap);
        return valueAdapters;
    }
}
