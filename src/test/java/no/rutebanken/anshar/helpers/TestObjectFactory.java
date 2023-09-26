package no.rutebanken.anshar.helpers;

import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import uk.org.siri.siri20.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.UUID;

public class TestObjectFactory {
    public static final String TEST_SUBSCRIPTION_ID = "test.subscription.id";


    public static PtSituationElement createPtSituationElement(String participantRef, String situationNumber, ZonedDateTime startTime, ZonedDateTime endTime) {
        PtSituationElement element = new PtSituationElement();
        element.setCreationTime(ZonedDateTime.now());
        HalfOpenTimestampOutputRangeStructure period = new HalfOpenTimestampOutputRangeStructure();
        period.setStartTime(startTime);

        element.setParticipantRef(SiriObjectFactory.createRequestorRef(participantRef));

        SituationNumber sn = new SituationNumber();
        sn.setValue(situationNumber);
        element.setSituationNumber(sn);

        //ValidityPeriod has already expired
        period.setEndTime(endTime);
        element.getValidityPeriods().add(period);
        return element;
    }


    public static VehicleActivityStructure createVehicleActivityStructure(ZonedDateTime recordedAtTime, String vehicleReference, String vehicleMontoringRef) {
        return createVehicleActivityStructure(recordedAtTime, vehicleReference, "defaultLine", vehicleMontoringRef);
    }

    public static VehicleActivityStructure createVehicleActivityStructure(ZonedDateTime recordedAtTime, String vehicleReference, String lineRefValue, String vehicleMontoringRef) {
        VehicleActivityStructure element = new VehicleActivityStructure();
        element.setRecordedAtTime(recordedAtTime);
        element.setValidUntilTime(recordedAtTime.plusMinutes(10));


        VehicleMonitoringRefStructure monitoringRef = new VehicleMonitoringRefStructure();
        monitoringRef.setValue(vehicleMontoringRef);
        element.setVehicleMonitoringRef(monitoringRef);

        VehicleActivityStructure.MonitoredVehicleJourney vehicleJourney = new VehicleActivityStructure.MonitoredVehicleJourney();
        VehicleRef vRef = new VehicleRef();
        vRef.setValue(vehicleReference);
        vehicleJourney.setVehicleRef(vRef);
        LineRef lineRef = new LineRef();
        lineRef.setValue(lineRefValue);

        vehicleJourney.setLineRef(lineRef);

        LocationStructure location = new LocationStructure();
        location.setLatitude(BigDecimal.valueOf(10.63));
        location.setLongitude(BigDecimal.valueOf(63.10));
        vehicleJourney.setVehicleLocation(location);

        CourseOfJourneyRefStructure journeyRefStructure = new CourseOfJourneyRefStructure();
        journeyRefStructure.setValue("yadayada");
        vehicleJourney.setCourseOfJourneyRef(journeyRefStructure);

        element.setMonitoredVehicleJourney(vehicleJourney);
        return element;
    }

    public static EstimatedVehicleJourney createEstimatedVehicleJourney(String lineRefValue, String vehicleRefValue, int startOrder, int callCount, ZonedDateTime arrival, Boolean isComplete) {
        return createEstimatedVehicleJourney(lineRefValue, vehicleRefValue, startOrder, callCount, arrival, arrival, isComplete);
    }

    public static EstimatedVehicleJourney createEstimatedVehicleJourney(String lineRefValue, String vehicleRefValue, int startOrder, int callCount, ZonedDateTime arrival, ZonedDateTime departure, Boolean isComplete) {
        EstimatedVehicleJourney element = new EstimatedVehicleJourney();
        LineRef lineRef = new LineRef();
        lineRef.setValue(lineRefValue);
        element.setLineRef(lineRef);
        VehicleRef vehicleRef = new VehicleRef();
        vehicleRef.setValue(vehicleRefValue);
        element.setVehicleRef(vehicleRef);
        element.setIsCompleteStopSequence(isComplete);

        EstimatedVehicleJourney.EstimatedCalls estimatedCalls = new EstimatedVehicleJourney.EstimatedCalls();
        for (int i = startOrder; i < callCount; i++) {

            StopPointRef stopPointRef = new StopPointRef();
            stopPointRef.setValue("NSR:TEST:" + i);
            EstimatedCall call = new EstimatedCall();
            call.setStopPointRef(stopPointRef);
            call.setAimedArrivalTime(arrival);
            call.setExpectedArrivalTime(arrival);
            call.setAimedDepartureTime(departure);
            call.setExpectedDepartureTime(departure);
            call.setOrder(BigInteger.valueOf(i));
            call.setVisitNumber(BigInteger.valueOf(i));
            estimatedCalls.getEstimatedCalls().add(call);
        }

        element.setEstimatedCalls(estimatedCalls);
        element.setRecordedAtTime(ZonedDateTime.now());

        return element;
    }

    public static MonitoredStopVisit createMonitoredStopVisit(ZonedDateTime recordedAtTime, String stopReference) {
        return createMonitoredStopVisit(recordedAtTime, stopReference, UUID.randomUUID().toString());
    }

    public static MonitoredStopVisit createMonitoredStopVisit(ZonedDateTime recordedAtTime, String stopReference, String itemIdentifier) {
        MonitoredStopVisit element = new MonitoredStopVisit();

        element.setRecordedAtTime(recordedAtTime);
        MonitoringRefStructure monitoringRefStructure = new MonitoringRefStructure();
        monitoringRefStructure.setValue(stopReference);
        element.setMonitoringRef(monitoringRefStructure);

        MonitoredVehicleJourneyStructure monitoredVehicleJourneyStructure = new MonitoredVehicleJourneyStructure();
        MonitoredCallStructure monitoredCallStructure = new MonitoredCallStructure();
        monitoredCallStructure.setExpectedArrivalTime(ZonedDateTime.now().plusHours(1));
        monitoredVehicleJourneyStructure.setMonitoredCall(monitoredCallStructure);
        element.setMonitoredVehicleJourney(monitoredVehicleJourneyStructure);

        element.setItemIdentifier(itemIdentifier);
        return element;
    }

    public static GeneralMessage createGeneralMessage() {
        return createGeneralMessage("Perturbation");
    }

    public static GeneralMessage createGeneralMessage(String infoChannel) {

        GeneralMessage msg = new GeneralMessage();
        InfoMessageRefStructure identifier = new InfoMessageRefStructure();
        identifier.setValue(UUID.randomUUID().toString());
        msg.setInfoMessageIdentifier(identifier);
        InfoChannelRefStructure RefStruct = new InfoChannelRefStructure();
        RefStruct.setValue(infoChannel);
        msg.setInfoChannelRef(RefStruct);

        return msg;
    }

    public static FacilityConditionStructure createFacilityMonitoring() {
        return createFacilityMonitoring("facility");
    }

    public static FacilityConditionStructure createFacilityMonitoring(String infoRef) {

        FacilityConditionStructure fcs = new FacilityConditionStructure();
        FacilityRef facilityRef = new FacilityRef();
        FacilityStructure facilityStructure = new FacilityStructure();

        facilityRef.setValue(infoRef);

        facilityStructure.setFacilityCode(infoRef);

        fcs.setFacilityRef(facilityRef);
        fcs.setFacility(facilityStructure);

        return fcs;
    }

    public static SubscriptionSetup getSubscriptionSetup(SiriDataType subscriptionType) {
        SubscriptionSetup sub = new SubscriptionSetup();
        sub.setSubscriptionType(subscriptionType);
        sub.setRequestorRef("TestSubscription");
        sub.setSubscriptionId(TEST_SUBSCRIPTION_ID);
        sub.setDurationOfSubscriptionHours(1);
        sub.setAddress("http://localhost:1234/incoming");
        return sub;
    }
}
