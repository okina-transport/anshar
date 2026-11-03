package no.rutebanken.anshar.routes.kafka;

import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.subscription.helpers.MappingAdapterPresets;
import no.rutebanken.anshar.util.SiriUtils;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;
import uk.org.siri.siri21.Siri;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static no.rutebanken.anshar.routes.validation.validators.Constants.DATASET_ID_HEADER_NAME;

@Slf4j
@Component
public class IdProcessingProcessor implements Processor {

    private final SubscriptionConfig subscriptionConfig;

    public IdProcessingProcessor(SubscriptionConfig subscriptionConfig) {
        this.subscriptionConfig = subscriptionConfig;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        final Siri siri = exchange.getIn().getBody(Siri.class);
        if (siri == null) {
            log.warn("No Siri message to apply IPP to");
            return;
        }
        String datasetId = exchange.getMessage().getHeader(DATASET_ID_HEADER_NAME, String.class);
        Map<ObjectType, Optional<IdProcessingParameters>> idProcessingParameters = subscriptionConfig.buildIdProcessingParamsFromDataset(datasetId);
        SiriUtils.getSiriDataType(siri).ifPresent(type -> {
            List<ValueAdapter> valueAdapters =  MappingAdapterPresets.getOutboundAdapters(type, OutboundIdMappingPolicy.ORIGINAL_ID, idProcessingParameters);
            Siri siriTransformed = SiriValueTransformer.transform(siri, valueAdapters, true, false);
            exchange.getIn().setBody(siriTransformed);
        });
    }
}