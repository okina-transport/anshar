package no.rutebanken.anshar.routes.kafka;


import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.config.AnsharConfiguration;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

import static no.rutebanken.anshar.routes.kafka.KafkaHeaders.*;
import static no.rutebanken.anshar.routes.validation.validators.Constants.DATASET_ID_HEADER_NAME;

/**
 * Processor to remove all headers except kafka headers
 */
@Slf4j
@Component
public class KeepOnlyKafkaHeaders implements Processor {

    private final AnsharConfiguration config;

    public KeepOnlyKafkaHeaders(AnsharConfiguration config) {
        this.config = config;
    }


    @Override
    public void process(Exchange exchange) throws Exception {
        Map<String, Object> originalHeaders = exchange.getIn().getHeaders();

        String datasetId = (String) originalHeaders.getOrDefault(DATASET_ID_HEADER_NAME, null);
        String requestorRefs = (String) originalHeaders.getOrDefault(REQUESTOR_REFS_HEADER, null);
        String consumerAddress = (String) originalHeaders.getOrDefault(CONSUMER_ADDRESS_HEADER, null);

        exchange.getIn().getHeaders().clear();

        exchange.getIn().setHeader(CLIENT_HEADER, config.getClientName());
        exchange.getIn().setHeader(ENV_HEADER, config.getEnvironment());
        if (StringUtils.isNotEmpty(requestorRefs)) {
            exchange.getIn().setHeader(REQUESTOR_REFS_HEADER, requestorRefs);
        }

        if (StringUtils.isNotEmpty(datasetId)) {
            exchange.getIn().setHeader(DATASET_ID_HEADER_NAME, datasetId);
        }

        if (StringUtils.isNotEmpty(consumerAddress)) {
            exchange.getIn().setHeader(CONSUMER_ADDRESS_HEADER, consumerAddress);
        }

    }
}
