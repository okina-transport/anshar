package no.rutebanken.anshar.routes.kafka;

import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.subscription.helpers.MappingAdapterPresets;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import uk.org.siri.siri21.LineRef;
import uk.org.siri.siri21.MonitoredStopVisitCancellation;
import uk.org.siri.siri21.MonitoringRefStructure;
import uk.org.siri.siri21.ServiceDelivery;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.StopMonitoringDeliveryStructure;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import static no.rutebanken.anshar.routes.validation.validators.Constants.DATASET_ID_HEADER_NAME;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Checks that IPP is applied on the Siri object before it is serialized for the SM OUT Kafka topic.
 */
class KafkaRouteBuilderIdProcessingTest {

    private static final String DATASET_ID = "TEST";
    private static final String SM_OUT_TOPIC = "sm-out";

    private CamelContext camelContext;
    private ApplicationContext previousApplicationContext;
    private ProducerTemplate producer;

    @BeforeEach
    void setUp() throws Exception {
        MappingAdapterPresets.clearCache();
        // Stop adapters look up StopPlaceUpdaterService through the static Spring context holder
        previousApplicationContext = ApplicationContextHolder.getContext();
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBean(StopPlaceUpdaterService.class)).thenReturn(mock(StopPlaceUpdaterService.class));
        new ApplicationContextHolder().setApplicationContext(applicationContext);

        KafkaConfig kafkaConfig = mock(KafkaConfig.class);
        when(kafkaConfig.isKafkaEnabled()).thenReturn(true);
        when(kafkaConfig.isSendSiriSmOutToKafka()).thenReturn(true);
        when(kafkaConfig.getSmOutTopic()).thenReturn(SM_OUT_TOPIC);
        when(kafkaConfig.createCamelProducerConfig(any())).thenAnswer(inv -> "mock:" + inv.getArgument(0));

        SubscriptionConfig subscriptionConfig = mock(SubscriptionConfig.class);
        when(subscriptionConfig.buildIdProcessingParamsFromDataset(DATASET_ID)).thenReturn(stopAndLineParams());

        KafkaRouteBuilder routeBuilder = new KafkaRouteBuilder(
                kafkaConfig,
                mock(OperatorService.class),
                new KeepOnlyKafkaHeaders(mock(AnsharConfiguration.class)),
                new IdProcessingProcessor(subscriptionConfig));

        camelContext = new DefaultCamelContext();
        camelContext.addRoutes(routeBuilder);
        camelContext.start();
        producer = camelContext.createProducerTemplate();
    }

    @AfterEach
    void tearDown() throws Exception {
        new ApplicationContextHolder().setApplicationContext(previousApplicationContext);
        MappingAdapterPresets.clearCache();
        camelContext.close();
    }

    @Test
    void smOut_cancellationsOnly_idsProcessedBeforeMarshalling() throws Exception {
        MockEndpoint kafka = camelContext.getEndpoint("mock:" + SM_OUT_TOPIC, MockEndpoint.class);
        kafka.expectedMessageCount(1);

        producer.sendBodyAndHeader(KafkaRouteBuilder.SEND_SM_OUT_TO_KAFKA,
                smSiriWithOnlyCancellation("STOP_IN_1", "LINE_IN_2"), DATASET_ID_HEADER_NAME, DATASET_ID);

        kafka.assertIsSatisfied();
        Object body = kafka.getExchanges().getFirst().getIn().getBody();
        assertFalse(body instanceof Siri, "Kafka payload must be serialized, not a Siri object");
        String xml = assertInstanceOf(String.class, kafka.getExchanges().getFirst().getIn().getBody(String.class));
        assertTrue(xml.contains("STOP_OUT_1"), xml);
        assertTrue(xml.contains("LINE_OUT_2"), xml);
        assertFalse(xml.contains("STOP_IN_1"), xml);
        assertFalse(xml.contains("LINE_IN_2"), xml);
    }

    private static Map<ObjectType, Optional<IdProcessingParameters>> stopAndLineParams() {
        Map<ObjectType, Optional<IdProcessingParameters>> map = new EnumMap<>(ObjectType.class);
        for (ObjectType type : ObjectType.values()) {
            map.put(type, Optional.empty());
        }
        map.put(ObjectType.STOP, Optional.of(params(ObjectType.STOP, "STOP_IN_", "STOP_OUT_")));
        map.put(ObjectType.LINE, Optional.of(params(ObjectType.LINE, "LINE_IN_", "LINE_OUT_")));
        return map;
    }

    private static IdProcessingParameters params(ObjectType type, String inputPrefixToRemove, String outputPrefixToAdd) {
        IdProcessingParameters params = new IdProcessingParameters();
        params.setDatasetId(DATASET_ID);
        params.setObjectType(type);
        params.setInputPrefixToRemove(inputPrefixToRemove);
        params.setOutputPrefixToAdd(outputPrefixToAdd);
        return params;
    }

    private static Siri smSiriWithOnlyCancellation(String monitoringRef, String lineRef) {
        MonitoringRefStructure monitoring = new MonitoringRefStructure();
        monitoring.setValue(monitoringRef);
        LineRef line = new LineRef();
        line.setValue(lineRef);
        MonitoredStopVisitCancellation cancellation = new MonitoredStopVisitCancellation();
        cancellation.setMonitoringRef(monitoring);
        cancellation.setLineRef(line);

        StopMonitoringDeliveryStructure sm = new StopMonitoringDeliveryStructure();
        sm.getMonitoredStopVisitCancellations().add(cancellation);
        ServiceDelivery delivery = new ServiceDelivery();
        delivery.getStopMonitoringDeliveries().add(sm);
        Siri siri = new Siri();
        siri.setServiceDelivery(delivery);
        return siri;
    }
}
