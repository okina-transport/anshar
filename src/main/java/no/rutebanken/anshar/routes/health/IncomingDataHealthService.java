package no.rutebanken.anshar.routes.health;

import no.rutebanken.anshar.api.FlowStatus;
import no.rutebanken.anshar.api.GtfsRTApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class IncomingDataHealthService {

    private static final Logger log = LoggerFactory.getLogger(IncomingDataHealthService.class);

    Map<IncomingFlowParameters, DailyStatus> dailyStatuses = new HashMap<>();


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
        if (!dailyStatuses.containsKey(flowParameters) || !FlowStatus.OK.equals(status)) {
            // In case of error, daily status must be green
            DailyStatus dailyStatus = FlowStatus.OK.equals(status) ? DailyStatus.GREEN : DailyStatus.RED;
            dailyStatuses.put(flowParameters, dailyStatus);
        } else {
            DailyStatus previousDailyStatus = dailyStatuses.get(flowParameters);
            if (previousDailyStatus == DailyStatus.RED) {
                // if status was green and the last check is in error => it became orange
                // if status was red and the last check is ok => it became orange
                dailyStatuses.put(flowParameters, DailyStatus.ORANGE);
            }
        }
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
