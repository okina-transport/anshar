package no.rutebanken.anshar.routes.kafka;

import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.routes.dataformat.SiriDataFormatHelper;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class KafkaRouteBuilder extends RouteBuilder {


    public static final String SEND_SX_TO_KAFKA = "direct:send.sx.to.kafka";
    public static final String SEND_TH_TR_CONSISTENCY_REPORT_TO_KAFKA = "direct:send.th.tr.consistency.report.to.kafka";
    public static final String SEND_TR_IN_SUBSCRIPTION_DATA_TO_KAFKA = "direct:send.tr.in.subscription.data.to.kafka";

    private final KafkaConfig kafkaConfig;
    private final AnsharConfiguration config;

    public KafkaRouteBuilder(KafkaConfig kafkaConfig, AnsharConfiguration config) {
        this.kafkaConfig = kafkaConfig;
        this.config = config;
    }

    @Override
    public void configure() throws Exception {
        if (kafkaConfig.isKafkaEnabled() && kafkaConfig.isSendSiriToKafka()) {
            from(SEND_SX_TO_KAFKA)
                    .log(LoggingLevel.INFO, "Sending SX message to KAFKA")
                    .marshal(SiriDataFormatHelper.getThreadSafeSiriJaxbDataformat())
                    .removeHeaders("*")
                    .setHeader(KafkaHeaders.ENV_HEADER,
                            constant(config.getEnvironment().getBytes(StandardCharsets.UTF_8)))
                    .wireTap(kafkaConfig.createCamelProducerConfig(kafkaConfig.getSxTopic()));
        } else {
            from(SEND_SX_TO_KAFKA)
                    .log(LoggingLevel.WARN, "Sending SIRI messages to KAFKA is disabled")
                    .end();
        }
        if (kafkaConfig.isKafkaEnabled() && kafkaConfig.isSendTrInSubscriptionDataToKafka()) {
            from(SEND_TR_IN_SUBSCRIPTION_DATA_TO_KAFKA)
                    .log(LoggingLevel.INFO, "Sending TR in subscription data to KAFKA")
                    .marshal()
                    .json()
                    .removeHeaders("*")
                    .setHeader(KafkaHeaders.ENV_HEADER, constant(config.getEnvironment().getBytes(StandardCharsets.UTF_8)))
                    .setHeader(KafkaHeaders.CLIENT_HEADER, constant(config.getClientName().getBytes(StandardCharsets.UTF_8)))
                    .wireTap(kafkaConfig.createCamelProducerConfig(kafkaConfig.getTrInSubscriptionDataTopic()));
        } else {
            from(SEND_TR_IN_SUBSCRIPTION_DATA_TO_KAFKA)
                    .log(LoggingLevel.WARN, "Sending TR in subscription data to KAFKA is disabled")
                    .end();
        }
        if (kafkaConfig.isKafkaEnabled()) {
            from(SEND_TH_TR_CONSISTENCY_REPORT_TO_KAFKA)
                    .log(LoggingLevel.INFO, "Sending TH TR consistency report to KAFKA")
                    .marshal()
                    .json()
                    .removeHeaders("*")
                    .setHeader(KafkaHeaders.ENV_HEADER, constant(config.getEnvironment().getBytes(StandardCharsets.UTF_8)))
                    .setHeader(KafkaHeaders.CLIENT_HEADER, constant(config.getClientName().getBytes(StandardCharsets.UTF_8)))
                    .wireTap(kafkaConfig.createCamelProducerConfig(kafkaConfig.getThTrConsistencyTopic()));
        } else {
            from(SEND_TH_TR_CONSISTENCY_REPORT_TO_KAFKA)
                    .log(LoggingLevel.WARN, "Sending messages to KAFKA is disabled")
                    .end();
        }

    }
}
