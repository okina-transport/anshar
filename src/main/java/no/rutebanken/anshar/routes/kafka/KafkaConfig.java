package no.rutebanken.anshar.routes.kafka;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


@Component
@Getter
@Slf4j
public class KafkaConfig {

    private final boolean kafkaEnabled;
    private final boolean sendSiriSmInToKafka;
    private final boolean sendSiriSxInToKafka;
    private final boolean sendSiriSxOutToKafka;
    private final boolean sendSiriSmOutToKafka;
    private final boolean sendTrInSubscriptionDataToKafka;
    private final String brokers;
    private final String clientId;
    private final String groupId;
    private final String smInTopic;
    private final String sxInTopic;
    private final String sxOutTopic;
    private final String smOutTopic;
    private final String thTrConsistencyTopic;
    private final String trInSubscriptionDataTopic;
    private final String trInSubscriptionMonitoringTopic;

    public KafkaConfig(@Value("${anshar.kafka.enabled:false}") boolean kafkaEnabled,
                       @Value("${anshar.send.siri.to.kafka:false}") boolean sendSiriToKafka,
                       @Value("${anshar.send.tr.in.subscription.data.to.kafka:false}") boolean sendTrInSubscriptionDataToKafka,
                       @Value("${anshar.kafka.brokers:}") String brokers,
                       @Value("${anshar.kafka.clientId:}") String clientId,
                       @Value("${anshar.kafka.groupId:}") String groupId,
                       @Value("${anshar.kafka.topic.in.sm:}") String smInTopic,
                       @Value("${anshar.kafka.topic.out.sm:}") String smOutTopic,
                       @Value("${anshar.kafka.topic.in.sx:}") String sxInTopic,
                       @Value("${anshar.kafka.topic.out.sx:}") String sxOutTopic,
                       @Value("${anshar.kafka.topic.th.tr.consistency:th_tr_consistency}") String thTrConsistencyTopic,
                       @Value("${anshar.kafka.topic.tr.in.subscription.data:tr_in_subscription_data}") String trInSubscriptionDataTopic,
                       @Value("${anshar.kafka.topic.tr.in.subscription.monitoring:tr_in_subscription_monitoring}") String trInSubscriptionMonitoringTopic) {
        this.kafkaEnabled = kafkaEnabled;
        this.sxInTopic = sxInTopic;
        this.sendSiriSmInToKafka = sendSiriToKafka && StringUtils.isNotBlank(smInTopic);
        this.sendSiriSxOutToKafka = sendSiriToKafka && StringUtils.isNotBlank(sxOutTopic);
        this.sendSiriSmOutToKafka = sendSiriToKafka && StringUtils.isNotBlank(smOutTopic);
        this.sendSiriSxInToKafka = sendSiriToKafka && StringUtils.isNotBlank(sxInTopic);
        this.sendTrInSubscriptionDataToKafka = sendTrInSubscriptionDataToKafka;
        this.brokers = brokers;
        this.clientId = clientId;
        this.groupId = groupId;
        this.smInTopic = smInTopic;
        this.sxOutTopic = sxOutTopic;
        this.smOutTopic = smOutTopic;
        this.thTrConsistencyTopic = thTrConsistencyTopic;
        this.trInSubscriptionDataTopic = trInSubscriptionDataTopic;
        this.trInSubscriptionMonitoringTopic = trInSubscriptionMonitoringTopic;
    }

    public String createCamelConsumerConfig(String topicName) {
        String config = "kafka:" + topicName;
        config += "?brokers=" + brokers;
        config += "&clientId=" + clientId;
        config += "&groupId=" + groupId;
        return config;
    }

    public String createCamelProducerConfig(String topicName) {
        String config = "kafka:" + topicName;
        config += "?brokers=" + brokers;
        config += "&clientId=" + clientId;
        return config;
    }

}
