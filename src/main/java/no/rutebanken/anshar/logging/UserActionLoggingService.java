package no.rutebanken.anshar.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.rutebanken.anshar.routes.outbound.OutboundSubscriptionSetup;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserActionLoggingService {

    private static final Logger log = LoggerFactory.getLogger(UserActionLoggingService.class);

    static final String SERVICE_NAME = "ANSHAR";

    @Value("${anshar.logging.queue:activemq:queue:logging.service}")
    private String loggingQueue;

    private final ProducerTemplate producerTemplate;
    private final ObjectMapper objectMapper;

    public UserActionLoggingService(ProducerTemplate producerTemplate, ObjectMapper objectMapper) {
        this.producerTemplate = producerTemplate;
        this.objectMapper = objectMapper;
    }

    public void logOutboundUnsubscribeByType(List<OutboundSubscriptionSetup> targetedSubscriptions,
                                             String user, ActionOutcome outcome) {
        sendLog(targetedSubscriptions,
                extractSubscriptionIds(targetedSubscriptions), extractDatasets(targetedSubscriptions),
                user, ActionType.OUTBOUND_SUBSCRIPTION_DELETE_ALL, outcome);
    }

    public void logOutboundUnsubscribeByTypeAndRequestor(List<OutboundSubscriptionSetup> targetedSubscriptions,
                                                         String user, ActionOutcome outcome) {
        sendLog(targetedSubscriptions,
                extractSubscriptionIds(targetedSubscriptions), extractDatasets(targetedSubscriptions),
                user, ActionType.OUTBOUND_SUBSCRIPTION_DELETE_BY_REQUESTOR, outcome);
    }

    public void logCacheClear(String datasetId, String user, ActionOutcome outcome) {
        sendLog(null, null, datasetId, user, ActionType.CACHE_CLEAR, outcome);
    }

    public void logOutboundTerminate(String subscriptionId, OutboundSubscriptionSetup setup,
                                     String user, ActionOutcome outcome) {
        String datasets = setup != null ? String.join(", ", setup.getDatasetList()) : "";
        sendLog(setup, subscriptionId, datasets, user, ActionType.OUTBOUND_SUBSCRIPTION_TERMINATE, outcome);
    }

    private String extractSubscriptionIds(List<OutboundSubscriptionSetup> subscriptions) {
        return subscriptions.stream()
                .map(OutboundSubscriptionSetup::getSubscriptionId)
                .collect(Collectors.joining(", "));
    }

    private String extractDatasets(List<OutboundSubscriptionSetup> subscriptions) {
        return subscriptions.stream()
                .flatMap(s -> s.getDatasetList().stream())
                .distinct()
                .collect(Collectors.joining(", "));
    }

    public String extractUser(Exchange exchange) {
        String userName = exchange.getIn().getHeader("X-User-Name", String.class);
        return (userName != null && !userName.isBlank()) ? userName : "unknown";
    }

    private void sendLog(Object before, String objectId, String organization,
                         String user, ActionType actionType, ActionOutcome outcome) {
        try {
            LogContentDto content = new LogContentDto(
                    outcome.getErrorMessage(),
                    objectMapper.writeValueAsString(before),
                    null
            );

            LogEntryDto entry = new LogEntryDto();
            entry.setEventTimestamp(Instant.now());
            entry.setActionType(actionType.getValue());
            entry.setUser(user);
            entry.setObjectId(objectId);
            entry.setOrganization(organization);
            entry.setService(SERVICE_NAME);
            entry.setLogContent(content);

            producerTemplate.sendBody(loggingQueue, objectMapper.writeValueAsString(entry));
        } catch (JsonProcessingException e) {
            log.error("Failed to send {} log to logging-service", actionType, e);
        }
    }
}
