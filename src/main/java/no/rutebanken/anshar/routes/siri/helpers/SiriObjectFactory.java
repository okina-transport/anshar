/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package no.rutebanken.anshar.routes.siri.helpers;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.pool.KryoFactory;
import com.esotericsoftware.kryo.pool.KryoPool;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.data.FacilityMonitoring;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.xerces.dom.ElementNSImpl;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.*;

import javax.xml.bind.JAXBException;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class SiriObjectFactory {

    private static final String SIRI_VERSION = "2.0";
    private static final Logger logger = LoggerFactory.getLogger(SiriObjectFactory.class);

    private static final KryoPool kryoPool;

    static {
    	KryoFactory factory = () -> {
                      Kryo kryo = new Kryo();
                      kryo.setInstantiatorStrategy(new Kryo.DefaultInstantiatorStrategy(new StdInstantiatorStrategy()));
                      kryo.register(ElementNSImpl.class, new Serializer() {


                @Override
                public void write(Kryo kryo, Output output, Object object) {
                    throw new NotImplementedException("write-method not implemented");
                }

                @Override
                public Object read(Kryo kryo, Input input, Class type) {
                    throw new NotImplementedException("read-method not implemented");
                }

                @Override
                public Object copy(Kryo kryo, Object original) {

                    return ((ElementNSImpl) original).cloneNode(true);
                }
            });

          // configure kryo instance, customize settings
          return kryo;
        };

    	kryoPool = new KryoPool.Builder(factory).softReferences().build();

    }

    @Autowired
    private AnsharConfiguration configuration;

    public final Instant serverStartTime;

    public SiriObjectFactory(@Autowired Instant serverStartTime) {
        this.serverStartTime = serverStartTime;
    }
    
    public static Siri createSubscriptionRequest(SubscriptionSetup subscriptionSetup) {
        Siri siri = createSiriObject();

        SubscriptionRequest request = null;

        if (subscriptionSetup.getSubscriptionType().equals(SiriDataType.SITUATION_EXCHANGE)) {
            request = createSituationExchangeSubscriptionRequest(subscriptionSetup.getRequestorRef(),subscriptionSetup.getSubscriptionId(),
                    subscriptionSetup.getHeartbeatInterval(),
                    subscriptionSetup.buildUrl(),
                    subscriptionSetup.getDurationOfSubscription(),
                    subscriptionSetup.getFilterMap(),
                    subscriptionSetup.getAddressFieldName(),
                    subscriptionSetup.getIncrementalUpdates(),
                    subscriptionSetup.getPreviewInterval());
        }
        if (subscriptionSetup.getSubscriptionType().equals(SiriDataType.VEHICLE_MONITORING)) {
            request = createVehicleMonitoringSubscriptionRequest(subscriptionSetup.getRequestorRef(),
                    subscriptionSetup.getSubscriptionId(),
                    subscriptionSetup.getHeartbeatInterval(),
                    subscriptionSetup.buildUrl(),
                    subscriptionSetup.getDurationOfSubscription(),
                    subscriptionSetup.getFilterMap(),
                    subscriptionSetup.getUpdateInterval(),
                    subscriptionSetup.getChangeBeforeUpdates(),
                    subscriptionSetup.getAddressFieldName(),
                    subscriptionSetup.getIncrementalUpdates(),
                    subscriptionSetup.getVehicleMonitoringRefValue(),
                    subscriptionSetup.getLineRefValue()
                    );
        }
        if (subscriptionSetup.getSubscriptionType().equals(SiriDataType.ESTIMATED_TIMETABLE)) {
            request = createEstimatedTimetableSubscriptionRequest(
                    subscriptionSetup.getRequestorRef(),
                    subscriptionSetup.getSubscriptionId(),
                    subscriptionSetup.getHeartbeatInterval(),
                    subscriptionSetup.buildUrl(),
                    subscriptionSetup.getDurationOfSubscription(),
                    subscriptionSetup.getFilterMap(),
                    subscriptionSetup.getAddressFieldName(),
                    subscriptionSetup.getIncrementalUpdates(),
                    subscriptionSetup.getPreviewInterval(),
                    subscriptionSetup.getChangeBeforeUpdates());
        }
        if (subscriptionSetup.getSubscriptionType().equals(SiriDataType.STOP_MONITORING)) {
            request = createStopMonitoringSubscriptionRequest(
                    subscriptionSetup.getRequestorRef(),
                    subscriptionSetup.getSubscriptionId(),
                    subscriptionSetup.getHeartbeatInterval(),
                    subscriptionSetup.buildUrl(),
                    subscriptionSetup.getDurationOfSubscription(),
                    subscriptionSetup.getFilterMap(),
                    subscriptionSetup.getAddressFieldName(),
                    subscriptionSetup.getIncrementalUpdates(),
                    subscriptionSetup.getPreviewInterval(),
                    subscriptionSetup.getChangeBeforeUpdates(),
                    subscriptionSetup.getStopMonitoringRefValue());
        }

        if (subscriptionSetup.getSubscriptionType().equals(SiriDataType.GENERAL_MESSAGE)) {
            request = createGeneralMessageSubscriptionRequest(
                    subscriptionSetup.getRequestorRef(),
                    subscriptionSetup.getSubscriptionId(),
                    subscriptionSetup.getHeartbeatInterval(),
                    subscriptionSetup.buildUrl(),
                    subscriptionSetup.getDurationOfSubscription(),
                    subscriptionSetup.getAddressFieldName());
        }

        if (subscriptionSetup.getSubscriptionType().equals(SiriDataType.FACILITY_MONITORING)) {
            request = createFacilityMonitoringSubscriptionRequest(
                    subscriptionSetup.getRequestorRef(),
                    subscriptionSetup.getSubscriptionId(),
                    subscriptionSetup.getHeartbeatInterval(),
                    subscriptionSetup.buildUrl(),
                    subscriptionSetup.getDurationOfSubscription(),
                    subscriptionSetup.getAddressFieldName());
        }

        siri.setSubscriptionRequest(request);

        return siri;
    }


    public static Siri createServiceRequest(SubscriptionSetup subscriptionSetup) {
        Siri siri = createSiriObject();

        ServiceRequest request = new ServiceRequest();
        request.setRequestTimestamp(ZonedDateTime.now());
        request.setRequestorRef(createRequestorRef(subscriptionSetup.getRequestorRef()));

        if (subscriptionSetup.getSubscriptionType().equals(SiriDataType.SITUATION_EXCHANGE)) {
            SituationExchangeRequestStructure sxRequestStruct = createSituationExchangeRequestStructure(subscriptionSetup.getPreviewInterval());
            request.setMessageIdentifier(sxRequestStruct.getMessageIdentifier());
            request.getSituationExchangeRequests().add(sxRequestStruct);

        }
        if (subscriptionSetup.getSubscriptionType().equals(SiriDataType.VEHICLE_MONITORING)) {
            VehicleMonitoringRequestStructure vmRequestStruct = createVehicleMonitoringRequestStructure();
            request.setMessageIdentifier(vmRequestStruct.getMessageIdentifier());
            request.getVehicleMonitoringRequests().add(vmRequestStruct);
        }
        if (subscriptionSetup.getSubscriptionType().equals(SiriDataType.ESTIMATED_TIMETABLE)) {
            EstimatedTimetableRequestStructure etRequestStruct = createEstimatedTimetableRequestStructure(subscriptionSetup.getPreviewInterval());
            request.setMessageIdentifier(etRequestStruct.getMessageIdentifier());
            request.getEstimatedTimetableRequests().add(etRequestStruct);
        }
        if (subscriptionSetup.getSubscriptionType().equals(SiriDataType.STOP_MONITORING)) {
            StopMonitoringRequestStructure smRequestStruct = createStopMonitoringRequestStructure(subscriptionSetup);
            request.setMessageIdentifier(smRequestStruct.getMessageIdentifier());
            request.getStopMonitoringRequests().add(smRequestStruct);
        }
        if (subscriptionSetup.getSubscriptionType().equals(SiriDataType.GENERAL_MESSAGE)) {
            GeneralMessageRequestStructure gmRequestStruct = createGeneralMessageRequestStructure(subscriptionSetup);
            request.setMessageIdentifier(gmRequestStruct.getMessageIdentifier());
            request.getGeneralMessageRequests().add(gmRequestStruct);
        }
        if (subscriptionSetup.getSubscriptionType().equals(SiriDataType.FACILITY_MONITORING)) {
            FacilityMonitoringRequestStructure fmRequestStruct = createFacilityMonitoringRequestStructure(subscriptionSetup);
            request.setMessageIdentifier(fmRequestStruct.getMessageIdentifier());
            request.getFacilityMonitoringRequests().add(fmRequestStruct);
        }
        siri.setServiceRequest(request);

        return siri;
    }


    public static Siri createDataSupplyRequest(SubscriptionSetup subscriptionSetup, Boolean allData) {
        Siri siri = createSiriObject();

        DataSupplyRequestStructure request = new DataSupplyRequestStructure();
        request.setRequestTimestamp(ZonedDateTime.now());
        request.setConsumerRef(createRequestorRef(subscriptionSetup.getRequestorRef()));
        request.setAllData(allData);

        siri.setDataSupplyRequest(request);

        return siri;
    }

    public static Siri createCheckStatusRequest(SubscriptionSetup subscriptionSetup) {
        Siri siri = createSiriObject();

        CheckStatusRequestStructure statusRequest = new CheckStatusRequestStructure();
        statusRequest.setRequestTimestamp(ZonedDateTime.now());
        statusRequest.setMessageIdentifier(createMessageIdentifier());
        statusRequest.setRequestorRef(createRequestorRef(subscriptionSetup.getRequestorRef()));
        siri.setCheckStatusRequest(statusRequest);

        return siri;
    }

    private static SituationExchangeRequestStructure createSituationExchangeRequestStructure(Duration previewInterval) {
        SituationExchangeRequestStructure sxRequest = new SituationExchangeRequestStructure();
        sxRequest.setRequestTimestamp(ZonedDateTime.now());
        sxRequest.setVersion(SIRI_VERSION);
        sxRequest.setMessageIdentifier(createMessageIdentifier());
        if (previewInterval != null) {
            sxRequest.setPreviewInterval(createDataTypeFactory().newDuration(previewInterval.toString()));
        }
        return sxRequest;
    }

    private static VehicleMonitoringRequestStructure createVehicleMonitoringRequestStructure() {
        VehicleMonitoringRequestStructure vmRequest = new VehicleMonitoringRequestStructure();
        vmRequest.setRequestTimestamp(ZonedDateTime.now());
        vmRequest.setVersion(SIRI_VERSION);
        vmRequest.setMessageIdentifier(createMessageIdentifier());
        return vmRequest;
    }

    private static EstimatedTimetableRequestStructure createEstimatedTimetableRequestStructure(Duration previewInterval) {
        EstimatedTimetableRequestStructure etRequest = new EstimatedTimetableRequestStructure();
        etRequest.setRequestTimestamp(ZonedDateTime.now());
        etRequest.setVersion(SIRI_VERSION);
        etRequest.setMessageIdentifier(createMessageIdentifier());
        if (previewInterval != null) {
            etRequest.setPreviewInterval(createDataTypeFactory().newDuration(previewInterval.toString()));
        }
        return etRequest;
    }

    // TODO MHI
    private static StopMonitoringRequestStructure createStopMonitoringRequestStructure(SubscriptionSetup subscriptionSetup) {
        StopMonitoringRequestStructure smRequest = new StopMonitoringRequestStructure();
        smRequest.setRequestTimestamp(ZonedDateTime.now());
        smRequest.setVersion(SIRI_VERSION);
        smRequest.setMessageIdentifier(createMessageIdentifier());
        if (subscriptionSetup.getPreviewInterval() != null) {
            smRequest.setPreviewInterval(createDataTypeFactory().newDuration(subscriptionSetup.getPreviewInterval().toString()));
        }
        if (subscriptionSetup.getStopMonitoringRefValue() != null) {
            MonitoringRefStructure monitoringRefStructure = new MonitoringRefStructure();
            monitoringRefStructure.setValue(subscriptionSetup.getStopMonitoringRefValue());
            smRequest.setMonitoringRef(monitoringRefStructure);
        }
        return smRequest;
    }

    private static GeneralMessageRequestStructure createGeneralMessageRequestStructure(SubscriptionSetup subscriptionSetup) {
        GeneralMessageRequestStructure gmRequest = new GeneralMessageRequestStructure();
        gmRequest.setRequestTimestamp(ZonedDateTime.now());
        gmRequest.setVersion(SIRI_VERSION);
        gmRequest.setMessageIdentifier(createMessageIdentifier());

        return gmRequest;
    }

    private static FacilityMonitoringRequestStructure createFacilityMonitoringRequestStructure(SubscriptionSetup subscriptionSetup) {
        FacilityMonitoringRequestStructure fmRequest = new FacilityMonitoringRequestStructure();
        fmRequest.setRequestTimestamp(ZonedDateTime.now());
        fmRequest.setVersion(SIRI_VERSION);
        fmRequest.setMessageIdentifier(createMessageIdentifier());
        return fmRequest;
    }

    private static SubscriptionRequest createSituationExchangeSubscriptionRequest(String requestorRef, String subscriptionId, Duration heartbeatInterval, String address, Duration subscriptionDuration, Map<Class, Set<Object>> filterMap, String addressFieldName, Boolean incrementalUpdates, Duration previewInterval) {
        SubscriptionRequest request = createSubscriptionRequest(requestorRef, heartbeatInterval, address, addressFieldName);

        SituationExchangeRequestStructure sxRequest = createSituationExchangeRequestStructure(null);

        if (previewInterval != null) {
            sxRequest.setPreviewInterval(createDataTypeFactory().newDuration(previewInterval.toString()));
        }

        if (filterMap != null) {
            Set<Object> vehicleRefs = filterMap.get(VehicleRef.class);
            if (vehicleRefs != null && !vehicleRefs.isEmpty()) {
                Object next = vehicleRefs.iterator().next();
                if (next instanceof VehicleRef)  {
                    sxRequest.setVehicleRef((VehicleRef) next);
                }
            }
        }

        SituationExchangeSubscriptionStructure sxSubscriptionReq = new SituationExchangeSubscriptionStructure();
        sxSubscriptionReq.setSituationExchangeRequest(sxRequest);
        sxSubscriptionReq.setSubscriptionIdentifier(createSubscriptionIdentifier(subscriptionId));
        sxSubscriptionReq.setInitialTerminationTime(ZonedDateTime.now().plusSeconds(subscriptionDuration.getSeconds()));
        sxSubscriptionReq.setSubscriberRef(request.getRequestorRef());

        sxSubscriptionReq.setIncrementalUpdates(incrementalUpdates);

        request.getSituationExchangeSubscriptionRequests().add(sxSubscriptionReq);

        return request;
    }

    private static SubscriptionRequest createVehicleMonitoringSubscriptionRequest(String requestorRef, String subscriptionId, Duration heartbeatInterval, String address, Duration subscriptionDuration, Map<Class, Set<Object>> filterMap,
                                                                                  Duration updateInterval, Duration changeBeforeUpdates, String addressFieldName, Boolean incrementalUpdates, String vehicleMonitoringRefValue, String lineRefValue) {
        SubscriptionRequest request = createSubscriptionRequest(requestorRef,heartbeatInterval, address, addressFieldName);

        VehicleMonitoringRequestStructure vmRequest = new VehicleMonitoringRequestStructure();
        vmRequest.setRequestTimestamp(ZonedDateTime.now());
        vmRequest.setVersion(SIRI_VERSION);

        if (vehicleMonitoringRefValue != null) {
            VehicleMonitoringRefStructure vehicleMonitoringRef = new VehicleMonitoringRefStructure();
            vehicleMonitoringRef.setValue(vehicleMonitoringRefValue);
            vmRequest.setVehicleMonitoringRef(vehicleMonitoringRef);
        }

        if (lineRefValue != null){
            LineRef lineRef = new LineRef();
            lineRef.setValue(lineRefValue);
            vmRequest.setLineRef(lineRef);
        }

        if (filterMap != null) {
            Set lineRefs = filterMap.get(LineRef.class);
            if (lineRefs != null && !lineRefs.isEmpty()) {
                Object next = lineRefs.iterator().next();
                if (next instanceof LineRef) {
                    vmRequest.setLineRef((LineRef) next);
                }
            }
            Set<Object> vehicleRefs = filterMap.get(VehicleRef.class);
            if (vehicleRefs != null && !vehicleRefs.isEmpty()) {
                Object next = vehicleRefs.iterator().next();
                if (next instanceof VehicleRef)  {
                    vmRequest.setVehicleRef((VehicleRef) next);
                }
            }
        }

        VehicleMonitoringSubscriptionStructure vmSubscriptionReq = new VehicleMonitoringSubscriptionStructure();
        vmSubscriptionReq.setVehicleMonitoringRequest(vmRequest);
        vmSubscriptionReq.setSubscriptionIdentifier(createSubscriptionIdentifier(subscriptionId));
        vmSubscriptionReq.setInitialTerminationTime(ZonedDateTime.now().plusSeconds(subscriptionDuration.getSeconds()));
        vmSubscriptionReq.setSubscriberRef(request.getRequestorRef());

        if (updateInterval != null) {
            //Requesting updates every second
            vmSubscriptionReq.setUpdateInterval(createDataTypeFactory().newDuration(updateInterval.toString()));
        }
        vmSubscriptionReq.setIncrementalUpdates(incrementalUpdates);
        if (changeBeforeUpdates != null) {
            vmSubscriptionReq.setChangeBeforeUpdates(createDataTypeFactory().newDuration(changeBeforeUpdates.toString()));
        }

        request.getVehicleMonitoringSubscriptionRequests().add(vmSubscriptionReq);

        return request;
    }
    private static SubscriptionRequest createGeneralMessageSubscriptionRequest(String requestorRef, String subscriptionId, Duration heartbeatInterval, String address,
                                                                               Duration subscriptionDuration,     String addressFieldName ) {


        SubscriptionRequest request = createSubscriptionRequest(requestorRef, heartbeatInterval, address, addressFieldName);
        GeneralMessageRequestStructure gmRequest = new GeneralMessageRequestStructure();

        gmRequest.setRequestTimestamp(ZonedDateTime.now());
        gmRequest.setVersion(SIRI_VERSION);



        GeneralMessageSubscriptionStructure gmSubscriptionReq = new GeneralMessageSubscriptionStructure();
        gmSubscriptionReq.setGeneralMessageRequest(gmRequest);

        gmSubscriptionReq.setSubscriptionIdentifier(createSubscriptionIdentifier(subscriptionId));
        gmSubscriptionReq.setInitialTerminationTime(ZonedDateTime.now().plusSeconds(subscriptionDuration.getSeconds()));
        gmSubscriptionReq.setSubscriberRef(request.getRequestorRef());
        request.getGeneralMessageSubscriptionRequests().add(gmSubscriptionReq);
        return request;
    }

    private static SubscriptionRequest createFacilityMonitoringSubscriptionRequest(String requestorRef, String subscriptionId, Duration heartbeatInterval, String address,
                                                                               Duration subscriptionDuration,     String addressFieldName ) {

        SubscriptionRequest request = createSubscriptionRequest(requestorRef, heartbeatInterval, address, addressFieldName);
        FacilityMonitoringRequestStructure fmRequest = new FacilityMonitoringRequestStructure();

        fmRequest.setRequestTimestamp(ZonedDateTime.now());
        fmRequest.setVersion(SIRI_VERSION);

        FacilityMonitoringSubscriptionStructure fmSubscriptionReq = new FacilityMonitoringSubscriptionStructure();
        fmSubscriptionReq.setFacilityMonitoringRequest(fmRequest);

        fmSubscriptionReq.setSubscriptionIdentifier(createSubscriptionIdentifier(subscriptionId));
        fmSubscriptionReq.setInitialTerminationTime(ZonedDateTime.now().plusSeconds(subscriptionDuration.getSeconds()));
        fmSubscriptionReq.setSubscriberRef(request.getRequestorRef());
        request.getFacilityMonitoringSubscriptionRequests().add(fmSubscriptionReq);
        return request;
    }
    private static SubscriptionRequest createStopMonitoringSubscriptionRequest(String requestorRef, String subscriptionId, Duration heartbeatInterval, String address, Duration subscriptionDuration, Map<Class, Set<Object>> filterMap, String addressFieldName, Boolean incrementalUpdates, Duration previewInterval, Duration changeBeforeUpdates, String stopMonitoringRefValue) {
        SubscriptionRequest request = createSubscriptionRequest(requestorRef, heartbeatInterval, address, addressFieldName);

        StopMonitoringRequestStructure smRequest = new StopMonitoringRequestStructure();
        smRequest.setRequestTimestamp(ZonedDateTime.now());
        smRequest.setVersion(SIRI_VERSION);
        if (stopMonitoringRefValue != null) {
            MonitoringRefStructure monitoringRefStructure = new MonitoringRefStructure();
            monitoringRefStructure.setValue(stopMonitoringRefValue);
            smRequest.setMonitoringRef(monitoringRefStructure);
        }

        if (previewInterval != null) {
            smRequest.setPreviewInterval(createDataTypeFactory().newDuration(previewInterval.toString()));
        }

        if (filterMap != null) {
            Set<Object> stopPointsRefs = filterMap.get(MonitoringRefStructure.class);
            if (stopPointsRefs != null && !stopPointsRefs.isEmpty()) {
                Object next = stopPointsRefs.iterator().next();
                if (next instanceof MonitoringRefStructure) {
                    smRequest.setMonitoringRef((MonitoringRefStructure) next);
                }
            }
        }

        StopMonitoringSubscriptionStructure smSubscriptionReq = new StopMonitoringSubscriptionStructure();
        smSubscriptionReq.setStopMonitoringRequest(smRequest);
        smSubscriptionReq.setSubscriptionIdentifier(createSubscriptionIdentifier(subscriptionId));
        smSubscriptionReq.setInitialTerminationTime(ZonedDateTime.now().plusSeconds(subscriptionDuration.getSeconds()));
        smSubscriptionReq.setSubscriberRef(request.getRequestorRef());
        if (changeBeforeUpdates != null) {
            smSubscriptionReq.setChangeBeforeUpdates(createDataTypeFactory().newDuration(changeBeforeUpdates.toString()));
        }

        smSubscriptionReq.setIncrementalUpdates(incrementalUpdates);

        request.getStopMonitoringSubscriptionRequests().add(smSubscriptionReq);

        return request;
    }


    private static SubscriptionRequest createEstimatedTimetableSubscriptionRequest(String requestorRef, String subscriptionId, Duration heartbeatInterval, String address, Duration subscriptionDuration, Map<Class, Set<Object>> filterMap, String addressFieldName, Boolean incrementalUpdates, Duration previewInterval, Duration changeBeforeUpdates) {
        SubscriptionRequest request = createSubscriptionRequest(requestorRef, heartbeatInterval, address, addressFieldName);

        EstimatedTimetableRequestStructure etRequest = new EstimatedTimetableRequestStructure();
        etRequest.setRequestTimestamp(ZonedDateTime.now());
        etRequest.setVersion(SIRI_VERSION);

        if (previewInterval != null) {
            etRequest.setPreviewInterval(createDataTypeFactory().newDuration(previewInterval.toString()));
        }

        if (filterMap != null) {
            if (filterMap.size() > 0) {

                if (filterMap.containsKey(LineDirectionStructure.class)) {
                    EstimatedTimetableRequestStructure.Lines lines = new EstimatedTimetableRequestStructure.Lines();
                    Set lineRefs = filterMap.get(LineDirectionStructure.class);
                    for (Object lineref : lineRefs) {
                        if (lineref instanceof LineDirectionStructure) {
                            lines.getLineDirections().add((LineDirectionStructure) lineref);
                        }
                    }
                    if (!lines.getLineDirections().isEmpty()) {
                        etRequest.setLines(lines);
                    }
                }

                if (filterMap.containsKey(OperatorRefStructure.class)) {
                    Set<Object> operatorRefs = filterMap.get(OperatorRefStructure.class);
                    for (Object operatorRef : operatorRefs) {
                        if (operatorRef instanceof OperatorRefStructure) {
                            etRequest.getOperatorReves().add((OperatorRefStructure) operatorRef);
                        }
                    }
                }
            }
        }

        EstimatedTimetableSubscriptionStructure etSubscriptionReq = new EstimatedTimetableSubscriptionStructure();
        etSubscriptionReq.setEstimatedTimetableRequest(etRequest);
        etSubscriptionReq.setSubscriptionIdentifier(createSubscriptionIdentifier(subscriptionId));
        etSubscriptionReq.setInitialTerminationTime(ZonedDateTime.now().plusSeconds(subscriptionDuration.getSeconds()));
        etSubscriptionReq.setSubscriberRef(request.getRequestorRef());
        if (changeBeforeUpdates != null) {
            etSubscriptionReq.setChangeBeforeUpdates(createDataTypeFactory().newDuration(changeBeforeUpdates.toString()));
        }

        etSubscriptionReq.setIncrementalUpdates(incrementalUpdates);

        request.getEstimatedTimetableSubscriptionRequests().add(etSubscriptionReq);

        return request;
    }

    private static SubscriptionRequest createSubscriptionRequest(String requestorRef, Duration heartbeatInterval, String address, String addressFieldName) {
        SubscriptionRequest request = new SubscriptionRequest();
        request.setRequestorRef(createRequestorRef(requestorRef));
        request.setMessageIdentifier(createMessageIdentifier(UUID.randomUUID().toString()));

        if (addressFieldName != null && addressFieldName.equalsIgnoreCase("Address")) {
            request.setAddress(address);
        } else {
            request.setConsumerAddress(address);
        }

        request.setRequestTimestamp(ZonedDateTime.now());

        if (heartbeatInterval != null) {
            SubscriptionContextStructure ctx = new SubscriptionContextStructure();
            ctx.setHeartbeatInterval(createDataTypeFactory().newDuration(heartbeatInterval.toString()));

            request.setSubscriptionContext(ctx);
        }
        return request;
    }

    public static Siri createTerminateSubscriptionRequest(SubscriptionSetup subscriptionSetup) {
        if (subscriptionSetup == null) {
            return null;
        }
        return createTerminateSubscriptionRequest(subscriptionSetup.getSubscriptionId(), createRequestorRef(subscriptionSetup.getRequestorRef()));
    }

    private static Siri createTerminateSubscriptionRequest(String subscriptionId, RequestorRef requestorRef) {
        if (requestorRef == null || requestorRef.getValue() == null) {
            logger.warn("RequestorRef cannot be null");
            return null;
        }
        TerminateSubscriptionRequestStructure terminationReq = new TerminateSubscriptionRequestStructure();

        terminationReq.setRequestTimestamp(ZonedDateTime.now());
        terminationReq.getSubscriptionReves().add(createSubscriptionIdentifier(subscriptionId));
        terminationReq.setRequestorRef(requestorRef);
        terminationReq.setMessageIdentifier(createMessageIdentifier(UUID.randomUUID().toString()));

        Siri siri = createSiriObject();
        siri.setTerminateSubscriptionRequest(terminationReq);
        return siri;
    }

    public static RequestorRef createRequestorRef(String value) {
        if(value == null) {
        	value = UUID.randomUUID().toString();
        }
    	RequestorRef requestorRef = new RequestorRef();
        requestorRef.setValue(value);
        return requestorRef;
    }

    private static SubscriptionQualifierStructure createSubscriptionIdentifier(String subscriptionId) {
        SubscriptionQualifierStructure subscriptionRef = new SubscriptionQualifierStructure();
        subscriptionRef.setValue(subscriptionId);
        return subscriptionRef;
    }

    private static MessageQualifierStructure createMessageIdentifier(String value) {
        MessageQualifierStructure msgId = new MessageQualifierStructure();
        msgId.setValue(value);
        return msgId;
    }

    private static MessageQualifierStructure createMessageIdentifier() {
        return createMessageIdentifier(UUID.randomUUID().toString());
    }

    public Siri createSXServiceDelivery(Collection<PtSituationElement> elements) {
        Siri siri = createSiriObject();
        ServiceDelivery delivery = createServiceDelivery();
        SituationExchangeDeliveryStructure deliveryStructure = new SituationExchangeDeliveryStructure();
        SituationExchangeDeliveryStructure.Situations situations = new SituationExchangeDeliveryStructure.Situations();
        situations.getPtSituationElements().addAll(elements);
        deliveryStructure.setSituations(situations);
        deliveryStructure.setResponseTimestamp(ZonedDateTime.now());
        delivery.getSituationExchangeDeliveries().add(deliveryStructure);
        siri.setServiceDelivery(delivery);
        return siri;
    }

    public Siri createVMServiceDelivery(Collection<VehicleActivityStructure> elements) {
        Siri siri = createSiriObject();
        ServiceDelivery delivery = createServiceDelivery();
        VehicleMonitoringDeliveryStructure deliveryStructure = new VehicleMonitoringDeliveryStructure();
        deliveryStructure.setVersion(SIRI_VERSION);
        deliveryStructure.getVehicleActivities().addAll(elements);
        deliveryStructure.setResponseTimestamp(ZonedDateTime.now());
        delivery.getVehicleMonitoringDeliveries().add(deliveryStructure);
        siri.setServiceDelivery(delivery);
        return siri;
    }

    public Siri createFMServiceDelivery(Collection<FacilityConditionStructure> elements) {
        Siri siri = createSiriObject();
        ServiceDelivery delivery = createServiceDelivery();
        FacilityMonitoringDeliveryStructure deliveryStructure = new FacilityMonitoringDeliveryStructure();
        deliveryStructure.setVersion(SIRI_VERSION);
        deliveryStructure.getFacilityConditions().addAll(elements);
        deliveryStructure.setResponseTimestamp(ZonedDateTime.now());
        delivery.getFacilityMonitoringDeliveries().add(deliveryStructure);
        siri.setServiceDelivery(delivery);
        return siri;
    }

    public Siri createETServiceDelivery(Collection<EstimatedVehicleJourney> elements) {
        Siri siri = createSiriObject();
        ServiceDelivery delivery = createServiceDelivery();
        EstimatedTimetableDeliveryStructure deliveryStructure = new EstimatedTimetableDeliveryStructure();
        deliveryStructure.setVersion(SIRI_VERSION);
        EstimatedVersionFrameStructure estimatedVersionFrameStructure = new EstimatedVersionFrameStructure();
        estimatedVersionFrameStructure.setRecordedAtTime(ZonedDateTime.now());
        estimatedVersionFrameStructure.getEstimatedVehicleJourneies().addAll(elements);
        deliveryStructure.getEstimatedJourneyVersionFrames().add(estimatedVersionFrameStructure);
        deliveryStructure.setResponseTimestamp(ZonedDateTime.now());

        delivery.getEstimatedTimetableDeliveries().add(deliveryStructure);
        siri.setServiceDelivery(delivery);
        return siri;
    }

    // TODO MHI
    public Siri createSMServiceDelivery(Collection<MonitoredStopVisit> elements) {
        Siri siri = createSiriObject();
        ServiceDelivery delivery = createServiceDelivery();
        StopMonitoringDeliveryStructure deliveryStructure = new StopMonitoringDeliveryStructure();
        deliveryStructure.setVersion(SIRI_VERSION);
        deliveryStructure.getMonitoredStopVisits().addAll(elements);
        deliveryStructure.setResponseTimestamp(ZonedDateTime.now());
        delivery.getStopMonitoringDeliveries().add(deliveryStructure);
        siri.setServiceDelivery(delivery);
        return siri;
    }

    public Siri createGMServiceDelivery(Collection<GeneralMessage> elements) {
        Siri siri = createSiriObject();
        ServiceDelivery delivery = createServiceDelivery();
        GeneralMessageDeliveryStructure deliveryStructure = new GeneralMessageDeliveryStructure();
        deliveryStructure.setVersion(SIRI_VERSION);
        deliveryStructure.getGeneralMessages().addAll(elements);
        deliveryStructure.setResponseTimestamp(ZonedDateTime.now());
        delivery.getGeneralMessageDeliveries().add(deliveryStructure);
        siri.setServiceDelivery(delivery);
        return siri;
    }


    /**
     * Creates a SIRI response message with all points recovered from discovery service
     *
     * @param elements
     *  the list of stopPoints from discovery service
     * @return
     *  the siri response with all points
     */
    public Siri createStopPointsDiscoveryDelivery(Collection<AnnotatedStopPointStructure> elements) {
        Siri siri = createSiriObject();
        StopPointsDeliveryStructure spDelStruct = new StopPointsDeliveryStructure();
        spDelStruct.getAnnotatedStopPointReves().addAll(elements);
        siri.setStopPointsDelivery(spDelStruct);
        return siri;
    }

    /**
     * Creates a SIRI response message with all lines recovered from discovery service
     *
     * @param elements
     *  the list of lines from discovery service
     * @return
     *  the siri response with all lines
     */
    public Siri createLinesDiscoveryDelivery(Collection<AnnotatedLineRef> elements) {
        Siri siri = createSiriObject();
        LinesDeliveryStructure lineStruct = new LinesDeliveryStructure();
        lineStruct.getAnnotatedLineReves().addAll(elements);
        siri.setLinesDelivery(lineStruct);
        return siri;
    }



    private ServiceDelivery createServiceDelivery() {
        ServiceDelivery delivery = new ServiceDelivery();
        delivery.setResponseTimestamp(ZonedDateTime.now());
        if (configuration != null && configuration.getProducerRef() != null) {
            delivery.setProducerRef(createRequestorRef(configuration.getProducerRef()));
        }
        return delivery;
    }

    private static DatatypeFactory createDataTypeFactory() {
        try {
            return DatatypeFactory.newInstance();
        } catch (DatatypeConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    public Siri createHeartbeatNotification(String requestorRef) {
        Siri siri = createSiriObject();
        HeartbeatNotificationStructure heartbeat = new HeartbeatNotificationStructure();
        heartbeat.setStatus(true);
        heartbeat.setServiceStartedTime(serverStartTime.atZone(ZoneId.systemDefault()));
        heartbeat.setRequestTimestamp(ZonedDateTime.now());
        heartbeat.setProducerRef(createRequestorRef(requestorRef));
        siri.setHeartbeatNotification(heartbeat);
        return siri;
    }

    public Siri createCheckStatusResponse(CheckStatusRequestStructure checkStatusRequest) {
        Siri siri = createSiriObject();
        CheckStatusResponseStructure response = new CheckStatusResponseStructure();
        response.setStatus(true);
        response.setServiceStartedTime(serverStartTime.atZone(ZoneId.systemDefault()));
        response.setShortestPossibleCycle(createDataTypeFactory().newDuration(60000));
        response.setResponseTimestamp(ZonedDateTime.now());
        RequestorRef prodRef = new RequestorRef();
        prodRef.setValue("MOBIITI");
        response.setProducerRef(prodRef);

        MessageQualifierStructure respId = new MessageQualifierStructure();
        respId.setValue(UUID.randomUUID().toString());
        response.setResponseMessageIdentifier(respId);

        if (checkStatusRequest.getMessageIdentifier() != null){
            MessageRefStructure requestRef = new MessageRefStructure();
            requestRef.setValue(checkStatusRequest.getMessageIdentifier().getValue());
            response.setRequestMessageRef(requestRef);
        }

        siri.setCheckStatusResponse(response);
        return siri;
    }

    private static Siri createSiriObject() {
        Siri siri = new Siri();
        siri.setVersion(SIRI_VERSION);
        return siri;
    }

    public Siri createSubscriptionResponse(String subscriptionRef, boolean status, String errorText) {
        Siri siri = createSiriObject();
        SubscriptionResponseStructure response = new SubscriptionResponseStructure();
        response.setServiceStartedTime(serverStartTime.atZone(ZoneId.systemDefault()));
        response.setRequestMessageRef(createMessageIdentifier());
        response.setResponderRef(createRequestorRef(subscriptionRef));
        response.setResponseTimestamp(ZonedDateTime.now());


        ResponseStatus responseStatus = new ResponseStatus();
        responseStatus.setResponseTimestamp(ZonedDateTime.now());
        responseStatus.setRequestMessageRef(createMessageIdentifier());
        responseStatus.setSubscriptionRef(createSubscriptionIdentifier(subscriptionRef));
        responseStatus.setStatus(status);

        if (errorText != null) {
            ServiceDeliveryErrorConditionElement error = new ServiceDeliveryErrorConditionElement();
            OtherErrorStructure otherError = new OtherErrorStructure();
            otherError.setErrorText(errorText);
            error.setOtherError(otherError);
            responseStatus.setErrorCondition(error);
        }

        response.getResponseStatuses().add(responseStatus);

        siri.setSubscriptionResponse(response);
        return siri;
    }

    public Siri createTerminateSubscriptionResponse(String subscriptionRef) {
        Siri siri = createSiriObject();
        TerminateSubscriptionResponseStructure response = new TerminateSubscriptionResponseStructure();
        TerminationResponseStatusStructure status = new TerminationResponseStatusStructure();
        status.setSubscriptionRef(createSubscriptionIdentifier(subscriptionRef));
        status.setResponseTimestamp(ZonedDateTime.now());
        status.setStatus(true);

        response.getTerminationResponseStatuses().add(status);
        siri.setTerminateSubscriptionResponse(response);
        return siri;
    }

    public Siri createDataReadyNotification() {
        Siri siri = createSiriObject();
        DataReadyRequestStructure dataReadyNotification = new DataReadyRequestStructure();
        dataReadyNotification.setRequestTimestamp(ZonedDateTime.now());
        siri.setDataReadyNotification(dataReadyNotification);
        return siri;
    }

    
    /**
     * Creates a deep copy of provided object
     * @param siri
     * @return
     * @throws JAXBException
     */
    public static Siri deepCopy(Siri siri) {
    	Kryo kryo = kryoPool.borrow();
        try {
        	return kryo.copy(siri);
        } finally {
        	kryoPool.release(kryo);
        }
    }

    /**
     * Creates a deep copy of provided object
     * @param estimatedVehicleJourney
     * @return
     * @throws JAXBException
     */
    public static EstimatedVehicleJourney deepCopy(EstimatedVehicleJourney estimatedVehicleJourney) {
    	Kryo kryo = kryoPool.borrow();
        try {
        	return kryo.copy(estimatedVehicleJourney);
        } finally {
        	kryoPool.release(kryo);
        }
    }
}
