package no.rutebanken.anshar.metrics;

import org.apache.camel.Exchange;


import static no.rutebanken.anshar.routes.validation.validators.Constants.INBOUND_TIME_HEADER_NAME;


public class InboundTimeProcessor {

    public static void setInboundTime(Exchange exchange) {

        if (exchange.getIn().getHeader(INBOUND_TIME_HEADER_NAME) == null) {
            exchange.getIn().setHeader(INBOUND_TIME_HEADER_NAME, System.currentTimeMillis());
        }

    }
}
