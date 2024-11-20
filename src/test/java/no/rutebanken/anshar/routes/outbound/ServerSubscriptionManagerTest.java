package no.rutebanken.anshar.routes.outbound;

import com.hazelcast.map.IMap;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.entur.siri.validator.SiriValidator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.org.siri.siri21.Siri;

import java.io.FileInputStream;
import java.io.InputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ServerSubscriptionManagerTest {

    public static final String ADDRESS = "adress";
    public static final long HEARTBEAT_INTERVAL = 30L;
    public static final boolean INCREMENTAL_UPDATES = false;
    public static final long CHANGE_BEFORE_UPDATES = 3000L;
    public static final long UPDATE_INTERVAL = 1000L;
    public static final String REQUESTOR_REF = "requestorRef";
    public static final String DATA_SET_ID = "dataSetId";
    public static final String CLIENT_NAME = "clientName";
    @InjectMocks
    private ServerSubscriptionManager serverSubscriptionManager;

    @Mock
    private IMap<String, OutboundSubscriptionSetup> subscriptions;

    @Mock
    private SiriObjectFactory siriObjectFactory;

    @Test
    void getSubscriptionsCountAsJsonTest() {
        OutboundSubscriptionSetup subscription1 = new OutboundSubscriptionSetup(
                ZonedDateTime.now(),
                SiriDataType.STOP_MONITORING,
                ADDRESS,
                HEARTBEAT_INTERVAL,
                INCREMENTAL_UPDATES,
                CHANGE_BEFORE_UPDATES,
                UPDATE_INTERVAL,
                Collections.emptyMap(),
                Collections.emptyList(),
                "subscriptionId",
                "requestorRef",
                ZonedDateTime.of(2025, 1, 1, 1, 1, 1, 0, ZoneId.systemDefault()),
                "dataSetId",
                "clientName",
                false,
                SiriValidator.Version.VERSION_2_0
        );

        Map.Entry<String, OutboundSubscriptionSetup> entry = new AbstractMap.SimpleEntry<>(subscription1.getSubscriptionId(), subscription1);

        OutboundSubscriptionSetup subscription2 = new OutboundSubscriptionSetup(
                ZonedDateTime.now(),
                SiriDataType.SITUATION_EXCHANGE,
                ADDRESS,
                HEARTBEAT_INTERVAL,
                INCREMENTAL_UPDATES,
                CHANGE_BEFORE_UPDATES,
                UPDATE_INTERVAL,
                Collections.emptyMap(),
                Collections.emptyList(),
                "subscriptionId2",
                "requestorRef",
                ZonedDateTime.of(2025, 1, 1, 1, 1, 1, 0, ZoneId.systemDefault()),
                "dataSetId",
                "clientName",
                false,
                SiriValidator.Version.VERSION_2_0
        );
        Map.Entry<String, OutboundSubscriptionSetup> entry2 = new AbstractMap.SimpleEntry<>(subscription2.getSubscriptionId(), subscription2);
        Set<Map.Entry<String, OutboundSubscriptionSetup>> entrySet = Set.of(entry, entry2);
        Mockito.when(subscriptions.entrySet()).thenReturn(entrySet);

        JSONArray result = serverSubscriptionManager.getSubscriptionsCountAsJson();

        String expectedJsonObject = "{\"count\":1,\"siriDataType\":\"SITUATION_EXCHANGE\"}";
        String expectedJsonObject2 = "{\"count\":1,\"siriDataType\":\"STOP_MONITORING\"}";
        assertThat(result).isNotEmpty().hasSize(2);
        assertThat(result.get(0).toString()).hasToString(expectedJsonObject);
        assertThat(result.get(1).toString()).hasToString(expectedJsonObject2);
    }

    @Test
    void getSubscriptionsWithPaginationTest() {
        OutboundSubscriptionSetup subscription1 = new OutboundSubscriptionSetup(
                ZonedDateTime.now(),
                SiriDataType.STOP_MONITORING,
                ADDRESS,
                HEARTBEAT_INTERVAL,
                INCREMENTAL_UPDATES,
                CHANGE_BEFORE_UPDATES,
                UPDATE_INTERVAL,
                Collections.emptyMap(),
                Collections.emptyList(),
                "subscriptionId",
                REQUESTOR_REF,
                ZonedDateTime.of(2025, 1, 1, 1, 1, 1, 0, ZoneId.systemDefault()),
                DATA_SET_ID,
                CLIENT_NAME,
                false,
                SiriValidator.Version.VERSION_2_0
        );

        Map.Entry<String, OutboundSubscriptionSetup> entry = new AbstractMap.SimpleEntry<>(subscription1.getSubscriptionId(), subscription1);

        OutboundSubscriptionSetup subscription2 = new OutboundSubscriptionSetup(
                ZonedDateTime.of(2023, 1, 1, 1, 1, 1, 0, ZoneId.systemDefault()),
                SiriDataType.SITUATION_EXCHANGE,
                ADDRESS,
                HEARTBEAT_INTERVAL,
                INCREMENTAL_UPDATES,
                CHANGE_BEFORE_UPDATES,
                UPDATE_INTERVAL,
                Collections.emptyMap(),
                Collections.emptyList(),
                "subscriptionId2",
                REQUESTOR_REF,
                ZonedDateTime.of(2025, 1, 1, 1, 1, 1, 0, ZoneId.systemDefault()),
                DATA_SET_ID,
                CLIENT_NAME,
                false,
                SiriValidator.Version.VERSION_2_0
        );
        Map.Entry<String, OutboundSubscriptionSetup> entry2 = new AbstractMap.SimpleEntry<>(subscription2.getSubscriptionId(), subscription2);

        OutboundSubscriptionSetup subscription3 = new OutboundSubscriptionSetup(
                ZonedDateTime.of(2024, 1, 1, 1, 1, 1, 0, ZoneId.systemDefault()),
                SiriDataType.SITUATION_EXCHANGE,
                ADDRESS,
                HEARTBEAT_INTERVAL,
                INCREMENTAL_UPDATES,
                CHANGE_BEFORE_UPDATES,
                UPDATE_INTERVAL,
                Collections.emptyMap(),
                Collections.emptyList(),
                "subscriptionId3",
                REQUESTOR_REF,
                ZonedDateTime.of(2025, 1, 1, 1, 1, 1, 0, ZoneId.systemDefault()),
                DATA_SET_ID,
                CLIENT_NAME,
                false,
                SiriValidator.Version.VERSION_2_0
        );
        Map.Entry<String, OutboundSubscriptionSetup> entry3 = new AbstractMap.SimpleEntry<>(subscription3.getSubscriptionId(), subscription3);

        Set<Map.Entry<String, OutboundSubscriptionSetup>> entrySet = new LinkedHashSet<>();
        entrySet.add(entry);
        entrySet.add(entry2);
        entrySet.add(entry3);
        Mockito.when(subscriptions.entrySet()).thenReturn(entrySet);

        JSONObject result = serverSubscriptionManager.getSubscriptionsWithPagination(SiriDataType.SITUATION_EXCHANGE, 0, 1);

        assertThat(result).isNotEmpty()
                .containsEntry("count", 2);

        assertThat(result.get("data").toString()).hasToString( "[{\"heartbeatInterval\":\"0 s\",\"address\":\"adress\",\"subscriptionType\":\"SITUATION_EXCHANGE\",\"filteredRefs\":\"\",\"datasetId\":\"dataSetId\",\"subscriptionRef\":\"subscriptionId2\",\"requestReceived\":\"2023-01-01 01:01:01\",\"initialTerminationTime\":\"2025-01-01 01:01:01\",\"clientTrackingName\":\"clientName\"}]");
    }

    @Test
    void getSubscriptionsWithWrongPaginationTest() {
        OutboundSubscriptionSetup subscription1 = new OutboundSubscriptionSetup(
                ZonedDateTime.now(),
                SiriDataType.STOP_MONITORING,
                ADDRESS,
                HEARTBEAT_INTERVAL,
                INCREMENTAL_UPDATES,
                CHANGE_BEFORE_UPDATES,
                UPDATE_INTERVAL,
                Collections.emptyMap(),
                Collections.emptyList(),
                "subscriptionId",
                REQUESTOR_REF,
                ZonedDateTime.of(2025, 1, 1, 1, 1, 1, 0, ZoneId.systemDefault()),
                DATA_SET_ID,
                CLIENT_NAME,
                false,
                SiriValidator.Version.VERSION_2_0
        );

        Map.Entry<String, OutboundSubscriptionSetup> entry = new AbstractMap.SimpleEntry<>(subscription1.getSubscriptionId(), subscription1);

        OutboundSubscriptionSetup subscription2 = new OutboundSubscriptionSetup(
                ZonedDateTime.of(2023, 1, 1, 1, 1, 1, 0, ZoneId.systemDefault()),
                SiriDataType.SITUATION_EXCHANGE,
                ADDRESS,
                HEARTBEAT_INTERVAL,
                INCREMENTAL_UPDATES,
                CHANGE_BEFORE_UPDATES,
                UPDATE_INTERVAL,
                Collections.emptyMap(),
                Collections.emptyList(),
                "subscriptionId2",
                REQUESTOR_REF,
                ZonedDateTime.of(2025, 1, 1, 1, 1, 1, 0, ZoneId.systemDefault()),
                DATA_SET_ID,
                CLIENT_NAME,
                false,
                SiriValidator.Version.VERSION_2_0
        );
        Map.Entry<String, OutboundSubscriptionSetup> entry2 = new AbstractMap.SimpleEntry<>(subscription2.getSubscriptionId(), subscription2);


        Set<Map.Entry<String, OutboundSubscriptionSetup>> entrySet = new LinkedHashSet<>();
        entrySet.add(entry);
        entrySet.add(entry2);
        Mockito.when(subscriptions.entrySet()).thenReturn(entrySet);

        JSONObject result = serverSubscriptionManager.getSubscriptionsWithPagination(SiriDataType.SITUATION_EXCHANGE, 2, 5);

        assertThat(result).isNotEmpty()
                .containsEntry("count", 1);

        assertThat(result.get("data").toString()).hasToString( "[]");
    }

    @Test
    void handleSingleSubscriptionRequest_SM_missingMonitoringRef_shouldReturnErrorMessage_test() {
        Siri incomingSiri;
        try (InputStream inputStream = new FileInputStream("src/test/resources/siri-sm-missing-monitoring-ref.xml")) {
            incomingSiri = SiriValueTransformer.parseXml(inputStream);
        } catch (Exception e) {
           throw new AssertionError("Illegal state");
        }

        boolean useOriginalId = false;
        boolean soapTransformation = false;

        serverSubscriptionManager.handleSingleSubscriptionRequest(incomingSiri, DATA_SET_ID, null, CLIENT_NAME, soapTransformation, useOriginalId);

        Mockito.verify(siriObjectFactory).createSubscriptionResponse("OKINA", false, "Error", "2.0");
    }

}