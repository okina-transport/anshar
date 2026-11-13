package no.rutebanken.anshar.routes.siri.transformer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.databind.deser.ValueInstantiator;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule;
import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.routes.siri.transformer.JsonDTOs.VehicleMonitoringJsonDTO;
import no.rutebanken.anshar.util.DateUtils;
import org.apache.commons.lang3.StringUtils;
import uk.org.siri.siri21.*;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mapping class to convert a json representing VehicleMonitoring data to a Siri object
 */
@Slf4j
public class SiriJsonTransformer {

    private SiriJsonTransformer() {
        throw new IllegalStateException();
    }

    private static final ObjectMapper SIRI_READER = createSiriReader();

    private static ObjectMapper createSiriReader() {
        ObjectMapper mapper = JsonMapper.builder()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.READ_ENUMS_USING_TO_STRING, true)
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
                .build();
        mapper.registerModule(new JavaTimeModule());
        mapper.registerModule(new JakartaXmlBindAnnotationModule());

        // SIRI Lite producers send plain strings for SIRI types wrapping a single value (LineRef, MonitoringRef, ...)
        mapper.addHandler(new DeserializationProblemHandler() {
            @Override
            public Object handleMissingInstantiator(DeserializationContext ctxt, Class<?> instClass, ValueInstantiator valueInsta,
                                                    JsonParser p, String msg) throws IOException {
                if (p.currentToken() == null || !p.currentToken().isScalarValue()) {
                    return NOT_HANDLED;
                }
                try {
                    Object instance = instClass.getDeclaredConstructor().newInstance();
                    instClass.getMethod("setValue", String.class).invoke(instance, p.getText());
                    return instance;
                } catch (ReflectiveOperationException e) {
                    return NOT_HANDLED;
                }
            }
        });
        return mapper;
    }

    /**
     * Converts a SIRI Lite json (with or without the "Siri" root element) to a Siri object
     *
     * @param json the SIRI Lite json
     * @return a siri object, or null if the json cannot be read
     */
    public static Siri convertJsonToSiri(String json) {
        try {
            JsonNode root = SIRI_READER.readTree(json);
            JsonNode siriNode = root.has("Siri") ? root.get("Siri") : root;
            return SIRI_READER.treeToValue(siriNode, Siri.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.error("Error while converting SIRI Lite json to Siri", e);
            return null;
        }
    }

    public static Siri convertJsonVMtoSiri(String jsonVehicleMonitoring) {


        Siri result = null;
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            VehicleMonitoringJsonDTO vehicleMonitoringJsonDTO = objectMapper.readValue(jsonVehicleMonitoring, new TypeReference<>() {
            });
            result = mapDTOToSiri(vehicleMonitoringJsonDTO);

        } catch (JsonProcessingException e) {
            log.error("Error while converting vehicleMonitoringJson to DTO");
        }

        return result;
    }

    /**
     * Converts a DTO that contains VehicleMonitoring data (json format) to a Siri object
     *
     * @param vehicleMonitoringJsonDTO the DTO that contains VehicleMonitoring data
     * @return a siri object
     */

    private static Siri mapDTOToSiri(VehicleMonitoringJsonDTO vehicleMonitoringJsonDTO) {

        Siri createdSiri = new Siri();
        createdSiri.setServiceDelivery(mapToServiceDelivery(vehicleMonitoringJsonDTO));

        return createdSiri;
    }

    /**
     * Converts a DTO that contains VehicleMonitoring data (json format) to a ServiceDelivery
     *
     * @param vehicleMonitoringJsonDTO the DTO that contains VehicleMonitoring data
     * @return the service delivery
     */
    private static uk.org.siri.siri21.ServiceDelivery mapToServiceDelivery(VehicleMonitoringJsonDTO vehicleMonitoringJsonDTO) {

        uk.org.siri.siri21.ServiceDelivery createdServiceDelivery = new uk.org.siri.siri21.ServiceDelivery();
        createdServiceDelivery.setResponseTimestamp(DateUtils.convertStringToZonedDateTime(vehicleMonitoringJsonDTO.getServiceDelivery().getResponseTimestamp()));

        MessageRefStructure messageRef = new MessageRefStructure();
        messageRef.setValue(StringUtils.isNotEmpty(vehicleMonitoringJsonDTO.getServiceDelivery().getRequestMessageRef()) ? vehicleMonitoringJsonDTO.getServiceDelivery().getRequestMessageRef() : UUID.randomUUID().toString());
        createdServiceDelivery.setRequestMessageRef(messageRef);
        createdServiceDelivery.getVehicleMonitoringDeliveries().addAll(mapVehicleMonitoringDeliveries(vehicleMonitoringJsonDTO.getServiceDelivery().getVehicleMonitoringDelivery()));

        return createdServiceDelivery;
    }

    /**
     * Converts a list of monitoringDelivery coming from the DTO to a siri list of DeliveryStructure
     *
     * @param vehicleMonitoringDelivery the delivery from json DTO
     * @return the siri list of delivery
     */
    private static List<VehicleMonitoringDeliveryStructure> mapVehicleMonitoringDeliveries(List<VehicleMonitoringJsonDTO.ServiceDelivery.VehicleMonitoringDelivery> vehicleMonitoringDelivery) {
        List<VehicleMonitoringDeliveryStructure> createdDeliveries = new ArrayList<>();
        for (VehicleMonitoringJsonDTO.ServiceDelivery.VehicleMonitoringDelivery monitoringDelivery : vehicleMonitoringDelivery) {
            createdDeliveries.add(mapVehicleMonitoringDelivery(monitoringDelivery));
        }
        return createdDeliveries;
    }

    /**
     * Converts a monitoring delivery from the json DTO to a Siri list of delivery
     *
     * @param monitoringDelivery monitoring delivery from the json DTO
     * @return the siri converted object
     */
    private static VehicleMonitoringDeliveryStructure mapVehicleMonitoringDelivery(VehicleMonitoringJsonDTO.ServiceDelivery.VehicleMonitoringDelivery monitoringDelivery) {
        VehicleMonitoringDeliveryStructure createdVehicleMonitoringStruct = new VehicleMonitoringDeliveryStructure();
        createdVehicleMonitoringStruct.setResponseTimestamp(DateUtils.convertStringToZonedDateTime(monitoringDelivery.getResponseTimestamp()));
        createdVehicleMonitoringStruct.setValidUntil(DateUtils.convertStringToZonedDateTime(monitoringDelivery.getValidUntil()));
        if (StringUtils.isNotEmpty(monitoringDelivery.getShortestPossibleCycle())) {
            try {
                createdVehicleMonitoringStruct.setShortestPossibleCycle(DatatypeFactory.newInstance().newDuration(monitoringDelivery.getShortestPossibleCycle()));
            } catch (DatatypeConfigurationException e) {
                log.error("Error while creating duration for shortestPossibleCycle", e);
            }
        }
        if (monitoringDelivery.getVehicleActivity() != null) {
            createdVehicleMonitoringStruct.getVehicleActivities().addAll(mapVehicleActivities(monitoringDelivery.getVehicleActivity()));
        }

        for (VehicleActivityStructure vehicleActivity : createdVehicleMonitoringStruct.getVehicleActivities()) {
            vehicleActivity.setValidUntilTime(createdVehicleMonitoringStruct.getValidUntil());
        }
        return createdVehicleMonitoringStruct;
    }

    /**
     * Converts a bacth of vehicleActivities coming from the json DTO to a siri object
     *
     * @param vehicleActivities Activities coming from the json DTO
     * @return A list of siri VehicleActivityStructure
     */
    private static List<VehicleActivityStructure> mapVehicleActivities(List<VehicleMonitoringJsonDTO.ServiceDelivery.VehicleActivity> vehicleActivities) {
        List<VehicleActivityStructure> results = new ArrayList<>();
        for (VehicleMonitoringJsonDTO.ServiceDelivery.VehicleActivity vehicleActivity : vehicleActivities) {
            results.add(mapVehicleActivity(vehicleActivity));
        }
        return results;
    }

    /**
     * Converts a vehicleActivity coming from the json DTO to a Siri VehicleActivityStructure
     *
     * @param vehicleActivity activity coming from the json DTO
     * @return a siri vehicleActivityStructure
     */
    private static VehicleActivityStructure mapVehicleActivity(VehicleMonitoringJsonDTO.ServiceDelivery.VehicleActivity vehicleActivity) {
        VehicleActivityStructure createdVehicleActivity = new VehicleActivityStructure();
        createdVehicleActivity.setRecordedAtTime(DateUtils.convertStringToZonedDateTime(vehicleActivity.getRecordedAtTime()));
        createdVehicleActivity.setMonitoredVehicleJourney(mapVehicleJourney(vehicleActivity.getMonitoredVehicleJourney()));
        if (StringUtils.isNotEmpty(vehicleActivity.getMonitoredVehicleJourney().getVehicleRef())) {
            VehicleMonitoringRefStructure vehicleMonitoringRef = new VehicleMonitoringRefStructure();
            vehicleMonitoringRef.setValue(vehicleActivity.getMonitoredVehicleJourney().getVehicleRef());
            createdVehicleActivity.setVehicleMonitoringRef(vehicleMonitoringRef);
        }
        return createdVehicleActivity;
    }

    /**
     * Converts a vehicleJourney coming from json DTO to a Siri MonitoredVehicleJourney
     *
     * @param monitoredVehicleJourney a vehicle journey coming from json DTO
     * @return a siri MonitoredVehicleJourney
     */
    private static VehicleActivityStructure.MonitoredVehicleJourney mapVehicleJourney(VehicleMonitoringJsonDTO.ServiceDelivery.MonitoredVehicleJourney monitoredVehicleJourney) {
        VehicleActivityStructure.MonitoredVehicleJourney createdVehicleJourney = new VehicleActivityStructure.MonitoredVehicleJourney();

        if (StringUtils.isNotEmpty(monitoredVehicleJourney.getLineRef())) {
            LineRef lineRef = new LineRef();
            lineRef.setValue(monitoredVehicleJourney.getLineRef());
            createdVehicleJourney.setLineRef(lineRef);
        }


        DirectionRefStructure directionRef = new DirectionRefStructure();
        directionRef.setValue(String.valueOf(monitoredVehicleJourney.getDirectionRef()));
        createdVehicleJourney.setDirectionRef(directionRef);

        if (monitoredVehicleJourney.getFramedVehicleJourneyRef() != null) {
            FramedVehicleJourneyRefStructure framedVehJourneyRefStruct = new FramedVehicleJourneyRefStructure();
            framedVehJourneyRefStruct.setDatedVehicleJourneyRef(monitoredVehicleJourney.getFramedVehicleJourneyRef().getDatedVehicleJourneySAERef());
            createdVehicleJourney.setFramedVehicleJourneyRef(framedVehJourneyRefStruct);
        }

        if (StringUtils.isNotEmpty(monitoredVehicleJourney.getVehicleMode())) {
            createdVehicleJourney.getVehicleModes().add(VehicleModesEnumeration.fromValue(monitoredVehicleJourney.getVehicleMode()));
        }

        if (StringUtils.isNotEmpty(monitoredVehicleJourney.getPublishedLineName())) {
            NaturalLanguageStringStructure publishedLineName = new NaturalLanguageStringStructure();
            publishedLineName.setValue(monitoredVehicleJourney.getPublishedLineName());
            createdVehicleJourney.getPublishedLineNames().add(publishedLineName);
        }

        if (StringUtils.isNotEmpty(monitoredVehicleJourney.getDestinationName())) {
            NaturalLanguageStringStructure destinationName = new NaturalLanguageStringStructure();
            destinationName.setValue(monitoredVehicleJourney.getDestinationName());
            createdVehicleJourney.getDestinationNames().add(destinationName);
        }

        if (StringUtils.isNotEmpty(monitoredVehicleJourney.getDestinationShortName())) {
            NaturalLanguagePlaceNameStructure destinationShortName = new NaturalLanguagePlaceNameStructure();
            destinationShortName.setValue(monitoredVehicleJourney.getDestinationShortName());
            createdVehicleJourney.getDestinationShortNames().add(destinationShortName);
        }

        if (StringUtils.isNotEmpty(monitoredVehicleJourney.getDestinationRef())) {
            DestinationRef destRef = new DestinationRef();
            destRef.setValue(monitoredVehicleJourney.getDestinationRef());
            createdVehicleJourney.setDestinationRef(destRef);
        }

        if (StringUtils.isNotEmpty(monitoredVehicleJourney.getVia())) {
            ViaNameStructure via = new ViaNameStructure();
            JourneyPlaceRefStructure journeyPlaceRef = new JourneyPlaceRefStructure();
            journeyPlaceRef.setValue(monitoredVehicleJourney.getVia());
            via.setPlaceRef(journeyPlaceRef);
            createdVehicleJourney.getVias().add(via);
        }

        if (monitoredVehicleJourney.getVehicleLocation() != null) {
            LocationStructure locationStruct = new LocationStructure();
            locationStruct.setLatitude(BigDecimal.valueOf(monitoredVehicleJourney.getVehicleLocation().getLatitude()));
            locationStruct.setLongitude(BigDecimal.valueOf(monitoredVehicleJourney.getVehicleLocation().getLongitude()));
            createdVehicleJourney.setVehicleLocation(locationStruct);
        }

        createdVehicleJourney.setBearing((float) monitoredVehicleJourney.getBearing());

        try {
            createdVehicleJourney.setDelay(DatatypeFactory.newInstance().newDuration(monitoredVehicleJourney.getDelay()));
        } catch (DatatypeConfigurationException e) {
            log.error("Error while converting delay", e);
        }

        if (StringUtils.isNotEmpty(monitoredVehicleJourney.getVehicleRef())) {
            VehicleRef vehRef = new VehicleRef();
            vehRef.setValue(monitoredVehicleJourney.getVehicleRef());
            createdVehicleJourney.setVehicleRef(vehRef);
        }

        if (StringUtils.isNotEmpty(monitoredVehicleJourney.getVehicleStatus())) {
            try {
                createdVehicleJourney.setVehicleStatus(VehicleStatusEnumeration.fromValue(monitoredVehicleJourney.getVehicleStatus()));
            } catch (IllegalArgumentException e) {
                log.error("Unable to create vehicleStatus for value: {}", monitoredVehicleJourney.getVehicleStatus());
            }
        }

        createdVehicleJourney.setMonitoredCall(mapMonitoredCall(monitoredVehicleJourney.getMonitoredCall()));
        createdVehicleJourney.setOnwardCalls(mapOnwardCalls(monitoredVehicleJourney.getOnwardCall()));

        return createdVehicleJourney;
    }

    /**
     * Converts a list of onward calls coming from json DTO to a siri OnwardCallsStructure
     *
     * @param onwardCall list of onward calls coming from json siri
     * @return the siri OnwardCallsStructure
     */
    private static OnwardCallsStructure mapOnwardCalls(List<VehicleMonitoringJsonDTO.ServiceDelivery.OnwardCall> onwardCall) {
        OnwardCallsStructure createdCall = new OnwardCallsStructure();
        for (VehicleMonitoringJsonDTO.ServiceDelivery.OnwardCall call : onwardCall) {
            createdCall.getOnwardCalls().add(mapOnwardCall(call));
        }
        return createdCall;
    }

    /**
     * Converts a onwardCall coming from json DTO to a siri OnwardCallStructure
     *
     * @param call the onwardCall coming from json DTO
     * @return the siri OnwardCallStructure
     */
    private static OnwardCallStructure mapOnwardCall(VehicleMonitoringJsonDTO.ServiceDelivery.OnwardCall call) {
        OnwardCallStructure createdCall = new OnwardCallStructure();

        if (StringUtils.isNotEmpty(call.getStopPointName())) {
            NaturalLanguageStringStructure stopPointName = new NaturalLanguageStringStructure();
            stopPointName.setValue(call.getStopPointName());
            createdCall.getStopPointNames().add(stopPointName);
        }


        if (StringUtils.isNotEmpty(call.getStopCode())) {
            StopPointRefStructure stopRef = new StopPointRefStructure();
            stopRef.setValue(call.getStopCode());
            createdCall.setStopPointRef(stopRef);
        }

        createdCall.setOrder(BigInteger.valueOf(call.getOrder()));

        if (StringUtils.isNotEmpty(call.getExpectedDepartureTime())) {
            createdCall.setExpectedDepartureTime(DateUtils.convertStringToZonedDateTime(call.getExpectedDepartureTime()));
        }

        if (StringUtils.isNotEmpty(call.getExpectedArrivalTime())) {
            createdCall.setExpectedArrivalTime(DateUtils.convertStringToZonedDateTime(call.getExpectedArrivalTime()));
        }

        return createdCall;

    }

    /**
     * Converts a monitoredCall coming from json DTO to a siri MonitoredCallStructure
     *
     * @param monitoredCall a monitoredCall from json DTO
     * @return the siri MonitoredCallStructure
     */
    private static MonitoredCallStructure mapMonitoredCall(VehicleMonitoringJsonDTO.ServiceDelivery.MonitoredCall monitoredCall) {
        MonitoredCallStructure createdMonitoredCall = new MonitoredCallStructure();

        if (StringUtils.isNotEmpty(monitoredCall.getStopPointName())) {
            NaturalLanguageStringStructure stopPointName = new NaturalLanguageStringStructure();
            stopPointName.setValue(monitoredCall.getStopPointName());
            createdMonitoredCall.getStopPointNames().add(stopPointName);
        }

        if (StringUtils.isNotEmpty(monitoredCall.getStopCode())) {
            StopPointRefStructure stopRef = new StopPointRefStructure();
            stopRef.setValue(monitoredCall.getStopCode());
            createdMonitoredCall.setStopPointRef(stopRef);
        }


        createdMonitoredCall.setOrder(BigInteger.valueOf(monitoredCall.getOrder()));

        if (StringUtils.isNotEmpty(monitoredCall.getExpectedDepartureTime())) {
            createdMonitoredCall.setExpectedDepartureTime(DateUtils.convertStringToZonedDateTime(monitoredCall.getExpectedDepartureTime()));
        }

        if (StringUtils.isNotEmpty(monitoredCall.getExpectedArrivalTime())) {
            createdMonitoredCall.setExpectedArrivalTime(DateUtils.convertStringToZonedDateTime(monitoredCall.getExpectedArrivalTime()));
        }


        return createdMonitoredCall;
    }
}
