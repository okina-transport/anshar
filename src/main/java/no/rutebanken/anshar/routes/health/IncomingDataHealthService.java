package no.rutebanken.anshar.routes.health;

import no.rutebanken.anshar.api.FlowStatus;
import no.rutebanken.anshar.api.GtfsRTApi;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionMonitoring;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

import static no.rutebanken.anshar.routes.kafka.KafkaRouteBuilder.SEND_TR_IN_SUBSCRIPTION_MONITORING_TO_KAFKA;

@EnableScheduling
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
        DailyStatus currentStatus = dailyStatuses.get(flowParameters);
        DailyStatus newStatus = convertStatusToColor(status);
        DailyStatus realStatus = getRealStatus(currentStatus, newStatus);
        dailyStatuses.put(flowParameters, realStatus);
    }

    private DailyStatus convertStatusToColor(FlowStatus flowStatus) {
        if (FlowStatus.OK.equals(flowStatus)) {
            return DailyStatus.GREEN;
        } else if (FlowStatus.EMPTY_FEED.equals(flowStatus)) {
            return DailyStatus.YELLOW;
        }
        return DailyStatus.RED;
    }

    private DailyStatus getRealStatus(DailyStatus currentStatus, DailyStatus newStatus) {
        if (newStatus == DailyStatus.RED) {
            return DailyStatus.RED;
        }
        if (newStatus == DailyStatus.ORANGE) {
            return DailyStatus.ORANGE;
        }
        if (currentStatus == DailyStatus.RED || currentStatus == DailyStatus.ORANGE) {
            // at least one fail this day
            return DailyStatus.ORANGE;
        }
        return newStatus;
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
