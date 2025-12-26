package no.rutebanken.anshar.routes.kafka;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


@Component
@Getter
@Slf4j
public class KafkaConfig {

    private final boolean kafkaEnabled;
    private final boolean sendSiriSxToKafka;
    private final boolean sendSiriSmToKafka;
    private final boolean sendTrInSubscriptionDataToKafka;
    private final String brokers;
    private final String clientId;
    private final String groupId;
    private final String sxTopic;
    private final String smTopic;
    private final String thTrConsistencyTopic;
    private final String trInSubscriptionDataTopic;
    private final String trInSubscriptionMonitoringTopic;

    public KafkaConfig(@Value("${anshar.kafka.enabled:false}") boolean kafkaEnabled,
                       @Value("${anshar.send.siri.to.kafka:false}") boolean sendSiriToKafka,
                       @Value("${anshar.send.tr.in.subscription.data.to.kafka:false}") boolean sendTrInSubscriptionDataToKafka,
                       @Value("${anshar.kafka.brokers:}") String brokers,
                       @Value("${anshar.kafka.clientId:}") String clientId,
                       @Value("${anshar.kafka.groupId:}") String groupId,
                       @Value("${anshar.kafka.topic.sx:}") String sxTopic,
                       @Value("${anshar.kafka.topic.sm:}") String smTopic,
                       @Value("${anshar.kafka.topic.th.tr.consistency:th_tr_consistency}") String thTrConsistencyTopic,
                       @Value("${anshar.kafka.topic.tr.in.subscription.data:tr_in_subscription_data}") String trInSubscriptionDataTopic,
                       @Value("${anshar.kafka.topic.tr.in.subscription.monitoring:tr_in_subscription_monitoring}") String trInSubscriptionMonitoringTopic) {
        this.kafkaEnabled = kafkaEnabled;
        this.sendSiriSxToKafka = sendSiriToKafka && StringUtils.isNotBlank(sxTopic);
        this.sendSiriSmToKafka = sendSiriToKafka && StringUtils.isNotBlank(smTopic);
        this.sendTrInSubscriptionDataToKafka = sendTrInSubscriptionDataToKafka;
        this.brokers = brokers;
        this.clientId = clientId;
        this.groupId = groupId;
        this.sxTopic = sxTopic;
        this.smTopic = smTopic;
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
