package no.rutebanken.anshar.gtfsrt.readers;

import no.rutebanken.anshar.routes.dataformat.SiriDataFormatHelper;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static no.rutebanken.anshar.routes.validation.validators.Constants.*;

@Service
public class SendSiriOutRouteBuilder extends RouteBuilder {

    private static final String ACTIVEMQ_PREFIX = "activemq:queue:";

    @Value("${external.sx.consumer.queue}")
    private String externalSxQueue;

    @Override
    public void configure() {

        from("direct:send.sm.to.realtime.server")
                .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                .setExchangePattern(ExchangePattern.InOnly)
                .to(ACTIVEMQ_PREFIX + GTFSRT_SM_QUEUE)
        ;

        from("direct:send.sx.to.realtime.server")
                .marshal(SiriDataFormatHelper.getThreadSafeSiriJaxbDataformat())
                .setExchangePattern(ExchangePattern.InOnly)
                .to(ACTIVEMQ_PREFIX + GTFSRT_SX_QUEUE)
        ;

        from("direct:send.vm.to.realtime.server")
                .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                .setExchangePattern(ExchangePattern.InOnly)
                .to(ACTIVEMQ_PREFIX + GTFSRT_VM_QUEUE)
        ;

        from("direct:send.et.to.realtime.server")
                .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                .setExchangePattern(ExchangePattern.InOnly)
                .to(ACTIVEMQ_PREFIX + GTFSRT_ET_QUEUE)
        ;

        from("direct:send.sx.to.external.consumer")
                .marshal(SiriDataFormatHelper.getThreadSafeSiriJaxbDataformat())
                .setExchangePattern(ExchangePattern.InOnly)
                .to(externalSxQueue)
        ;

    }
}
