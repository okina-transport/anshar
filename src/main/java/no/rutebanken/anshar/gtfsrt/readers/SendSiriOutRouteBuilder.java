package no.rutebanken.anshar.gtfsrt.readers;

import no.rutebanken.anshar.routes.dataformat.SiriDataFormatHelper;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

import static no.rutebanken.anshar.routes.validation.validators.Constants.*;

@Service
public class SendSiriOutRouteBuilder extends RouteBuilder {

    private static final String ACTIVEMQ_PREFIX = "activemq:queue:";
    private static final String ENV_HEADER_NAME = "env";

    @Value("${external.sx.consumer.queue}")
    private String externalSxQueue;

    @Value("${siri.sm.kafka.queue}")
    private String siriSMKafkaQueue;

    @Value("${anshar.send.sx.to.kafka.uri:kafka:{{kafka.topic.sx}}?brokers{{kafka.brokers}}&clientId={{kafka.client-id.anshar}}}")
    private String sxToKafkaUri;

    @Value("${anshar.env}")
    private String env;

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

        from("direct:send.sm.to.kafka")
                .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                .setExchangePattern(ExchangePattern.InOnly)
                .to(siriSMKafkaQueue)
        ;

        from("direct:send.sx.to.kafka")
                .log(LoggingLevel.INFO, "Send SX to " + sxToKafkaUri + " for env " + env)
                .marshal(SiriDataFormatHelper.getThreadSafeSiriJaxbDataformat())
                .setHeader(ENV_HEADER_NAME, constant(env.getBytes(StandardCharsets.UTF_8)))
                .setExchangePattern(ExchangePattern.InOnly)
                .to(sxToKafkaUri)
        ;

        from("direct:send.sx.to.external.consumer")
                .marshal(SiriDataFormatHelper.getThreadSafeSiriJaxbDataformat())
                .setExchangePattern(ExchangePattern.InOnly)
                .to(externalSxQueue)
        ;

        from("direct:send.vm.to.kafka")
                .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                .setExchangePattern(ExchangePattern.InOnly)
                .to(ACTIVEMQ_PREFIX + SIRI_VM_KAFKA_QUEUE)
        ;

        from("direct:send.et.to.kafka")
                .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                .setExchangePattern(ExchangePattern.InOnly)
                .to(ACTIVEMQ_PREFIX + SIRI_ET_KAFKA_QUEUE)
        ;

        from("direct:send.gm.to.kafka")
                .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                .setExchangePattern(ExchangePattern.InOnly)
                .to(ACTIVEMQ_PREFIX + SIRI_GM_KAFKA_QUEUE)
        ;

        from("direct:send.fm.to.kafka")
                .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                .setExchangePattern(ExchangePattern.InOnly)
                .to(ACTIVEMQ_PREFIX + SIRI_FM_KAFKA_QUEUE)
        ;

    }
}
