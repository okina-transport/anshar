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

package no.rutebanken.anshar.data;

import no.rutebanken.anshar.api.GtfsRTApi;
import no.rutebanken.anshar.config.IncomingSiriParameters;
import no.rutebanken.anshar.helpers.TestObjectFactory;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.mapping.LineUpdaterService;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.io.IOUtils;
import org.junit.Ignore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri21.*;

import jakarta.xml.bind.UnmarshalException;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.*;

import static no.rutebanken.anshar.idTests.TestUtils.*;
import static no.rutebanken.anshar.routes.HttpParameter.INTERNAL_SIRI_DATA_TYPE;
import static no.rutebanken.anshar.routes.HttpParameter.SIRI_VERSION_HEADER_NAME;
import static no.rutebanken.anshar.routes.siri.Siri20RequestHandlerRoute.TRANSFORM_SOAP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;


public class MonitoredStopVisitsTest extends SpringBootBaseTest implements CamelContextAware {


    @Autowired
    private MonitoredStopVisits monitoredStopVisits;

    @Autowired
    private SiriHandler handler;

    @Autowired
    private SubscriptionConfig subscriptionConfig;

    @Autowired
    private LineUpdaterService lineupdaterService;


    @Produce(value = "direct:send.to.external.subscription")
    protected ProducerTemplate sendExternalSubscription;

    @BeforeEach
    public void init() {
        monitoredStopVisits.clearAll();
    }

    private CamelContext camelContext;


    @Produce(value = "direct:enqueue.message")
    protected ProducerTemplate enqueueMessageProducer;


    @Test
    public void testSplitDoubleMessage() throws InterruptedException {

        String msg = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "  <soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
                "    <soapenv:Header/>\n" +
                "    <soapenv:Body>\n" +
                "       <NotifyStopMonitoring xmlns=\"http://wsdl.siri.org.uk\">\n" +
                "          <ServiceDeliveryInfo xmlns=\"\">\n" +
                "             <ResponseTimestamp xmlns=\"http://www.siri.org.uk/siri\">2025-03-24T09:10:38.009402+01:00</ResponseTimestamp>\n" +
                "             <ProducerRef xmlns=\"http://www.siri.org.uk/siri\">OKI</ProducerRef>\n" +
                "          </ServiceDeliveryInfo>\n" +
                "          <Notification xmlns=\"\">\n" +
                "             <siri:StopMonitoringDelivery xmlns:siri=\"http://www.siri.org.uk/siri\" version=\"2.0\">\n" +
                "                <siri:ResponseTimestamp>2025-03-24T09:10:38.009402+01:00</siri:ResponseTimestamp>\n" +
                "                <siri:RequestMessageRef/>\n" +
                "                <MonitoredStopVisit xmlns=\"http://www.siri.org.uk/siri\">\n" +
                "                   <RecordedAtTime>2025-03-24T09:10:37.927094+01:00</RecordedAtTime>\n" +
                "                   <ItemIdentifier>C3-MKSU2-R-2025-03-24T09:10+01:00[Europe/Paris]</ItemIdentifier>\n" +
                "                   <MonitoringRef>FR_NAOLIB:Quay:2458</MonitoringRef>\n" +
                "                   <MonitoredVehicleJourney>\n" +
                "                      <LineRef>NAOLIBORG:Line:C3:LOC</LineRef>\n" +
                "                      <FramedVehicleJourneyRef>\n" +
                "                         <DataFrameRef>any</DataFrameRef>\n" +
                "                         <DatedVehicleJourneyRef>NAOLIBORG:VehicleJourney:36558869-CR_24_25-HS25H1K1-L-Ma-Me-J-00:LOC</DatedVehicleJourneyRef>\n" +
                "                      </FramedVehicleJourneyRef>\n" +
                "                      <VehicleMode>bus</VehicleMode>\n" +
                "                      <DirectionName>R</DirectionName>\n" +
                "                      <DestinationRef>FR_NAOLIB:Quay:4</DestinationRef>\n" +
                "                      <DestinationName xml:lang=\"FR\">Bd de Doulon</DestinationName>\n" +
                "                      <Monitored>true</Monitored>\n" +
                "                      <MonitoredCall>\n" +
                "                         <StopPointRef>FR_NAOLIB:Quay:2458</StopPointRef>\n" +
                "                         <Order>63</Order>\n" +
                "                         <VehicleAtStop>true</VehicleAtStop>\n" +
                "                         <AimedArrivalTime>2025-03-24T09:10:00+01:00</AimedArrivalTime>\n" +
                "                         <ExpectedArrivalTime>2025-03-24T09:11:07+01:00</ExpectedArrivalTime>\n" +
                "                         <ArrivalStatus>delayed</ArrivalStatus>\n" +
                "                         <ArrivalProximityText>Départ proche</ArrivalProximityText>\n" +
                "                         <AimedDepartureTime>2025-03-24T09:10:00+01:00</AimedDepartureTime>\n" +
                "                         <ExpectedDepartureTime>2025-03-24T09:11:07+01:00</ExpectedDepartureTime>\n" +
                "                      </MonitoredCall>\n" +
                "                   </MonitoredVehicleJourney>\n" +
                "                </MonitoredStopVisit>\n" +
                "             </siri:StopMonitoringDelivery>\n" +
                "          </Notification>\n" +
                "          <SiriExtension xmlns=\"\"/>\n" +
                "       </NotifyStopMonitoring>\n" +
                "    </soapenv:Body>\n" +
                "  </soapenv:Envelope>\n" +
                "  <?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "  <soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
                "    <soapenv:Header/>\n" +
                "    <soapenv:Body>\n" +
                "       <NotifyStopMonitoring xmlns=\"http://wsdl.siri.org.uk\">\n" +
                "          <ServiceDeliveryInfo xmlns=\"\">\n" +
                "             <ResponseTimestamp xmlns=\"http://www.siri.org.uk/siri\">2025-03-24T09:10:38.009402+01:00</ResponseTimestamp>\n" +
                "             <ProducerRef xmlns=\"http://www.siri.org.uk/siri\">OKI</ProducerRef>\n" +
                "          </ServiceDeliveryInfo>\n" +
                "          <Notification xmlns=\"\">\n" +
                "             <siri:StopMonitoringDelivery xmlns:siri=\"http://www.siri.org.uk/siri\" version=\"2.0\">\n" +
                "                <siri:ResponseTimestamp>2025-03-24T09:10:38.009402+01:00</siri:ResponseTimestamp>\n" +
                "                <siri:RequestMessageRef/>\n" +
                "                <MonitoredStopVisit xmlns=\"http://www.siri.org.uk/siri\">\n" +
                "                   <RecordedAtTime>2025-03-24T09:10:37.927094+01:00</RecordedAtTime>\n" +
                "                   <ItemIdentifier>C3-MKSU2-R-2025-03-24T09:10+01:00[Europe/Paris]</ItemIdentifier>\n" +
                "                   <MonitoringRef>FR_NAOLIB:Quay:2459</MonitoringRef>\n" +
                "                   <MonitoredVehicleJourney>\n" +
                "                      <LineRef>NAOLIBORG:Line:C3:LOC</LineRef>\n" +
                "                      <FramedVehicleJourneyRef>\n" +
                "                         <DataFrameRef>any</DataFrameRef>\n" +
                "                         <DatedVehicleJourneyRef>NAOLIBORG:VehicleJourney:36558869-CR_24_25-HS25H1K1-L-Ma-Me-J-00:LOC</DatedVehicleJourneyRef>\n" +
                "                      </FramedVehicleJourneyRef>\n" +
                "                      <VehicleMode>bus</VehicleMode>\n" +
                "                      <DirectionName>R</DirectionName>\n" +
                "                      <DestinationRef>FR_NAOLIB:Quay:4</DestinationRef>\n" +
                "                      <DestinationName xml:lang=\"FR\">Bd de Doulon</DestinationName>\n" +
                "                      <Monitored>true</Monitored>\n" +
                "                      <MonitoredCall>\n" +
                "                         <StopPointRef>FR_NAOLIB:Quay:2458</StopPointRef>\n" +
                "                         <Order>63</Order>\n" +
                "                         <VehicleAtStop>true</VehicleAtStop>\n" +
                "                         <AimedArrivalTime>2025-03-24T09:10:00+01:00</AimedArrivalTime>\n" +
                "                         <ExpectedArrivalTime>2025-03-24T09:11:07+01:00</ExpectedArrivalTime>\n" +
                "                         <ArrivalStatus>delayed</ArrivalStatus>\n" +
                "                         <ArrivalProximityText>Départ proche</ArrivalProximityText>\n" +
                "                         <AimedDepartureTime>2025-03-24T09:10:00+01:00</AimedDepartureTime>\n" +
                "                         <ExpectedDepartureTime>2025-03-24T09:11:07+01:00</ExpectedDepartureTime>\n" +
                "                      </MonitoredCall>\n" +
                "                   </MonitoredVehicleJourney>\n" +
                "                </MonitoredStopVisit>\n" +
                "             </siri:StopMonitoringDelivery>\n" +
                "          </Notification>\n" +
                "          <SiriExtension xmlns=\"\"/>\n" +
                "       </NotifyStopMonitoring>\n" +
                "    </soapenv:Body>\n" +
                "  </soapenv:Envelope>";


        Map<String, Object> headers = new HashMap<>();
        headers.put(TRANSFORM_SOAP, TRANSFORM_SOAP);
        headers.put(INTERNAL_SIRI_DATA_TYPE, SiriDataType.STOP_MONITORING);

        enqueueMessageProducer.asyncRequestBodyAndHeaders(enqueueMessageProducer.getDefaultEndpoint(), msg, headers);
    }


    @Test
    public void testSplitDoubleMessage2() throws InterruptedException {

        String msg = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "  <soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
                "    <soapenv:Header/>\n" +
                "    <soapenv:Body>\n" +
                "       <NotifyStopMonitoring xmlns=\"http://wsdl.siri.org.uk\">\n" +
                "          <ServiceDeliveryInfo xmlns=\"\">\n" +
                "             <ResponseTimestamp xmlns=\"http://www.siri.org.uk/siri\">2025-03-24T09:10:38.009402+01:00</ResponseTimestamp>\n" +
                "             <ProducerRef xmlns=\"http://www.siri.org.uk/siri\">OKI</ProducerRef>\n" +
                "          </ServiceDeliveryInfo>\n" +
                "          <Notification xmlns=\"\">\n" +
                "             <siri:StopMonitoringDelivery xmlns:siri=\"http://www.siri.org.uk/siri\" version=\"2.0\">\n" +
                "                <siri:ResponseTimestamp>2025-03-24T09:10:38.009402+01:00</siri:ResponseTimestamp>\n" +
                "                <siri:RequestMessageRef/>\n" +
                "                <MonitoredStopVisit xmlns=\"http://www.siri.org.uk/siri\">\n" +
                "                   <RecordedAtTime>2025-03-24T09:10:37.927094+01:00</RecordedAtTime>\n" +
                "                   <ItemIdentifier>C3-MKSU2-R-2025-03-24T09:10+01:00[Europe/Paris]</ItemIdentifier>\n" +
                "                   <MonitoringRef>FR_NAOLIB:Quay:2458</MonitoringRef>\n" +
                "                   <MonitoredVehicleJourney>\n" +
                "                      <LineRef>NAOLIBORG:Line:C3:LOC</LineRef>\n" +
                "                      <FramedVehicleJourneyRef>\n" +
                "                         <DataFrameRef>any</DataFrameRef>\n" +
                "                         <DatedVehicleJourneyRef>NAOLIBORG:VehicleJourney:36558869-CR_24_25-HS25H1K1-L-Ma-Me-J-00:LOC</DatedVehicleJourneyRef>\n" +
                "                      </FramedVehicleJourneyRef>\n" +
                "                      <VehicleMode>bus</VehicleMode>\n" +
                "                      <DirectionName>R</DirectionName>\n" +
                "                      <DestinationRef>FR_NAOLIB:Quay:4</DestinationRef>\n" +
                "                      <DestinationName xml:lang=\"FR\">Bd de Doulon</DestinationName>\n" +
                "                      <Monitored>true</Monitored>\n" +
                "                      <MonitoredCall>\n" +
                "                         <StopPointRef>FR_NAOLIB:Quay:2458</StopPointRef>\n" +
                "                         <Order>63</Order>\n" +
                "                         <VehicleAtStop>true</VehicleAtStop>\n" +
                "                         <AimedArrivalTime>2025-03-24T09:10:00+01:00</AimedArrivalTime>\n" +
                "                         <ExpectedArrivalTime>2025-03-24T09:11:07+01:00</ExpectedArrivalTime>\n" +
                "                         <ArrivalStatus>delayed</ArrivalStatus>\n" +
                "                         <ArrivalProximityText>Départ proche</ArrivalProximityText>\n" +
                "                         <AimedDepartureTime>2025-03-24T09:10:00+01:00</AimedDepartureTime>\n" +
                "                         <ExpectedDepartureTime>2025-03-24T09:11:07+01:00</ExpectedDepartureTime>\n" +
                "                      </MonitoredCall>\n" +
                "                   </MonitoredVehicleJourney>\n" +
                "                </MonitoredStopVisit>\n" +
                "             </siri:StopMonitoringDelivery>\n" +
                "          </Notification>\n" +
                "          <SiriExtension xmlns=\"\"/>\n" +
                "       </NotifyStopMonitoring>\n" +
                "    </soapenv:Body>\n" +
                "  </soapenv:Envelope>\n" +
                "  <?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "  <soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
                "    <soapenv:Header/>\n" +
                "    <soapenv:Body>\n" +
                "       <NotifyStopMonitoring xmlns=\"http://wsdl.siri.org.uk\">\n" +
                "          <ServiceDeliveryInfo xmlns=\"\">\n" +
                "             <ResponseTimestamp xmlns=\"http://www.siri.org.uk/siri\">2025-03-24T09:10:38.009402+01:00</ResponseTimestamp>\n" +
                "             <ProducerRef xmlns=\"http://www.siri.org.uk/siri\">OKI</ProducerRef>\n" +
                "          </ServiceDeliveryInfo>\n" +
                "          <Notification xmlns=\"\">\n" +
                "             <siri:StopMonitoringDelivery xmlns:siri=\"http://www.siri.org.uk/siri\" version=\"2.0\">\n" +
                "                <siri:ResponseTimestamp>2025-03-24T09:10:38.009402+01:00</siri:ResponseTimestamp>\n" +
                "                <siri:RequestMessageRef/>\n" +
                "                <MonitoredStopVisit xmlns=\"http://www.siri.org.uk/siri\">\n" +
                "                   <RecordedAtTime>2025-03-24T09:10:37.927094+01:00</RecordedAtTime>\n" +
                "                   <ItemIdentifier>C3-MKSU2-R-2025-03-24T09:10+01:00[Europe/Paris]</ItemIdentifier>\n" +
                "                   <MonitoringRef>FR_NAOLIB:Quay:2458</MonitoringRef>\n" +
                "                   <MonitoredVehicleJourney>\n" +
                "                      <LineRef>NAOLIBORG:Line:C3:LOC</LineRef>\n" +
                "                      <FramedVehicleJourneyRef>\n" +
                "                         <DataFrameRef>any</DataFrameRef>\n" +
                "                         <DatedVehicleJourneyRef>NAOLIBORG:VehicleJourney:36558869-CR_24_25-HS25H1K1-L-Ma-Me-J-00:LOC</DatedVehicleJourneyRef>\n" +
                "                      </FramedVehicleJourneyRef>\n" +
                "                      <VehicleMode>bus</VehicleMode>\n" +
                "                      <DirectionName>R</DirectionName>\n" +
                "                      <DestinationRef>FR_NAOLIB:Quay:4</DestinationRef>\n" +
                "                      <DestinationName xml:lang=\"FR\">Bd de Doulon</DestinationName>\n" +
                "                      <Monitored>true</Monitored>\n" +
                "                      <MonitoredCall>\n" +
                "                         <StopPointRef>FR_NAOLIB:Quay:2458</StopPointRef>\n" +
                "                         <Order>63</Order>\n" +
                "                         <VehicleAtStop>true</VehicleAtStop>\n" +
                "                         <AimedArrivalTime>2025-03-24T09:10:00+01:00</AimedArrivalTime>\n" +
                "                         <ExpectedArrivalTime>2025-03-24T09:11:07+01:00</ExpectedArrivalTime>\n" +
                "                         <ArrivalStatus>delayed</ArrivalStatus>\n" +
                "                         <ArrivalProximityText>Départ proche</ArrivalProximityText>\n" +
                "                         <AimedDepartureTime>2025-03-24T09:10:00+01:00</AimedDepartureTime>\n" +
                "                         <ExpectedDepartureTime>2025-03-24T09:11:07+01:00</ExpectedDepartureTime>\n" +
                "                      </MonitoredCall>\n" +
                "                   </MonitoredVehicleJourney>\n" +
                "                </MonitoredStopVisit>\n" +
                "             </siri:StopMonitoringDelivery>\n" +
                "          </Notification>\n" +
                "          <SiriExtension xmlns=\"\"/>\n" +
                "       </NotifyStopMonitoring>\n" +
                "    </soapenv:Body>\n" +
                "  </soapenv:Envelope>";


        Map<String, Object> headers = new HashMap<>();
        headers.put(TRANSFORM_SOAP, TRANSFORM_SOAP);
        headers.put(INTERNAL_SIRI_DATA_TYPE, SiriDataType.STOP_MONITORING);

        enqueueMessageProducer.asyncRequestBodyAndHeaders(enqueueMessageProducer.getDefaultEndpoint(), msg, headers);
    }

    @Test
    public void testEmptyBodyCheck() throws InterruptedException {

        Siri siriToSend = new Siri();
//        ServiceDelivery serviceDelivery = new ServiceDelivery();
//        StopMonitoringDeliveryStructure smstruct = new StopMonitoringDeliveryStructure();
//        MonitoredStopVisitCancellation cancellation = new MonitoredStopVisitCancellation();
//        LineRef lineRef = new LineRef();
//        lineRef.setValue("a");
//        cancellation.setLineRef(lineRef);


//        smstruct.getMonitoredStopVisitCancellations().add(cancellation);
//
//
//        serviceDelivery.getStopMonitoringDeliveries().add(smstruct);
//
//        siriToSend.setServiceDelivery(serviceDelivery);

        MockEndpoint mock = getCamelContext().getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedBodiesReceived("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");

        Map<String, Object> headers = new HashMap<>();

        headers.put("breadcrumbId", MDC.get("camel.breadcrumbId"));
        headers.put("endpoint", "mock:result");
        headers.put("SubscriptionId", "id1");
        headers.put("showBody", false);
        headers.put("datasetId", "dat1");
        headers.put("requestorRef", "reqRef");
        headers.put(SIRI_VERSION_HEADER_NAME, "2.1");
        headers.put(TRANSFORM_SOAP, TRANSFORM_SOAP);

        sendExternalSubscription.sendBodyAndHeaders(siriToSend, headers);
        //    mock.assertIsSatisfied();
    }

    @Test
    public void testAddMonitoredStopVisit() {
        int previousSize = monitoredStopVisits.getAll().size();
        MonitoredStopVisit element = createMonitoredStopVisit(
                ZonedDateTime.now().plusMinutes(1), UUID.randomUUID().toString());

        monitoredStopVisits.add("test", element);
        assertEquals(previousSize + 1, monitoredStopVisits.getAll().size(), "Vehicle not added");
    }

    @Test
    public void testNullStopReference() {
        int previousSize = monitoredStopVisits.getAll().size();

        monitoredStopVisits.add("test", null);
        assertEquals(previousSize, monitoredStopVisits.getAll().size(), "Null-element added");
    }

    @Test
    public void testFlexibleLineConversion() throws UnmarshalException {
        String flexibleLineId = "PROV1:Line:35";
        String standardlineId = "PROV2:Line:AAA";

        List<GtfsRTApi> gtfsApis = new ArrayList<>();
        GtfsRTApi api1 = new GtfsRTApi();
        api1.setDatasetId("PROV1");
        GtfsRTApi api2 = new GtfsRTApi();
        api2.setDatasetId("PROV2");
        gtfsApis.add(api1);
        gtfsApis.add(api2);

        subscriptionConfig.setGtfsRTApis(gtfsApis);

        Map<String, Boolean> flexibleLineMap = new HashMap<>();
        flexibleLineMap.put(flexibleLineId, true);
        flexibleLineMap.put(standardlineId, false);
        lineupdaterService.addFlexibleLines(flexibleLineMap);

        String datasetId = "DATASET1";


        MonitoredStopVisit sm1 = TestObjectFactory.createMonitoredStopVisit(ZonedDateTime.now().plusMinutes(1), "aa");
        addLineRef(sm1, standardlineId);

        MonitoredStopVisit sm2 = TestObjectFactory.createMonitoredStopVisit(ZonedDateTime.now().plusMinutes(1), "aa");
        addLineRef(sm2, flexibleLineId);

        monitoredStopVisits.add(datasetId, sm1);
        monitoredStopVisits.add(datasetId, sm2);


        Collection<MonitoredStopVisit> sms = monitoredStopVisits.getAll();
        assertFalse(sms.isEmpty());


        String stringXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:ns2=\"http://www.ifopt.org.uk/acsb\" xmlns:ns3=\"http://www.ifopt.org.uk/ifopt\" xmlns:ns4=\"http://datex2.eu/schema/2_0RC1/2_0\" version=\"2.0\">\n" +
                "    <ServiceRequest>\n" +
                "        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>\n" +
                "        <StopMonitoringRequest version=\"2.0\">\n" +
                "        </StopMonitoringRequest>\n" +
                "    </ServiceRequest>\n" +
                "</Siri>";


        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);


        IncomingSiriParameters params = new IncomingSiriParameters();
        params.setIncomingSiriStream(xml);
        params.setDatasetId("DATASET1");
        params.setOutboundIdMappingPolicy(SiriHandler.getIdMappingPolicy("true", "false"));
        params.setMaxSize(-1);


        Siri response = handler.handleIncomingSiri(params);
        Assertions.assertNotNull(response);
        Assertions.assertNotNull(response.getServiceDelivery());
        assertFalse(response.getServiceDelivery().getStopMonitoringDeliveries().isEmpty());
        Assertions.assertFalse(response.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().get(0).getMonitoringRef() == null);
    }

    private void addLineRef(MonitoredStopVisit sm1, String lineId) {
        LineRef lineRef = new LineRef();
        lineRef.setValue(lineId);
        sm1.getMonitoredVehicleJourney().setLineRef(lineRef);
    }

    @Test
    public void testUpdatedMonitoredStopvisit() {
        int previousSize = monitoredStopVisits.getAll().size();

        //Add element
        String stopReference = UUID.randomUUID().toString();
        String itempIdentifier = UUID.randomUUID().toString();
        MonitoredStopVisit element = createMonitoredStopVisit(ZonedDateTime.now().plusMinutes(1), stopReference, itempIdentifier);

        monitoredStopVisits.add("test", element);
        //Verify that element is added
        assertEquals(previousSize + 1, monitoredStopVisits.getAll().size());

        //Update element
        MonitoredStopVisit element2 = createMonitoredStopVisit(ZonedDateTime.now().plusMinutes(1), stopReference, itempIdentifier);

        MonitoredStopVisit updatedMonitoredStopVisit = monitoredStopVisits.add("test", element2);

        //Verify that activity is found as updated
        assertNotNull(updatedMonitoredStopVisit);
        //Verify that existing element is updated
        assertEquals(previousSize + 1, monitoredStopVisits.getAll().size());

        //Add brand new element
        MonitoredStopVisit element3 = createMonitoredStopVisit(
                ZonedDateTime.now().plusMinutes(1), UUID.randomUUID().toString(), UUID.randomUUID().toString());

        updatedMonitoredStopVisit = monitoredStopVisits.add("test", element3);

        //Verify that activity is found as new
        assertNotNull(updatedMonitoredStopVisit);
        //Verify that new element is added
        assertEquals(previousSize + 2, monitoredStopVisits.getAll().size());

        monitoredStopVisits.add("test2", element3);
        //Verify that new element is added
        assertEquals(previousSize + 3, monitoredStopVisits.getAll().size());

        //Verify that element added is vendor-specific
        assertEquals(previousSize + 2, monitoredStopVisits.getAll("test").size());
    }


    @Test
    public void testUpdatedMonitoredStopvisitCancellation() {
        int previousSize = monitoredStopVisits.getAll().size();

        //Add element
        String stopReference = UUID.randomUUID().toString();
        String itempIdentifier = UUID.randomUUID().toString();
        String itemIdentifier2 = UUID.randomUUID().toString();
        String tripId = UUID.randomUUID().toString();
        String routeId = UUID.randomUUID().toString();
        MonitoredStopVisit element = createMonitoredStopVisit(ZonedDateTime.now(), stopReference, itempIdentifier);
        monitoredStopVisits.add("test", element);
        //Verify that element is added
        assertEquals(previousSize + 1, monitoredStopVisits.getAll().size());

        //Update element
        MonitoredStopVisitCancellation elementCancelled = createMonitoredStopVisitCancellation(ZonedDateTime.now(), stopReference, itempIdentifier);

        monitoredStopVisits.cancelStopVsits("test", List.of(elementCancelled));

        assertEquals(previousSize, monitoredStopVisits.getAll().size());

        MonitoredStopVisit element2 = createMonitoredCompleteStopVisit(ZonedDateTime.now().plusMinutes(1), stopReference, tripId, itemIdentifier2, routeId);
        monitoredStopVisits.add("test2", element2);
        //Verify that element is added
        assertEquals(previousSize + 1, monitoredStopVisits.getAll().size());

        //Update element
        MonitoredStopVisitCancellation elementCancelled2 = createMonitoredStopVisitCompleteCancellation(ZonedDateTime.now().plusMinutes(1), stopReference, tripId, itemIdentifier2, routeId);

        monitoredStopVisits.cancelStopVsits("test2", List.of(elementCancelled2));

        //Verify that existing element is updated
        assertEquals(previousSize, monitoredStopVisits.getAll().size());
    }


    @Test
    public void testUpdatedMonitoredStopvisitWithMonitoredChanges() {
        int previousSize = monitoredStopVisits.getAll().size();

        //Add element
        String stopReference = UUID.randomUUID().toString();
        String itempIdentifier = UUID.randomUUID().toString();
        ZonedDateTime arrivalTime = ZonedDateTime.now().plusMinutes(2);


        //First call is created with monitored status  : false
        MonitoredStopVisit element = createMonitoredStopVisit(arrivalTime, stopReference, itempIdentifier);
        element.getMonitoredVehicleJourney().setMonitored(false);
        MonitoredCallStructure monitoredCall = element.getMonitoredVehicleJourney().getMonitoredCall();
        monitoredCall.setAimedArrivalTime(arrivalTime);
        monitoredCall.setExpectedArrivalTime(arrivalTime);
        monitoredCall.setAimedDepartureTime(arrivalTime);
        monitoredCall.setExpectedDepartureTime(arrivalTime);
        monitoredCall.setDepartureStatus(CallStatusEnumeration.ON_TIME);

        monitoredStopVisits.add("test", element);
        //Verify that element is added
        assertEquals(previousSize + 1, monitoredStopVisits.getAll().size());

        //Update element
        //second call with exactly same hours but monitored status : true
        //First call is created with monitored status  : false
        MonitoredStopVisit element2 = createMonitoredStopVisit(arrivalTime, stopReference, itempIdentifier);

        MonitoredCallStructure monitoredCall2 = element2.getMonitoredVehicleJourney().getMonitoredCall();
        element2.getMonitoredVehicleJourney().setMonitored(true);
        monitoredCall2.setAimedArrivalTime(arrivalTime);
        monitoredCall2.setExpectedArrivalTime(arrivalTime);
        monitoredCall2.setAimedDepartureTime(arrivalTime);
        monitoredCall2.setExpectedDepartureTime(arrivalTime);
        monitoredCall2.setDepartureStatus(CallStatusEnumeration.ON_TIME);

        MonitoredStopVisit updatedMonitoredStopVisit = monitoredStopVisits.add("test", element2);

        //Verify that activity is found as updated
        assertNotNull(updatedMonitoredStopVisit);
        //Verify that existing element is updated
        assertEquals(previousSize + 1, monitoredStopVisits.getAll().size());

        Collection<MonitoredStopVisit> allVisits = monitoredStopVisits.getAll();
        MonitoredStopVisit first = allVisits.iterator().next();

        //After second call, monitored status should be changed to true
        assertTrue(first.getMonitoredVehicleJourney().isMonitored());


    }

    @Test
    void testExcludeTheoreticalDataMonitoredChanges() {
        String dataset = "test";
        String stopReference1 = UUID.randomUUID().toString();
        String itemIdentifier1 = UUID.randomUUID().toString();
        ZonedDateTime arrivalTime1 = ZonedDateTime.now().plusMinutes(2);

        MonitoredStopVisit element1 = createMonitoredStopVisit(arrivalTime1, stopReference1, itemIdentifier1);
        element1.getMonitoredVehicleJourney().setMonitored(false);

        MonitoredStopVisit element2 = createMonitoredStopVisit(arrivalTime1, stopReference1 + "duplicate", itemIdentifier1 + "duplicate");
        element2.getMonitoredVehicleJourney().setMonitored(true);

        monitoredStopVisits.add(dataset, element1);
        monitoredStopVisits.add(dataset, element2);

        Siri serviceDelivery = monitoredStopVisits.createServiceDelivery("test", dataset, Collections.emptyList(), 15000, -1, Collections.emptySet(), Collections.emptySet(), "messageId", true);

        assertThat(serviceDelivery.getServiceDelivery().getStopMonitoringDeliveries().getFirst().getMonitoredStopVisits()).hasSize(1);
        assertThat(serviceDelivery.getServiceDelivery().getStopMonitoringDeliveries().getFirst().getMonitoredStopVisits().getFirst().getItemIdentifier()).isEqualTo(itemIdentifier1 + "duplicate");
    }

    @Test
    void testCreateServiceDeliveryWithLineRefFiltering() {
        String dataset = "test-dataset";
        String stopReference = "FR_NAOLIB:Quay:2458";
        String lineTarget = "LINE:TARGET";
        String lineOther = "LINE:OTHER";

        ZonedDateTime arrivalTime = ZonedDateTime.now().plusMinutes(10);

        // Création d'un passage pour la ligne CIBLÉE
        MonitoredStopVisit elementTarget = createMonitoredStopVisit(arrivalTime, stopReference, "ID-TARGET");
        addLineRef(elementTarget, lineTarget);
        elementTarget.getMonitoredVehicleJourney().setMonitored(true);

        // Création d'un passage pour une AUTRE ligne (sur le même arrêt)
        MonitoredStopVisit elementOther = createMonitoredStopVisit(arrivalTime, stopReference, "ID-OTHER");
        addLineRef(elementOther, lineOther);
        elementOther.getMonitoredVehicleJourney().setMonitored(true);

        monitoredStopVisits.add(dataset, elementTarget);
        monitoredStopVisits.add(dataset, elementOther);

        Set<String> searchedLines = Set.of(lineTarget);

        Siri serviceDelivery = monitoredStopVisits.createServiceDelivery("test-requestor", dataset, Collections.emptyList(), 100, -1, Set.of(stopReference), searchedLines, "msg-123", false);

        List<MonitoredStopVisit> results = serviceDelivery.getServiceDelivery()
                .getStopMonitoringDeliveries().getFirst().getMonitoredStopVisits();

        // On vérifie qu'on n'a qu'un seul résultat (la ligne Target) et pas deux
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getItemIdentifier()).isEqualTo("ID-TARGET");
        assertThat(results.getFirst().getMonitoredVehicleJourney().getLineRef().getValue()).isEqualTo(lineTarget);

        // Si on demande une ligne qui n'existe pas, la liste doit être vide
        Siri emptyDelivery = monitoredStopVisits.createServiceDelivery(
                "test", dataset, Collections.emptyList(), 100, -1,
                Set.of(stopReference), Set.of("LINE:UNKNOWN"), "msg-456", false);

        assertThat(emptyDelivery.getServiceDelivery().getStopMonitoringDeliveries().getFirst().getMonitoredStopVisits()).isEmpty();
    }


    @Test
    @Ignore
    public void testExcludeDatasetIds() {

        String prefix = "excludedOnly-";

        MonitoredStopVisit activity_1 = createMonitoredStopVisit(ZonedDateTime.now(), prefix + "1234");
        activity_1.getMonitoredVehicleJourney().setDataSource("test1");
        monitoredStopVisits.add("test1", activity_1);

        MonitoredStopVisit activity_2 = createMonitoredStopVisit(ZonedDateTime.now(), prefix + "2345");
        activity_2.getMonitoredVehicleJourney().setDataSource("test2");
        monitoredStopVisits.add("test2", activity_2);

        MonitoredStopVisit activity_3 = createMonitoredStopVisit(ZonedDateTime.now(), prefix + "3456");
        activity_3.getMonitoredVehicleJourney().setDataSource("test3");
        monitoredStopVisits.add("test3", activity_3);

        assertExcludedId("test1");
        assertExcludedId("test2");
        assertExcludedId("test3");
    }

    private void assertExcludedId(String excludedDatasetId) {
        Siri serviceDelivery = monitoredStopVisits.createServiceDelivery(null, null, Arrays.asList(excludedDatasetId), 100, -1, new HashSet<>());

        List<MonitoredStopVisit> monitoredStopVisits = serviceDelivery.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits();

        assertEquals(2, monitoredStopVisits.size());
        for (MonitoredStopVisit activity : monitoredStopVisits) {
            assertFalse(activity.getMonitoredVehicleJourney().getDataSource().equals(excludedDatasetId));
        }
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }
}
