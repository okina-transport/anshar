package no.rutebanken.anshar.routes.kafka;

import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.subscription.helpers.MappingAdapterPresets;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import uk.org.siri.siri21.FramedVehicleJourneyRefStructure;
import uk.org.siri.siri21.LineRef;
import uk.org.siri.siri21.MonitoredStopVisitCancellation;
import uk.org.siri.siri21.MonitoringRefStructure;
import uk.org.siri.siri21.ServiceDelivery;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.StopMonitoringDeliveryStructure;
import uk.org.siri.siri21.VehicleActivityStructure;
import uk.org.siri.siri21.VehicleMonitoringDeliveryStructure;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import static no.rutebanken.anshar.routes.validation.validators.Constants.DATASET_ID_HEADER_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdProcessingProcessorTest {

    private static final String DATASET_ID = "TEST";

    private CamelContext camelContext;
    private ApplicationContext previousApplicationContext;
    private SubscriptionConfig subscriptionConfig;
    private IdProcessingProcessor processor;

    @BeforeEach
    void setUp() {
        MappingAdapterPresets.clearCache();
        // Stop adapters look up StopPlaceUpdaterService through the static Spring context holder
        previousApplicationContext = ApplicationContextHolder.getContext();
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBean(StopPlaceUpdaterService.class)).thenReturn(mock(StopPlaceUpdaterService.class));
        new ApplicationContextHolder().setApplicationContext(applicationContext);
        camelContext = new DefaultCamelContext();
        subscriptionConfig = mock(SubscriptionConfig.class);
        processor = new IdProcessingProcessor(subscriptionConfig);
    }

    @AfterEach
    void tearDown() throws Exception {
        new ApplicationContextHolder().setApplicationContext(previousApplicationContext);
        MappingAdapterPresets.clearCache();
        camelContext.close();
    }

    private Exchange exchangeWith(Object body, String datasetId) {
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setBody(body);
        if (datasetId != null) {
            exchange.getIn().setHeader(DATASET_ID_HEADER_NAME, datasetId);
        }
        return exchange;
    }

    private static Siri vmSiriWithLineRef(String lineRefValue) {
        LineRef lineRef = new LineRef();
        lineRef.setValue(lineRefValue);
        VehicleActivityStructure.MonitoredVehicleJourney mvj = new VehicleActivityStructure.MonitoredVehicleJourney();
        mvj.setLineRef(lineRef);
        VehicleActivityStructure activity = new VehicleActivityStructure();
        activity.setMonitoredVehicleJourney(mvj);
        VehicleMonitoringDeliveryStructure vm = new VehicleMonitoringDeliveryStructure();
        vm.getVehicleActivities().add(activity);
        ServiceDelivery delivery = new ServiceDelivery();
        delivery.getVehicleMonitoringDeliveries().add(vm);
        Siri siri = new Siri();
        siri.setServiceDelivery(delivery);
        return siri;
    }

    private static Map<ObjectType, Optional<IdProcessingParameters>> lineParams(String inputPrefixToRemove, String outputPrefixToAdd) {
        IdProcessingParameters params = new IdProcessingParameters();
        params.setDatasetId(DATASET_ID);
        params.setObjectType(ObjectType.LINE);
        params.setInputPrefixToRemove(inputPrefixToRemove);
        params.setOutputPrefixToAdd(outputPrefixToAdd);

        Map<ObjectType, Optional<IdProcessingParameters>> map = emptyParams();
        map.put(ObjectType.LINE, Optional.of(params));
        return map;
    }

    private static Map<ObjectType, Optional<IdProcessingParameters>> emptyParams() {
        Map<ObjectType, Optional<IdProcessingParameters>> map = new EnumMap<>(ObjectType.class);
        for (ObjectType type : ObjectType.values()) {
            map.put(type, Optional.empty());
        }
        return map;
    }

    private static String lineRefOf(Siri siri) {
        return siri.getServiceDelivery()
                .getVehicleMonitoringDeliveries().get(0)
                .getVehicleActivities().get(0)
                .getMonitoredVehicleJourney()
                .getLineRef().getValue();
    }

    private static Siri smSiriWithOnlyCancellation(String monitoringRef, String lineRef, String datedVjRef) {
        MonitoringRefStructure monitoring = new MonitoringRefStructure();
        monitoring.setValue(monitoringRef);
        LineRef line = new LineRef();
        line.setValue(lineRef);
        FramedVehicleJourneyRefStructure vjRef = new FramedVehicleJourneyRefStructure();
        vjRef.setDatedVehicleJourneyRef(datedVjRef);

        MonitoredStopVisitCancellation cancellation = new MonitoredStopVisitCancellation();
        cancellation.setMonitoringRef(monitoring);
        cancellation.setLineRef(line);
        cancellation.setVehicleJourneyRef(vjRef);

        StopMonitoringDeliveryStructure sm = new StopMonitoringDeliveryStructure();
        sm.getMonitoredStopVisitCancellations().add(cancellation);
        ServiceDelivery delivery = new ServiceDelivery();
        delivery.getStopMonitoringDeliveries().add(sm);
        Siri siri = new Siri();
        siri.setServiceDelivery(delivery);
        return siri;
    }

    private static IdProcessingParameters params(ObjectType type, String inputPrefixToRemove, String outputPrefixToAdd) {
        IdProcessingParameters params = new IdProcessingParameters();
        params.setDatasetId(DATASET_ID);
        params.setObjectType(type);
        params.setInputPrefixToRemove(inputPrefixToRemove);
        params.setOutputPrefixToAdd(outputPrefixToAdd);
        return params;
    }

    private static MonitoredStopVisitCancellation cancellationOf(Siri siri) {
        return siri.getServiceDelivery()
                .getStopMonitoringDeliveries().get(0)
                .getMonitoredStopVisitCancellations().get(0);
    }

    @Test
    void process_appliesIdProcessingParametersToStopVisitCancellations() throws Exception {
        Map<ObjectType, Optional<IdProcessingParameters>> map = emptyParams();
        map.put(ObjectType.STOP, Optional.of(params(ObjectType.STOP, "STOP_IN_", "STOP_OUT_")));
        map.put(ObjectType.LINE, Optional.of(params(ObjectType.LINE, "LINE_IN_", "LINE_OUT_")));
        map.put(ObjectType.VEHICLE_JOURNEY, Optional.of(params(ObjectType.VEHICLE_JOURNEY, "VJ_IN_", "VJ_OUT_")));
        when(subscriptionConfig.buildIdProcessingParamsFromDataset(DATASET_ID)).thenReturn(map);
        Exchange exchange = exchangeWith(smSiriWithOnlyCancellation("STOP_IN_1", "LINE_IN_2", "VJ_IN_3"), DATASET_ID);

        processor.process(exchange);

        MonitoredStopVisitCancellation result = cancellationOf(exchange.getIn().getBody(Siri.class));
        assertEquals("STOP_OUT_1", result.getMonitoringRef().getValue());
        assertEquals("LINE_OUT_2", result.getLineRef().getValue());
        // ORIGINAL_ID policy: RuterOutboundDatedVehicleRefAdapter keeps original VJ id, VEHICLE_JOURNEY IPP not applied
        assertEquals("VJ_IN_3", result.getVehicleJourneyRef().getDatedVehicleJourneyRef());
    }

    @Test
    void process_nullBody_doesNothing() throws Exception {
        Exchange exchange = exchangeWith(null, DATASET_ID);

        processor.process(exchange);

        assertNull(exchange.getIn().getBody());
        verify(subscriptionConfig, never()).buildIdProcessingParamsFromDataset(any());
    }

    @Test
    void process_appliesLineIdProcessingParameters() throws Exception {
        when(subscriptionConfig.buildIdProcessingParamsFromDataset(DATASET_ID))
                .thenReturn(lineParams("IN_", "OUT_"));
        Siri original = vmSiriWithLineRef("IN_42");
        Exchange exchange = exchangeWith(original, DATASET_ID);

        processor.process(exchange);

        Siri result = exchange.getIn().getBody(Siri.class);
        assertEquals("OUT_42", lineRefOf(result));
        // deep copy: original message must be left untouched
        assertNotSame(original, result);
        assertEquals("IN_42", lineRefOf(original));
        verify(subscriptionConfig).buildIdProcessingParamsFromDataset(DATASET_ID);
    }

    @Test
    void process_noIdProcessingParameters_keepsIds() throws Exception {
        when(subscriptionConfig.buildIdProcessingParamsFromDataset(DATASET_ID))
                .thenReturn(emptyParams());
        Exchange exchange = exchangeWith(vmSiriWithLineRef("IN_42"), DATASET_ID);

        processor.process(exchange);

        assertEquals("IN_42", lineRefOf(exchange.getIn().getBody(Siri.class)));
    }

    @Test
    void process_missingDatasetHeader_queriesConfigWithNull() throws Exception {
        when(subscriptionConfig.buildIdProcessingParamsFromDataset(null))
                .thenReturn(emptyParams());
        Exchange exchange = exchangeWith(vmSiriWithLineRef("IN_42"), null);

        processor.process(exchange);

        verify(subscriptionConfig).buildIdProcessingParamsFromDataset(null);
        assertEquals("IN_42", lineRefOf(exchange.getIn().getBody(Siri.class)));
    }

    @Test
    void process_siriWithoutData_bodyUnchanged() throws Exception {
        when(subscriptionConfig.buildIdProcessingParamsFromDataset(DATASET_ID))
                .thenReturn(lineParams("IN_", "OUT_"));
        Siri emptySiri = new Siri();
        emptySiri.setServiceDelivery(new ServiceDelivery());
        Exchange exchange = exchangeWith(emptySiri, DATASET_ID);

        processor.process(exchange);

        assertSame(emptySiri, exchange.getIn().getBody());
    }
}
