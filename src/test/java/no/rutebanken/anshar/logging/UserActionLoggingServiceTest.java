package no.rutebanken.anshar.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import no.rutebanken.anshar.routes.outbound.OutboundSubscriptionSetup;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.entur.siri.validator.SiriValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserActionLoggingServiceTest {

    private static final String LOGGING_QUEUE = "activemq:queue:logging.service";
    private static final String USER = "alice";

    private ProducerTemplate producerTemplate;
    private ObjectMapper objectMapper;
    private UserActionLoggingService loggingService;

    @BeforeEach
    void setUp() {
        producerTemplate = mock(ProducerTemplate.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        loggingService = new UserActionLoggingService(producerTemplate, objectMapper);
        ReflectionTestUtils.setField(loggingService, "loggingQueue", LOGGING_QUEUE);
    }

    private LogEntryDto captureLogEntry() throws Exception {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(producerTemplate).sendBody(eq(LOGGING_QUEUE), captor.capture());
        return objectMapper.readValue(captor.getValue(), LogEntryDto.class);
    }

    @Test
    void logOutboundUnsubscribeByType_shouldSendSubscriptionIdsAndDatasets_whenOutcomeSucceeds() throws Exception {
        List<OutboundSubscriptionSetup> targeted = List.of(
                buildSubscription("sub-1", "dataset-1"),
                buildSubscription("sub-2", "dataset-2")
        );

        loggingService.logOutboundUnsubscribeByType(targeted, USER, ActionOutcome.success());

        LogEntryDto entry = captureLogEntry();
        assertThat(entry.getActionType()).isEqualTo(ActionType.OUTBOUND_SUBSCRIPTION_DELETE_ALL.getValue());
        assertThat(entry.getUser()).isEqualTo(USER);
        assertThat(entry.getObjectId()).isEqualTo("sub-1, sub-2");
        assertThat(entry.getOrganization()).isEqualTo("dataset-1, dataset-2");
        assertThat(entry.getService()).isEqualTo("ANSHAR");
        assertThat(entry.getEventTimestamp()).isNotNull();
        assertThat(entry.getLogContent().getMetadata()).isNull();
        assertThat(entry.getLogContent().getObjectBefore())
                .contains("\"subscriptionId\":\"sub-1\"", "\"subscriptionId\":\"sub-2\"");
        assertThat(entry.getLogContent().getObjectAfter()).isNull();
    }

    @Test
    void logOutboundUnsubscribeByType_shouldSetMetadataWithErrorMessage_whenOutcomeIsFailure() throws Exception {
        List<OutboundSubscriptionSetup> targeted = List.of(buildSubscription("sub-1", "dataset-1"));

        loggingService.logOutboundUnsubscribeByType(targeted, USER, ActionOutcome.failure(new IllegalStateException("boom")));

        LogEntryDto entry = captureLogEntry();
        assertThat(entry.getLogContent().getMetadata()).isEqualTo("boom");
    }

    @Test
    void logOutboundUnsubscribeByTypeAndRequestor_shouldSendSubscriptionIdsAndDistinctDatasets() throws Exception {
        List<OutboundSubscriptionSetup> targeted = List.of(
                buildSubscription("sub-1", "dataset-1"),
                buildSubscription("sub-2", "dataset-1")
        );

        loggingService.logOutboundUnsubscribeByTypeAndRequestor(targeted, USER, ActionOutcome.success());

        LogEntryDto entry = captureLogEntry();
        assertThat(entry.getActionType()).isEqualTo(ActionType.OUTBOUND_SUBSCRIPTION_DELETE_BY_REQUESTOR.getValue());
        assertThat(entry.getObjectId()).isEqualTo("sub-1, sub-2");
        assertThat(entry.getOrganization()).isEqualTo("dataset-1");
        assertThat(entry.getLogContent().getMetadata()).isNull();
    }

    @Test
    void logCacheClear_shouldSendDatasetAsOrganizationWithNullObjectId() throws Exception {
        loggingService.logCacheClear("dataset-1", USER, ActionOutcome.success());

        LogEntryDto entry = captureLogEntry();
        assertThat(entry.getActionType()).isEqualTo(ActionType.CACHE_CLEAR.getValue());
        assertThat(entry.getUser()).isEqualTo(USER);
        assertThat(entry.getObjectId()).isNull();
        assertThat(entry.getOrganization()).isEqualTo("dataset-1");
        assertThat(entry.getLogContent().getObjectBefore()).isEqualTo("null");
        assertThat(entry.getLogContent().getObjectAfter()).isNull();
        assertThat(entry.getLogContent().getMetadata()).isNull();
    }

    @Test
    void logCacheClear_shouldSetMetadataWithErrorMessage_whenOutcomeIsFailure() throws Exception {
        loggingService.logCacheClear("dataset-1", USER, ActionOutcome.failure(new IllegalStateException("cache boom")));

        LogEntryDto entry = captureLogEntry();
        assertThat(entry.getLogContent().getMetadata()).isEqualTo("cache boom");
    }

    @Test
    void logOutboundTerminate_shouldSendSubscriptionIdAndJoinedDatasets() throws Exception {
        OutboundSubscriptionSetup setup = buildSubscription("sub-1", "dataset-1,dataset-2");

        loggingService.logOutboundTerminate("sub-1", setup, USER, ActionOutcome.success());

        LogEntryDto entry = captureLogEntry();
        assertThat(entry.getActionType()).isEqualTo(ActionType.OUTBOUND_SUBSCRIPTION_TERMINATE.getValue());
        assertThat(entry.getObjectId()).isEqualTo("sub-1");
        assertThat(entry.getOrganization()).isEqualTo("dataset-1, dataset-2");
        assertThat(entry.getLogContent().getObjectBefore()).contains("\"subscriptionId\":\"sub-1\"");
    }

    @Test
    void logOutboundTerminate_shouldSendEmptyOrganization_whenSetupIsNull() throws Exception {
        loggingService.logOutboundTerminate("sub-1", null, USER, ActionOutcome.success());

        LogEntryDto entry = captureLogEntry();
        assertThat(entry.getObjectId()).isEqualTo("sub-1");
        assertThat(entry.getOrganization()).isEmpty();
        assertThat(entry.getLogContent().getObjectBefore()).isEqualTo("null");
    }

    @Test
    void extractUser_shouldReturnHeaderValue_whenPresent() {
        Exchange exchange = mock(Exchange.class);
        Message message = mock(Message.class);
        when(exchange.getIn()).thenReturn(message);
        when(message.getHeader(eq("X-User-Name"), eq(String.class))).thenReturn(USER);

        assertThat(loggingService.extractUser(exchange)).isEqualTo(USER);
    }

    @Test
    void extractUser_shouldReturnUnknown_whenHeaderIsBlankOrMissing() {
        Exchange exchange = mock(Exchange.class);
        Message message = mock(Message.class);
        when(exchange.getIn()).thenReturn(message);
        when(message.getHeader(eq("X-User-Name"), eq(String.class))).thenReturn("  ");

        assertThat(loggingService.extractUser(exchange)).isEqualTo("unknown");
    }

    @Test
    void logCacheClear_shouldNotThrowAndShouldSkipSending_whenJsonSerializationFails() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("serialization failed") {
        });
        UserActionLoggingService serviceWithFailingMapper = new UserActionLoggingService(producerTemplate, failingMapper);
        ReflectionTestUtils.setField(serviceWithFailingMapper, "loggingQueue", LOGGING_QUEUE);

        assertThatCode(() -> serviceWithFailingMapper.logCacheClear("dataset-1", USER, ActionOutcome.success()))
                .doesNotThrowAnyException();

        verifyNoInteractions(producerTemplate);
    }

    private static OutboundSubscriptionSetup buildSubscription(String subscriptionId, String datasetId) {
        return new OutboundSubscriptionSetup(
                ZonedDateTime.of(2025, 1, 1, 1, 1, 1, 0, ZoneId.systemDefault()),
                SiriDataType.ESTIMATED_TIMETABLE,
                "http://example.com/address",
                30L,
                false,
                3000L,
                1000L,
                Collections.emptyMap(),
                Collections.emptyList(),
                subscriptionId,
                "requestorRef",
                ZonedDateTime.of(2025, 1, 1, 1, 1, 1, 0, ZoneId.systemDefault()),
                datasetId,
                "clientName",
                false,
                SiriValidator.Version.VERSION_2_0
        );
    }
}
