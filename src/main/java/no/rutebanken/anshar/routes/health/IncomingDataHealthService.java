package no.rutebanken.anshar.routes.health;

import no.rutebanken.anshar.api.FlowStatus;
import no.rutebanken.anshar.api.GtfsRTApi;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionMonitoring;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

import static no.rutebanken.anshar.routes.kafka.KafkaRouteBuilder.SEND_TR_IN_SUBSCRIPTION_MONITORING_TO_KAFKA;

@Service
public class IncomingDataHealthService {

    private static final Logger log = LoggerFactory.getLogger(IncomingDataHealthService.class);

    Map<IncomingFlowParameters, DailyStatus> dailyStatuses = new HashMap<>();

    @Produce(SEND_TR_IN_SUBSCRIPTION_MONITORING_TO_KAFKA)
    private ProducerTemplate template;

    public void sendSubscriptionMonitoringData(String type, String datasetId, String httpStatus, String producerUrl) {
        SubscriptionMonitoring sm = new SubscriptionMonitoring();
        sm.setDataset(datasetId);
        sm.setDataType(type);
        sm.setHttpStatus(httpStatus);
        sm.setProducerUrl(producerUrl);
        sm.setSiriDataType(null);
        template.asyncSendBody(template.getDefaultEndpoint(), sm);
    }

    public void sendSubscriptionMonitoringData(String type, String datasetId, String httpStatus, String producerUrl, SiriDataType siriDataType) {
        SubscriptionMonitoring sm = new SubscriptionMonitoring();
        sm.setDataset(datasetId);
        sm.setDataType(type);
        sm.setHttpStatus(httpStatus);
        sm.setProducerUrl(producerUrl);
        sm.setSiriDataType(siriDataType);
        template.asyncSendBody(template.getDefaultEndpoint(), sm);
    }

    public void recordStatus(GtfsRTApi gtfsrtApi) {
        IncomingFlowParameters incomingFlowParameters = new IncomingFlowParameters();
        String id = gtfsrtApi.getId() != null ? gtfsrtApi.getId().toString() : String.format("%s-%s-%s", gtfsrtApi.getDatasetId(), gtfsrtApi.getType(), gtfsrtApi.getRouteIdList());
        incomingFlowParameters.setId(id);
        incomingFlowParameters.setDataset(gtfsrtApi.getDatasetId());
        incomingFlowParameters.setUrl(gtfsrtApi.getUrl());
        incomingFlowParameters.setType(IncomingFlowType.GTFS);
        recordStatus(incomingFlowParameters, gtfsrtApi.getStatus());
    }

    public void recordStatus(IncomingFlowStatus currentStatus) {
        recordStatus(currentStatus.getId(), currentStatus.getDataset(), currentStatus.getUrl(), IncomingFlowType.SIRI, FlowStatus.valueOf(currentStatus.getStatus()));
    }

    public void recordStatus(String id, String dataset, String url, IncomingFlowType type, FlowStatus status) {
        IncomingFlowParameters incomingFlowParameters = new IncomingFlowParameters();
        incomingFlowParameters.setId(id);
        incomingFlowParameters.setDataset(dataset);
        incomingFlowParameters.setUrl(url);
        incomingFlowParameters.setType(type);
        recordStatus(incomingFlowParameters, status);
    }

    public void recordStatus(IncomingFlowParameters flowParameters, FlowStatus status) {

        DailyStatus currentColor = convertStatusToColor(status);
        if (!dailyStatuses.containsKey(flowParameters) || !DailyStatus.GREEN.equals(currentColor) || !DailyStatus.YELLOW.equals(currentColor)) {
            // In case of error, daily status must be red
            dailyStatuses.put(flowParameters, currentColor);
        } else {
            DailyStatus previousDailyStatus = dailyStatuses.get(flowParameters);
            if (previousDailyStatus == DailyStatus.RED) {
                // if status was green and the last check is in error => it became orange
                // if status was red and the last check is ok => it became orange
                dailyStatuses.put(flowParameters, DailyStatus.ORANGE);
            }
        }
    }

    private DailyStatus convertStatusToColor(FlowStatus flowStatus) {
        if (FlowStatus.OK.equals(flowStatus)) {
            return DailyStatus.GREEN;
        } else if (FlowStatus.EMPTY_FEED.equals(flowStatus)) {
            return DailyStatus.YELLOW;
        }
        return DailyStatus.RED;
    }

    public Map<IncomingFlowParameters, DailyStatus> getDailyStatuses() {
        return dailyStatuses;
    }

    @Scheduled(cron = "0 0 4 * * *")
    public void clearDailyStatuses() {
        log.info("Clearing daily statuses");
        dailyStatuses.clear();
    }


}
