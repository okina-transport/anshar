package no.rutebanken.anshar.routes.siri.processor;

import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.FramedVehicleJourneyRefStructure;
import uk.org.siri.siri21.MonitoredStopVisitCancellation;
import uk.org.siri.siri21.ServiceDelivery;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.StopMonitoringDeliveryStructure;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuterOutboundDatedVehicleRefAdapterTest {

    private static IdProcessingParameters vjParams() {
        IdProcessingParameters params = new IdProcessingParameters();
        params.setDatasetId("TEST");
        params.setObjectType(ObjectType.VEHICLE_JOURNEY);
        params.setInputPrefixToRemove("VJ_IN_");
        params.setOutputPrefixToAdd("VJ_OUT_");
        return params;
    }

    private static Siri smWithCancellation(String datedVehicleJourneyRef) {
        FramedVehicleJourneyRefStructure vjRef = new FramedVehicleJourneyRefStructure();
        vjRef.setDatedVehicleJourneyRef(datedVehicleJourneyRef);
        MonitoredStopVisitCancellation cancellation = new MonitoredStopVisitCancellation();
        cancellation.setVehicleJourneyRef(vjRef);
        StopMonitoringDeliveryStructure sm = new StopMonitoringDeliveryStructure();
        sm.getMonitoredStopVisitCancellations().add(cancellation);
        ServiceDelivery delivery = new ServiceDelivery();
        delivery.getStopMonitoringDeliveries().add(sm);
        Siri siri = new Siri();
        siri.setServiceDelivery(delivery);
        return siri;
    }

    private static String vjRefOf(Siri siri) {
        return siri.getServiceDelivery().getStopMonitoringDeliveries().get(0)
                .getMonitoredStopVisitCancellations().get(0)
                .getVehicleJourneyRef().getDatedVehicleJourneyRef();
    }

    private static Siri process(RuterOutboundDatedVehicleRefAdapter adapter, IdProcessingParameters params, String vjRef) {
        adapter.setIdProcessingParameters(params);
        Siri siri = smWithCancellation(vjRef);
        adapter.process(siri);
        return siri;
    }

    private static Siri process(OutboundIdMappingPolicy policy, IdProcessingParameters params, String vjRef) {
        return process(new RuterOutboundDatedVehicleRefAdapter(RuterOutboundDatedVehicleRefAdapterTest.class, policy), params, vjRef);
    }

    @Test
    void originalId_appliesIdProcessingParameters() {
        assertEquals("VJ_OUT_42", vjRefOf(process(OutboundIdMappingPolicy.ORIGINAL_ID, vjParams(), "VJ_IN_42")));
    }

    @Test
    void originalId_combinedId_appliesIdProcessingParametersToWholeValue() {
        // same behaviour as OutboundIdAdapter: combined id is not split before IPP
        assertEquals("VJ_OUT_42$MAPPED_42", vjRefOf(process(OutboundIdMappingPolicy.ORIGINAL_ID, vjParams(), "VJ_IN_42$MAPPED_42")));
    }

    @Test
    void originalId_noIdProcessingParameters_keepsValue() {
        assertEquals("VJ_IN_42$MAPPED_42", vjRefOf(process(OutboundIdMappingPolicy.ORIGINAL_ID, null, "VJ_IN_42$MAPPED_42")));
    }

    @Test
    void originalId_convertToNetexIdEnabled_vehicleJourneyIdNotTreatedAsStop() {
        // as built by MappingAdapterPresets: stop place replacement only applies to STOP IPP
        RuterOutboundDatedVehicleRefAdapter adapter = new RuterOutboundDatedVehicleRefAdapter(
                RuterOutboundDatedVehicleRefAdapterTest.class, OutboundIdMappingPolicy.ORIGINAL_ID, true);
        assertEquals("VJ_OUT_42", vjRefOf(process(adapter, vjParams(), "VJ_IN_42")));
    }

    @Test
    void defaultPolicy_appliesIdProcessingParameters() {
        assertEquals("VJ_OUT_42", vjRefOf(process(OutboundIdMappingPolicy.DEFAULT, vjParams(), "VJ_IN_42")));
    }
}
