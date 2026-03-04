package no.rutebanken.anshar.routes.kafka;

import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.data.util.CustomSiriXml;
import no.rutebanken.anshar.routes.dataformat.SiriDataFormatHelper;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import uk.org.siri.siri21.Siri;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static no.rutebanken.anshar.routes.validation.validators.Constants.DATASET_ID_HEADER_NAME;

@Component
public class KafkaRouteBuilder extends RouteBuilder {

    public static final String SEND_SM_IN_TO_KAFKA = "direct:send.sm.in.to.kafka";
    public static final String SEND_GM_IN_TO_KAFKA = "direct:send.gm.in.to.kafka";
    public static final String SEND_SX_OUT_TO_KAFKA = "direct:send.sx.out.to.kafka";
    public static final String SEND_SM_OUT_TO_KAFKA = "direct:send.sm.out.to.kafka";
    public static final String SEND_GM_OUT_TO_KAFKA = "direct:send.gm.out.to.kafka";
    public static final String SEND_SX_IN_TO_KAFKA = "direct:send.sx.in.to.kafka";
    public static final String SEND_TH_TR_CONSISTENCY_REPORT_TO_KAFKA = "direct:send.th.tr.consistency.report.to.kafka";
    public static final String SEND_TR_IN_SUBSCRIPTION_DATA_TO_KAFKA = "direct:send.tr.in.subscription.data.to.kafka";
    public static final String SEND_TR_IN_SUBSCRIPTION_MONITORING_TO_KAFKA = "direct:send.tr.in.subscription.monitoring.to.kafka";

    private final KafkaConfig kafkaConfig;
    private final AnsharConfiguration config;
    private final OperatorService operatorService;
    private final KeepOnlyKafkaHeaders keepOnlyKafkaHeaders;

    public KafkaRouteBuilder(KafkaConfig kafkaConfig, AnsharConfiguration config, OperatorService operatorService, KeepOnlyKafkaHeaders keepOnlyKafkaHeaders) {
        this.kafkaConfig = kafkaConfig;
        this.config = config;
        this.operatorService = operatorService;
        this.keepOnlyKafkaHeaders = keepOnlyKafkaHeaders;
    }

    @Override
    public void configure() throws Exception {
        if (kafkaConfig.isKafkaEnabled() && kafkaConfig.isSendSiriSmInToKafka()) {
            from(SEND_SM_IN_TO_KAFKA)
                    .marshal(SiriDataFormatHelper.getThreadSafeSiriJaxbDataformat())
                    .process(keepOnlyKafkaHeaders)
                    .wireTap(kafkaConfig.createCamelProducerConfig(kafkaConfig.getSmInTopic()));
        } else {
            from(SEND_SM_IN_TO_KAFKA)
                    .to("stub:nowhere") // does nothing but required otherwise camel crash @ start-up
                    .end();
        }

        if (kafkaConfig.isKafkaEnabled() && kafkaConfig.isSendSiriSxInToKafka()) {
            from(SEND_SX_IN_TO_KAFKA)
                    .marshal(SiriDataFormatHelper.getThreadSafeSiriJaxbDataformat())
                    .process(keepOnlyKafkaHeaders)
                    .wireTap(kafkaConfig.createCamelProducerConfig(kafkaConfig.getSxInTopic()));
        } else {
            from(SEND_SX_IN_TO_KAFKA)
                    .to("stub:nowhere") // does nothing but required otherwise camel crash @ start-up
                    .end();
        }

        if (kafkaConfig.isKafkaEnabled() && kafkaConfig.isSendSiriGmInToKafka()) {
            from(SEND_GM_IN_TO_KAFKA)
                    .process(e -> {
                        Siri siri = e.getIn().getBody(Siri.class);
                        String stringMsg = CustomSiriXml.toXml(siri);
                        e.getIn().setBody(stringMsg);
                    })
                    .process(keepOnlyKafkaHeaders)
                    .wireTap(kafkaConfig.createCamelProducerConfig(kafkaConfig.getGmInTopic()));
        } else {
            from(SEND_GM_IN_TO_KAFKA)
                    .to("stub:nowhere") // does nothing but required otherwise camel crash @ start-up
                    .end();
        }

        if (kafkaConfig.isKafkaEnabled() && kafkaConfig.isSendSiriSxOutToKafka()) {
            from(SEND_SX_OUT_TO_KAFKA)
                    .log(LoggingLevel.INFO, "Sending SX message to KAFKA")
                    .process(keepOnlyKafkaHeaders)
                    .process(e -> {
                        Set<String> operators = operatorService.getSxOperators(e.getIn().getBody(Siri.class));
                        byte[] operatorBytes = StringUtils.join(operators, ',').getBytes(StandardCharsets.UTF_8);
                        e.getMessage().setHeader(KafkaHeaders.OPERATORS_HEADER, operatorBytes);
                    })
                    .marshal(SiriDataFormatHelper.getThreadSafeSiriJaxbDataformat())
                    .wireTap(kafkaConfig.createCamelProducerConfig(kafkaConfig.getSxOutTopic()));
        } else {
            from(SEND_SX_OUT_TO_KAFKA)
                    .log(LoggingLevel.WARN, "Sending SIRI SX messages to KAFKA is disabled")
                    .end();
        }
        if (kafkaConfig.isKafkaEnabled() && kafkaConfig.isSendSiriSmOutToKafka()) {
            from(SEND_SM_OUT_TO_KAFKA)
                    .marshal(SiriDataFormatHelper.getThreadSafeSiriJaxbDataformat())
                    .process(keepOnlyKafkaHeaders)
                    .wireTap(kafkaConfig.createCamelProducerConfig(kafkaConfig.getSmOutTopic()));
        } else {
            from(SEND_SM_OUT_TO_KAFKA)
                    .to("stub:nowhere") // does nothing but required otherwise camel crash @ start-up
                    .end();
        }

        if (kafkaConfig.isKafkaEnabled() && kafkaConfig.isSendSiriGmOutToKafka()) {
            from(SEND_GM_OUT_TO_KAFKA)
                    .process(e -> {
                        Siri siri = e.getIn().getBody(Siri.class);
                        String stringMsg = CustomSiriXml.toXml(siri);
                        e.getIn().setBody(stringMsg);
                    })
                    .process(keepOnlyKafkaHeaders)
                    .wireTap(kafkaConfig.createCamelProducerConfig(kafkaConfig.getGmOutTopic()));
        } else {
            from(SEND_GM_OUT_TO_KAFKA)
                    .to("stub:nowhere") // does nothing but required otherwise camel crash @ start-up
                    .end();
        }

        if (kafkaConfig.isKafkaEnabled() && kafkaConfig.isSendTrInSubscriptionDataToKafka()) {
            from(SEND_TR_IN_SUBSCRIPTION_DATA_TO_KAFKA)
                    .log(LoggingLevel.INFO, "Sending TR in subscription data to KAFKA")
                    .marshal()
                    .json()
                    .process(keepOnlyKafkaHeaders)
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
                    .process(keepOnlyKafkaHeaders)
                    .wireTap(kafkaConfig.createCamelProducerConfig(kafkaConfig.getThTrConsistencyTopic()));
            from(SEND_TR_IN_SUBSCRIPTION_MONITORING_TO_KAFKA)
                    .log(LoggingLevel.INFO, "Sending TR in subscription monitoring to KAFKA")
                    .marshal()
                    .json()
                    .process(keepOnlyKafkaHeaders)
                    .wireTap(kafkaConfig.createCamelProducerConfig(kafkaConfig.getTrInSubscriptionMonitoringTopic()));
        } else {
            from(SEND_TH_TR_CONSISTENCY_REPORT_TO_KAFKA)
                    .log(LoggingLevel.WARN, "Sending messages to KAFKA is disabled")
                    .end();
            from(SEND_TR_IN_SUBSCRIPTION_MONITORING_TO_KAFKA)
                    .log(LoggingLevel.WARN, "Sending messages to KAFKA is disabled")
                    .end();
        }

    }
}
