package no.rutebanken.anshar.routes.kafka;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


@Component
@Getter
@Slf4j
public class KafkaConfig {

    private final boolean kafkaEnabled;
    private final boolean sendSiriToKafka;
    private final boolean sendTrInSubscriptionDataToKafka;
    private final String brokers;
    private final String clientId;
    private final String groupId;
    private final String sxTopic;
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
                       @Value("${anshar.kafka.topic.th.tr.consistency:th_tr_consistency}") String thTrConsistencyTopic,
                       @Value("${anshar.kafka.topic.tr.in.subscription.data:tr_in_subscription_data}") String trInSubscriptionDataTopic,
                       @Value("${anshar.kafka.topic.tr.in.subscription.monitoring:tr_in_subscription_monitoring}") String trInSubscriptionMonitoringTopic) {
        this.kafkaEnabled = kafkaEnabled;
        this.sendSiriToKafka = sendSiriToKafka;
        this.sendTrInSubscriptionDataToKafka = sendTrInSubscriptionDataToKafka;
        this.brokers = brokers;
        this.clientId = clientId;
        this.groupId = groupId;
        this.sxTopic = sxTopic;
        this.thTrConsistencyTopic = thTrConsistencyTopic;
        this.trInSubscriptionDataTopic = trInSubscriptionDataTopic;
        this.trInSubscriptionMonitoringTopic = trInSubscriptionMonitoringTopic;
    }

    public String createCamelConsumerConfig(String topicName) {
        String config = "kafka:" + topicName;
        config += "?brokers=" + brokers;
        config += "&clientId=" + clientId;
        config += "&groupId=" + groupId;
        log.info("KAFKA consumer config: {}", config);
        return config;
    }

    public String createCamelProducerConfig(String topicName) {
        String config = "kafka:" + topicName;
        config += "?brokers=" + brokers;
        config += "&clientId=" + clientId;
        log.info("KAFKA producer config: {}", config);
        return config;
    }

}
