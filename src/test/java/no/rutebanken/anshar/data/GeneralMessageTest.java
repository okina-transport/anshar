package no.rutebanken.anshar.data;

import com.hazelcast.scheduledexecutor.IScheduledExecutorService;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.UnmarshalException;
import no.rutebanken.anshar.config.IncomingSiriParameters;
import no.rutebanken.anshar.data.frGeneralMessageStructure.Content;
import no.rutebanken.anshar.data.util.CustomSiriXml;
import no.rutebanken.anshar.helpers.TestObjectFactory;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.routes.siri.handlers.inbound.SituationExchangeInbound;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import uk.org.siri.siri21.*;

import javax.xml.transform.TransformerException;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class GeneralMessageTest extends SpringBootBaseTest {

    @Autowired
    @Qualifier("getSharedScheduler")
    IScheduledExecutorService sharedScheduler;
    @Autowired
    private GeneralMessages generalMessages;
    @Autowired
    private GeneralMessagesCancellations generalMessagesCancellations;
    @Autowired
    private SiriHandler handler;
    @Autowired
    private SituationExchangeInbound situationExchangeInbound;
    @Autowired
    private Situations situations;

    @BeforeEach
    public void init() {
        generalMessages.clearAll();
    }

    @Test
    public void testAddNull() {
        int previousSize = generalMessages.getAll().size();
        generalMessages.add("test", null);
        assertEquals(previousSize, generalMessages.getAll().size());
    }

    @Test
    public void testAddGeneralMessage() {
        int previousSize = generalMessages.getAll().size();
        GeneralMessage msg = TestObjectFactory.createGeneralMessage();
        generalMessages.add("test", msg);
        assertEquals(generalMessages.getAll().size(), previousSize + 1);
    }

    @Test
    public void test_empty_affect_is_converted() throws InterruptedException {
        generalMessages.clearAll();
        String datasetId = "test";
        List<PtSituationElement> incomingSituations = new ArrayList<>();
        PtSituationElement newOpensituation = new PtSituationElement();
        SituationNumber sitNumber = new SituationNumber();
        sitNumber.setValue("SIT1");
        newOpensituation.setSituationNumber(sitNumber);
        newOpensituation.setProgress(WorkflowStatusEnumeration.OPEN);
        HalfOpenTimestampOutputRangeStructure publicationWindow = new HalfOpenTimestampOutputRangeStructure();
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime nowPlusOne = now.minusDays(1);
        publicationWindow.setStartTime(nowPlusOne);
        publicationWindow.setEndTime(nowPlusOne.plusHours(2));
        newOpensituation.getPublicationWindows().add(publicationWindow);
        incomingSituations.add(newOpensituation);
        situationExchangeInbound.ingestSituations(datasetId, incomingSituations, false);
        Assertions.assertEquals(1, generalMessages.getAll().size());
    }

    @Test
    public void test_affectedVehiclejourney_only_is_rejected() throws InterruptedException {
        generalMessages.clearAll();
        String datasetId = "test";
        List<PtSituationElement> incomingSituations = new ArrayList<>();
        PtSituationElement newOpensituation = new PtSituationElement();
        SituationNumber sitNumber = new SituationNumber();
        sitNumber.setValue("SIT1");
        newOpensituation.setSituationNumber(sitNumber);
        newOpensituation.setProgress(WorkflowStatusEnumeration.OPEN);
        HalfOpenTimestampOutputRangeStructure publicationWindow = new HalfOpenTimestampOutputRangeStructure();
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime nowPlusOne = now.minusDays(1);
        publicationWindow.setStartTime(nowPlusOne);
        publicationWindow.setEndTime(nowPlusOne.plusHours(2));
        newOpensituation.getPublicationWindows().add(publicationWindow);
        AffectsScopeStructure affectsScopeStructure = new AffectsScopeStructure();
        AffectsScopeStructure.VehicleJourneys affectedVJ = new AffectsScopeStructure.VehicleJourneys();
        AffectedVehicleJourneyStructure affectedVehicle = new AffectedVehicleJourneyStructure();
        FramedVehicleJourneyRefStructure framedVJ = new FramedVehicleJourneyRefStructure();
        framedVJ.setDatedVehicleJourneyRef("testVJ");
        affectedVehicle.setFramedVehicleJourneyRef(framedVJ);
        affectedVJ.getAffectedVehicleJourneies().add(affectedVehicle);
        affectsScopeStructure.setVehicleJourneys(affectedVJ);
        newOpensituation.setAffects(affectsScopeStructure);
        incomingSituations.add(newOpensituation);
        situationExchangeInbound.ingestSituations(datasetId, incomingSituations, false);
        Assertions.assertEquals(0, generalMessages.getAll().size());
    }

    @Test
    public void test_affectedVehicle_only_is_rejected() throws InterruptedException {
        generalMessages.clearAll();
        String datasetId = "test";
        List<PtSituationElement> incomingSituations = new ArrayList<>();
        PtSituationElement newOpensituation = new PtSituationElement();
        SituationNumber sitNumber = new SituationNumber();
        sitNumber.setValue("SIT1");
        newOpensituation.setSituationNumber(sitNumber);
        newOpensituation.setProgress(WorkflowStatusEnumeration.OPEN);
        HalfOpenTimestampOutputRangeStructure publicationWindow = new HalfOpenTimestampOutputRangeStructure();
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime nowPlusOne = now.minusDays(1);
        publicationWindow.setStartTime(nowPlusOne);
        publicationWindow.setEndTime(nowPlusOne.plusHours(2));
        newOpensituation.getPublicationWindows().add(publicationWindow);
        AffectsScopeStructure affectsScopeStructure = new AffectsScopeStructure();
        AffectsScopeStructure.Vehicles vehicles = new AffectsScopeStructure.Vehicles();
        AffectedVehicleStructure affectedVehicle = new AffectedVehicleStructure();
        VehicleRef vehicleRef = new VehicleRef();
        vehicleRef.setValue("testVehicle");
        affectedVehicle.setVehicleRef(vehicleRef);
        vehicles.getAffectedVehicles().add(affectedVehicle);
        affectsScopeStructure.setVehicles(vehicles);
        newOpensituation.setAffects(affectsScopeStructure);
        incomingSituations.add(newOpensituation);
        situationExchangeInbound.ingestSituations(datasetId, incomingSituations, false);
        Assertions.assertEquals(0, generalMessages.getAll().size());
    }

    @Test
    public void test_affectedLine_only_is_accepted() throws InterruptedException {
        generalMessages.clearAll();
        String datasetId = "test";
        List<PtSituationElement> incomingSituations = new ArrayList<>();
        PtSituationElement newOpensituation = new PtSituationElement();
        SituationNumber sitNumber = new SituationNumber();
        sitNumber.setValue("SIT1");
        newOpensituation.setSituationNumber(sitNumber);
        newOpensituation.setProgress(WorkflowStatusEnumeration.OPEN);
        HalfOpenTimestampOutputRangeStructure publicationWindow = new HalfOpenTimestampOutputRangeStructure();
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime nowPlusOne = now.minusDays(1);
        publicationWindow.setStartTime(nowPlusOne);
        publicationWindow.setEndTime(nowPlusOne.plusHours(2));
        newOpensituation.getPublicationWindows().add(publicationWindow);

        AffectsScopeStructure affectsScopeStructure = new AffectsScopeStructure();
        AffectsScopeStructure.Networks networks = new AffectsScopeStructure.Networks();
        AffectsScopeStructure.Networks.AffectedNetwork affectedNetwork = new AffectsScopeStructure.Networks.AffectedNetwork();
        AffectedLineStructure affectedLine = new AffectedLineStructure();
        LineRef lineRef = new LineRef();
        lineRef.setValue("testLine");
        affectedLine.setLineRef(lineRef);
        affectedNetwork.getAffectedLines().add(affectedLine);
        networks.getAffectedNetworks().add(affectedNetwork);
        affectsScopeStructure.setNetworks(networks);
        newOpensituation.setAffects(affectsScopeStructure);
        incomingSituations.add(newOpensituation);
        situationExchangeInbound.ingestSituations(datasetId, incomingSituations, false);
        Assertions.assertEquals(1, generalMessages.getAll().size());
    }

    @Test
    public void test_affectedPlace_only_is_rejected() throws InterruptedException {
        generalMessages.clearAll();
        String datasetId = "test";
        List<PtSituationElement> incomingSituations = new ArrayList<>();
        PtSituationElement newOpensituation = new PtSituationElement();
        SituationNumber sitNumber = new SituationNumber();
        sitNumber.setValue("SIT1");
        newOpensituation.setSituationNumber(sitNumber);
        newOpensituation.setProgress(WorkflowStatusEnumeration.OPEN);
        HalfOpenTimestampOutputRangeStructure publicationWindow = new HalfOpenTimestampOutputRangeStructure();
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime nowPlusOne = now.minusDays(1);
        publicationWindow.setStartTime(nowPlusOne);
        publicationWindow.setEndTime(nowPlusOne.plusHours(2));
        newOpensituation.getPublicationWindows().add(publicationWindow);
        AffectsScopeStructure affectsScopeStructure = new AffectsScopeStructure();
        AffectsScopeStructure.Places places = new AffectsScopeStructure.Places();
        AffectedPlaceStructure affectedPlace = new AffectedPlaceStructure();
        affectedPlace.setPlaceRef("aaa");
        places.getAffectedPlaces().add(affectedPlace);
        affectsScopeStructure.setPlaces(places);
        newOpensituation.setAffects(affectsScopeStructure);
        incomingSituations.add(newOpensituation);
        situationExchangeInbound.ingestSituations(datasetId, incomingSituations, false);
        Assertions.assertEquals(0, generalMessages.getAll().size());
    }

    @Test
    public void test_that_publication_window_in_future_are_not_inserted_in_GM_cache() throws InterruptedException {
        String datasetId = "test";


        // Creating situation with publication window that start 1 min in the future
        List<PtSituationElement> incomingSituations = new ArrayList<>();
        PtSituationElement newOpensituation = new PtSituationElement();
        SituationNumber sitNumber = new SituationNumber();
        sitNumber.setValue("SIT1");
        newOpensituation.setSituationNumber(sitNumber);
        newOpensituation.setProgress(WorkflowStatusEnumeration.OPEN);
        HalfOpenTimestampOutputRangeStructure publicationWindow = new HalfOpenTimestampOutputRangeStructure();
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime nowPlusOne = now.plusMinutes(1);
        publicationWindow.setStartTime(nowPlusOne);
        publicationWindow.setEndTime(nowPlusOne.plusHours(2));
        newOpensituation.getPublicationWindows().add(publicationWindow);
        incomingSituations.add(newOpensituation);

        situationExchangeInbound.ingestSituations(datasetId, incomingSituations, false);

        // Checking that General Message has NOT be inserted into GM cache (because publication window is defined in the future)
        Assertions.assertEquals(0, generalMessages.getAll().size());

        // waiting 70s. During this time, GM should have been inserted into cache, by the scheduler
        Thread.sleep(70000);

        // Checking that GM has really been inserted into cache
        Assertions.assertEquals(1, generalMessages.getAll().size());

    }

    @Test
    public void test_that_reprog_gm_window() throws InterruptedException {
        String datasetId = "test";


        // Creating situation with publication window that start 2 hour in the future
        List<PtSituationElement> incomingSituations = new ArrayList<>();
        PtSituationElement newOpensituation = new PtSituationElement();
        SituationNumber sitNumber = new SituationNumber();
        sitNumber.setValue("SIT1");
        newOpensituation.setSituationNumber(sitNumber);
        newOpensituation.setProgress(WorkflowStatusEnumeration.OPEN);
        HalfOpenTimestampOutputRangeStructure publicationWindow = new HalfOpenTimestampOutputRangeStructure();
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime nowPlusOne = now.plusHours(2);
        publicationWindow.setStartTime(nowPlusOne);
        publicationWindow.setEndTime(nowPlusOne.plusHours(2));
        newOpensituation.getPublicationWindows().add(publicationWindow);
        incomingSituations.add(newOpensituation);

        situationExchangeInbound.ingestSituations(datasetId, incomingSituations, false);

        // Checking that General Message has NOT be inserted into GM cache (because publication window is defined in the future)
        Assertions.assertEquals(0, generalMessages.getAll().size());

        // waiting 70s. During this time, GM should have been inserted into cache, by the scheduler
        Thread.sleep(70000);

        // After 70s, gm must be at 0 because publication is 2 hours in the future
        Assertions.assertEquals(0, generalMessages.getAll().size());


        ZonedDateTime now2 = ZonedDateTime.now();
        ZonedDateTime now2PlusOne = now2.plusMinutes(1);
        publicationWindow.setStartTime(now2PlusOne);
        situationExchangeInbound.ingestSituations(datasetId, incomingSituations, false);

        // still empty because publication starts in 1 min
        Assertions.assertEquals(0, generalMessages.getAll().size());

        // waiting 70s. During this time, GM should have been inserted into cache, by the scheduler
        Thread.sleep(70000);


        situationExchangeInbound.cleanupFinishedTasks();


        sharedScheduler.getAllScheduledFutures().values().forEach(list ->
                list.forEach(f -> System.out.println(
                        "==========>   task=" + f.getHandler().getTaskName() +
                                " done=" + f.isDone() +
                                " cancelled=" + f.isCancelled()
                ))
        );

        // GM insert must have been replanned and inserted into cache
        Assertions.assertEquals(1, generalMessages.getAll().size());

    }

    @Test
    public void testUpdate() {


        GeneralMessage msg = TestObjectFactory.createGeneralMessage();
        Content content1 = new Content();
        content1.setStopPointRefs(List.of("stop1"));
        msg.setContent(content1);

        //adding gm with 1 stopRef
        generalMessages.add("test", msg);
        assertEquals(1, generalMessages.getAll().size());

        Siri siri = generalMessages.createServiceDelivery("test", "test", "name", 20, new ArrayList<>());


        Content recoveredContent = getContentFromGeneralMessage(getGeneralMessagesFromSiri(siri).get(0));


        assertEquals(1, recoveredContent.getStopPointRefs().size());
        assertEquals("stop1", recoveredContent.getStopPointRefs().get(0));

        Content content2 = new Content();
        content2.setStopPointRefs(Arrays.asList("stop1", "stop2"));
        msg.setContent(content2);

        //adding an update of the msg with 2 stopRefs
        generalMessages.add("test", msg);
        assertEquals(1, generalMessages.getAll().size());

        siri = generalMessages.createServiceDelivery("test", "test", "name", 20, new ArrayList<>());

        recoveredContent = getContentFromGeneralMessage(getGeneralMessagesFromSiri(siri).get(0));
        assertEquals(2, recoveredContent.getStopPointRefs().size());
        assertEquals("stop1", recoveredContent.getStopPointRefs().get(0));
        assertEquals("stop2", recoveredContent.getStopPointRefs().get(1));

    }

    @Test
    public void test_SOAP_conversion() throws JAXBException, FileNotFoundException, TransformerException {

        String siriString = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:ns2=\"http://www.ifopt.org.uk/acsb\"\n" +
                "      xmlns:ns3=\"http://www.ifopt.org.uk/ifopt\" xmlns:ns4=\"http://datex2.eu/schema/2_0RC1/2_0\"\n" +
                "      xmlns:ns5=\"http://www.opengis.net/gml/3.2\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" version=\"2.1\">\n" +
                "    <ServiceDelivery>\n" +
                "        <ResponseTimestamp>2025-03-19T04:10:17.440566+01:00</ResponseTimestamp>\n" +
                "        <ProducerRef>OKI</ProducerRef>\n" +
                "        <Status>true</Status>\n" +
                "        <GeneralMessageDelivery version=\"2.1\">\n" +
                "            <ResponseTimestamp>2025-03-19T04:10:17.440584+01:00</ResponseTimestamp>\n" +
                "            <GeneralMessage formatRef=\"France\">\n" +
                "                <RecordedAtTime>2024-11-22T13:25:27+01:00</RecordedAtTime>\n" +
                "                <ItemIdentifier>daee04c6-a8cc-11ef-8786-0a58a9feac02</ItemIdentifier>\n" +
                "                <InfoMessageIdentifier>daee04c6-a8cc-11ef-8786-0a58a9feac02</InfoMessageIdentifier>\n" +
                "                <InfoChannelRef>Perturbation</InfoChannelRef>\n" +
                "                <ValidUntilTime>2025-03-28T22:00:00+01:00</ValidUntilTime>\n" +
                "                <SituationRef>\n" +
                "                    <SituationSimpleRef>daee04c6-a8cc-11ef-8786-0a58a9feac02</SituationSimpleRef>\n" +
                "                </SituationRef>\n" +
                "                <ns7:Content xmlns:ns7=\"http://www.siri.org.uk/siri\" xsi:type=\"siri:FrGeneralMessageStructure\">\n" +
                "                    <ns7:LineRef>93</ns7:LineRef>\n" +
                "                    <ns7:StopPointRef>CCDI3</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>CCDI2</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>ARO93</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>BOUE2</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>CCDI1</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>BOUE1</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>BOU72</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>AROR3</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>BOU71</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>CCD99</ns7:StopPointRef>\n" +
                "                    <ns7:Message>\n" +
                "                        <ns7:MessageType>textOnly</ns7:MessageType>\n" +
                "                        <ns7:MessageText/>\n" +
                "                    </ns7:Message>\n" +
                "                </ns7:Content>\n" +
                "                <Extensions>\n" +
                "                    <Alerts>\n" +
                "                        <SendNotifications>true</SendNotifications>\n" +
                "                        <NotificationsDate>2024-11-22T12:25:27Z</NotificationsDate>\n" +
                "                        <AlertMessages>\n" +
                "                            <AlertMessage>\n" +
                "                                <ChannelName>SIC à quai</ChannelName>\n" +
                "                                <ChannelType>beacon</ChannelType>\n" +
                "                                <MessageUsage>start</MessageUsage>\n" +
                "                                <SendNotification>true</SendNotification>\n" +
                "                                <NotificationDate>2024-11-22T12:25:27Z</NotificationDate>\n" +
                "                                <MessageText>Travaux L93 déviée Armor-Internat Laënnec</MessageText>\n" +
                "                            </AlertMessage>\n" +
                "                            <AlertMessage>\n" +
                "                                <ChannelName>SIC à quai</ChannelName>\n" +
                "                                <ChannelType>notification</ChannelType>\n" +
                "                                <MessageUsage>start</MessageUsage>\n" +
                "                                <SendNotification>true</SendNotification>\n" +
                "                                <NotificationDate>2024-11-22T12:25:27Z</NotificationDate>\n" +
                "                                <MessageText>Travaux L93 déviée Armor-Internat Laënnec</MessageText>\n" +
                "                            </AlertMessage>\n" +
                "                            <AlertMessage>\n" +
                "                                <ChannelName>SIC embarqué</ChannelName>\n" +
                "                                <ChannelType>beacon</ChannelType>\n" +
                "                                <MessageUsage>start</MessageUsage>\n" +
                "                                <SendNotification>true</SendNotification>\n" +
                "                                <NotificationDate>2024-11-22T12:25:27Z</NotificationDate>\n" +
                "                                <MessageText>Travaux L93 déviée Armor-Internat Laënnec</MessageText>\n" +
                "                            </AlertMessage>\n" +
                "                        </AlertMessages>\n" +
                "                    </Alerts>\n" +
                "                </Extensions>\n" +
                "            </GeneralMessage>\n" +
                "            <GeneralMessage formatRef=\"France\">\n" +
                "                <RecordedAtTime>2025-01-06T15:41:14+01:00</RecordedAtTime>\n" +
                "                <ItemIdentifier>477ec318-cc3c-11ef-9bf6-0a58a9feac02</ItemIdentifier>\n" +
                "                <InfoMessageIdentifier>477ec318-cc3c-11ef-9bf6-0a58a9feac02</InfoMessageIdentifier>\n" +
                "                <InfoChannelRef>Perturbation</InfoChannelRef>\n" +
                "                <ValidUntilTime>2025-04-06T22:30:00+02:00</ValidUntilTime>\n" +
                "                <SituationRef>\n" +
                "                    <SituationSimpleRef>477ec318-cc3c-11ef-9bf6-0a58a9feac02</SituationSimpleRef>\n" +
                "                </SituationRef>\n" +
                "                <ns7:Content xmlns:ns7=\"http://www.siri.org.uk/siri\" xsi:type=\"siri:FrGeneralMessageStructure\">\n" +
                "                    <ns7:LineRef>81</ns7:LineRef>\n" +
                "                    <ns7:Message>\n" +
                "                        <ns7:MessageType>textOnly</ns7:MessageType>\n" +
                "                        <ns7:MessageText>caramel</ns7:MessageText>\n" +
                "                    </ns7:Message>\n" +
                "                </ns7:Content>\n" +
                "                <Extensions>\n" +
                "                    <Alerts>\n" +
                "                        <SendNotifications>true</SendNotifications>\n" +
                "                        <NotificationsDate>2025-02-21T07:37:27Z</NotificationsDate>\n" +
                "                        <AlertMessages>\n" +
                "                            <AlertMessage>\n" +
                "                                <ChannelName>Titre</ChannelName>\n" +
                "                                <ChannelType>web</ChannelType>\n" +
                "                                <MessageUsage>start</MessageUsage>\n" +
                "                                <NotificationDate>2025-02-21T07:37:27Z</NotificationDate>\n" +
                "                                <MessageText>caramel</MessageText>\n" +
                "                            </AlertMessage>\n" +
                "                        </AlertMessages>\n" +
                "                    </Alerts>\n" +
                "                </Extensions>\n" +
                "            </GeneralMessage>\n" +
                "            <GeneralMessage formatRef=\"France\">\n" +
                "                <RecordedAtTime>2025-02-19T15:14:32+01:00</RecordedAtTime>\n" +
                "                <ItemIdentifier>d6ad0804-eecb-11ef-8f1d-0a58a9feac02</ItemIdentifier>\n" +
                "                <InfoMessageIdentifier>d6ad0804-eecb-11ef-8f1d-0a58a9feac02</InfoMessageIdentifier>\n" +
                "                <InfoChannelRef>Perturbation</InfoChannelRef>\n" +
                "                <ValidUntilTime>2025-03-19T23:12:00+01:00</ValidUntilTime>\n" +
                "                <SituationRef>\n" +
                "                    <SituationSimpleRef>d6ad0804-eecb-11ef-8f1d-0a58a9feac02</SituationSimpleRef>\n" +
                "                </SituationRef>\n" +
                "                <ns7:Content xmlns:ns7=\"http://www.siri.org.uk/siri\" xsi:type=\"siri:FrGeneralMessageStructure\">\n" +
                "                    <ns7:LineRef>1</ns7:LineRef>\n" +
                "                    <ns7:StopPointRef>BDOU2</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>MDOU2</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>LDRE2</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>LDRE1</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>MDOU1</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>BDOU1</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>HBLI2</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>HBLI1</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>MOUT1</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>MOUT2</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>MNFA1</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>SOUI2</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>SOUI1</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>MNFA2</ns7:StopPointRef>\n" +
                "                    <ns7:Message>\n" +
                "                        <ns7:MessageType>textOnly</ns7:MessageType>\n" +
                "                        <ns7:MessageText>Coupure HBL/SOUIL</ns7:MessageText>\n" +
                "                    </ns7:Message>\n" +
                "                </ns7:Content>\n" +
                "                <Extensions>\n" +
                "                    <Alerts>\n" +
                "                        <SendNotifications>true</SendNotifications>\n" +
                "                        <NotificationsDate>2025-02-19T14:14:31Z</NotificationsDate>\n" +
                "                        <AlertMessages>\n" +
                "                            <AlertMessage>\n" +
                "                                <ChannelName>Site web</ChannelName>\n" +
                "                                <ChannelType>web</ChannelType>\n" +
                "                                <MessageUsage>start</MessageUsage>\n" +
                "                                <NotificationDate>2025-02-19T14:14:31Z</NotificationDate>\n" +
                "                                <MessageText>&lt;p&gt;Coupure HBL/SOUIL&lt;/p&gt;</MessageText>\n" +
                "                            </AlertMessage>\n" +
                "                            <AlertMessage>\n" +
                "                                <ChannelName>Titre</ChannelName>\n" +
                "                                <ChannelType>web</ChannelType>\n" +
                "                                <MessageUsage>start</MessageUsage>\n" +
                "                                <NotificationDate>2025-02-19T14:14:31Z</NotificationDate>\n" +
                "                                <MessageText>Coupure HBL/SOUIL</MessageText>\n" +
                "                            </AlertMessage>\n" +
                "                            <AlertMessage>\n" +
                "                                <ChannelName>SIC à quai</ChannelName>\n" +
                "                                <ChannelType>notification</ChannelType>\n" +
                "                                <MessageUsage>start</MessageUsage>\n" +
                "                                <NotificationDate>2025-02-19T14:14:31Z</NotificationDate>\n" +
                "                                <MessageText>Coupure HBL/SOUIL</MessageText>\n" +
                "                            </AlertMessage>\n" +
                "                            <AlertMessage>\n" +
                "                                <ChannelName>SIC embarqué</ChannelName>\n" +
                "                                <ChannelType>notification</ChannelType>\n" +
                "                                <MessageUsage>start</MessageUsage>\n" +
                "                                <NotificationDate>2025-02-19T14:14:31Z</NotificationDate>\n" +
                "                                <MessageText>Coupure HBL/SOUIL</MessageText>\n" +
                "                            </AlertMessage>\n" +
                "                        </AlertMessages>\n" +
                "                    </Alerts>\n" +
                "                </Extensions>\n" +
                "            </GeneralMessage>\n" +
                "            <GeneralMessage formatRef=\"France\">\n" +
                "                <RecordedAtTime>2025-01-06T10:54:48+01:00</RecordedAtTime>\n" +
                "                <ItemIdentifier>43cec3c6-cc14-11ef-bad5-0a58a9feac02</ItemIdentifier>\n" +
                "                <InfoMessageIdentifier>43cec3c6-cc14-11ef-bad5-0a58a9feac02</InfoMessageIdentifier>\n" +
                "                <InfoChannelRef>Perturbation</InfoChannelRef>\n" +
                "                <ValidUntilTime>2025-03-28T23:30:00+01:00</ValidUntilTime>\n" +
                "                <SituationRef>\n" +
                "                    <SituationSimpleRef>43cec3c6-cc14-11ef-bad5-0a58a9feac02</SituationSimpleRef>\n" +
                "                </SituationRef>\n" +
                "                <ns7:Content xmlns:ns7=\"http://www.siri.org.uk/siri\" xsi:type=\"siri:FrGeneralMessageStructure\">\n" +
                "                    <ns7:LineRef>4</ns7:LineRef>\n" +
                "                    <ns7:StopPointRef>JLVE1</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>MVSI2</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>MVSI1</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>JLVE2</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>BGAR1</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>CVER1</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>CTOR2</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>CTOR1</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>CVER2</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>BODO2</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>BODO1</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>BGAR2</ns7:StopPointRef>\n" +
                "                    <ns7:Message>\n" +
                "                        <ns7:MessageType>textOnly</ns7:MessageType>\n" +
                "                        <ns7:MessageText>En raison de Travaux rue des Bourdonnières, la ligne 4 est déviée dans les 2\n" +
                "                            sens entre les arrêts Gréneraie et Maraîchers du lundi 6 janvier au vendredi 28 mars 2025\n" +
                "                            https://naolib.fr/medias/photo/20220719-140510_1736158773994-jpg\n" +
                "                        </ns7:MessageText>\n" +
                "                    </ns7:Message>\n" +
                "                </ns7:Content>\n" +
                "                <Extensions>\n" +
                "                    <Alerts>\n" +
                "                        <SendNotifications>true</SendNotifications>\n" +
                "                        <NotificationsDate>2025-01-06T10:21:17Z</NotificationsDate>\n" +
                "                        <AlertMessages>\n" +
                "                            <AlertMessage>\n" +
                "                                <ChannelName>Site web</ChannelName>\n" +
                "                                <ChannelType>web</ChannelType>\n" +
                "                                <MessageUsage>start</MessageUsage>\n" +
                "                                <NotificationDate>2025-01-06T10:21:17Z</NotificationDate>\n" +
                "                                <MessageText>&lt;p&gt;En raison de Travaux rue des Bourdonnières, la ligne 4 est déviée\n" +
                "                                    dans les 2 sens entre les arrêts Gréneraie et Maraîchers du lundi 6 janvier au\n" +
                "                                    vendredi 28 mars 2025&lt;/p&gt;&lt;p&gt;&amp;nbsp;&lt;/p&gt;&lt;p&gt;&lt;a\n" +
                "                                    href=\"https://naolib.fr/medias/photo/20220719-140510_1736158773994-jpg\"\n" +
                "                                    target=\"_blank\" rel=\"noopener noreferrer\"&gt;https://naolib.fr/medias/photo/20220719-140510_1736158773994-jpg&lt;/a&gt;&lt;/p&gt;&lt;p&gt;&amp;nbsp;&lt;/p&gt;&lt;p&gt;&amp;nbsp;&lt;/p&gt;\n" +
                "                                </MessageText>\n" +
                "                            </AlertMessage>\n" +
                "                            <AlertMessage>\n" +
                "                                <ChannelName>Notifications</ChannelName>\n" +
                "                                <ChannelType>notification</ChannelType>\n" +
                "                                <MessageUsage>start</MessageUsage>\n" +
                "                                <NotificationDate>2025-01-06T10:21:17Z</NotificationDate>\n" +
                "                                <MessageText>&lt;p&gt;Travaux rue des Bourdonnières, ligne 4 déviée dans les 2 sens\n" +
                "                                    entre les arrêts Gréneraie et Maraîchers du lundi 6 janvier au vendredi 28 mars 2025&lt;/p&gt;\n" +
                "                                </MessageText>\n" +
                "                            </AlertMessage>\n" +
                "                            <AlertMessage>\n" +
                "                                <ChannelName>X</ChannelName>\n" +
                "                                <ChannelType>twitter</ChannelType>\n" +
                "                                <MessageUsage>start</MessageUsage>\n" +
                "                                <NotificationDate>2025-01-06T10:21:17Z</NotificationDate>\n" +
                "                                <MessageText>#Infotrafic Travaux rue des Bourdonnières, #Bus4 déviée dans les 2 sens\n" +
                "                                    entre les arrêts Gréneraie et Maraîchers du lundi 6 janvier au vendredi 28 mars 2025\n" +
                "                                </MessageText>\n" +
                "                            </AlertMessage>\n" +
                "                            <AlertMessage>\n" +
                "                                <ChannelName>Titre</ChannelName>\n" +
                "                                <ChannelType>web</ChannelType>\n" +
                "                                <MessageUsage>start</MessageUsage>\n" +
                "                                <NotificationDate>2025-01-06T10:21:17Z</NotificationDate>\n" +
                "                                <MessageText>Travaux rue des Bourdonnières</MessageText>\n" +
                "                            </AlertMessage>\n" +
                "                            <AlertMessage>\n" +
                "                                <ChannelName>SIC à quai</ChannelName>\n" +
                "                                <ChannelType>beacon</ChannelType>\n" +
                "                                <MessageUsage>start</MessageUsage>\n" +
                "                                <NotificationDate>2025-01-06T10:21:17Z</NotificationDate>\n" +
                "                                <MessageText>Travaux 4 déviée entre Gréneraie et Maraîchers</MessageText>\n" +
                "                            </AlertMessage>\n" +
                "                            <AlertMessage>\n" +
                "                                <ChannelName>SIC embarqué</ChannelName>\n" +
                "                                <ChannelType>beacon</ChannelType>\n" +
                "                                <MessageUsage>start</MessageUsage>\n" +
                "                                <NotificationDate>2025-01-06T10:21:17Z</NotificationDate>\n" +
                "                                <MessageText>Travaux 4 déviée entre Gréneraie et Maraîchers</MessageText>\n" +
                "                            </AlertMessage>\n" +
                "                        </AlertMessages>\n" +
                "                    </Alerts>\n" +
                "                </Extensions>\n" +
                "            </GeneralMessage>\n" +
                "            <GeneralMessage formatRef=\"France\">\n" +
                "                <RecordedAtTime>2024-12-18T15:23:23+01:00</RecordedAtTime>\n" +
                "                <ItemIdentifier>a3390e70-bd4b-11ef-bf3a-0a58a9feac02</ItemIdentifier>\n" +
                "                <InfoMessageIdentifier>a3390e70-bd4b-11ef-bf3a-0a58a9feac02</InfoMessageIdentifier>\n" +
                "                <InfoChannelRef>Perturbation</InfoChannelRef>\n" +
                "                <ValidUntilTime>2025-06-28T17:21:00+02:00</ValidUntilTime>\n" +
                "                <SituationRef>\n" +
                "                    <SituationSimpleRef>a3390e70-bd4b-11ef-bf3a-0a58a9feac02</SituationSimpleRef>\n" +
                "                </SituationRef>\n" +
                "                <ns7:Content xmlns:ns7=\"http://www.siri.org.uk/siri\" xsi:type=\"siri:FrGeneralMessageStructure\">\n" +
                "                    <ns7:LineRef>1</ns7:LineRef>\n" +
                "                    <ns7:Message>\n" +
                "                        <ns7:MessageType>textOnly</ns7:MessageType>\n" +
                "                        <ns7:MessageText>infotrafic à En raison de Intempéries Ligne 1 est perturbée comme suit : Ligne\n" +
                "                            1 est coupée entre XXX et YYY\n" +
                "                        </ns7:MessageText>\n" +
                "                    </ns7:Message>\n" +
                "                </ns7:Content>\n" +
                "                <Extensions>\n" +
                "                    <Alerts>\n" +
                "                        <SendNotifications>true</SendNotifications>\n" +
                "                        <NotificationsDate>2024-12-18T14:23:23Z</NotificationsDate>\n" +
                "                        <AlertMessages>\n" +
                "                            <AlertMessage>\n" +
                "                                <ChannelName>Notifications</ChannelName>\n" +
                "                                <ChannelType>notification</ChannelType>\n" +
                "                                <MessageUsage>start</MessageUsage>\n" +
                "                                <SendNotification>true</SendNotification>\n" +
                "                                <NotificationDate>2024-12-18T14:23:23Z</NotificationDate>\n" +
                "                                <MessageText>&lt;p&gt;Intempéries secteur X&lt;/p&gt;</MessageText>\n" +
                "                            </AlertMessage>\n" +
                "                            <AlertMessage>\n" +
                "                                <ChannelName>SIC à quai</ChannelName>\n" +
                "                                <ChannelType>beacon</ChannelType>\n" +
                "                                <MessageUsage>start</MessageUsage>\n" +
                "                                <SendNotification>true</SendNotification>\n" +
                "                                <NotificationDate>2024-12-18T14:23:23Z</NotificationDate>\n" +
                "                                <MessageText>XXX</MessageText>\n" +
                "                            </AlertMessage>\n" +
                "                            <AlertMessage>\n" +
                "                                <ChannelName>SIC à quai</ChannelName>\n" +
                "                                <ChannelType>notification</ChannelType>\n" +
                "                                <MessageUsage>start</MessageUsage>\n" +
                "                                <SendNotification>true</SendNotification>\n" +
                "                                <NotificationDate>2024-12-18T14:23:23Z</NotificationDate>\n" +
                "                                <MessageText>XXX</MessageText>\n" +
                "                            </AlertMessage>\n" +
                "                            <AlertMessage>\n" +
                "                                <ChannelName>Site web</ChannelName>\n" +
                "                                <ChannelType>web</ChannelType>\n" +
                "                                <MessageUsage>start</MessageUsage>\n" +
                "                                <SendNotification>true</SendNotification>\n" +
                "                                <NotificationDate>2024-12-18T14:23:23Z</NotificationDate>\n" +
                "                                <MessageText>&lt;h4&gt;&lt;strong&gt;infotrafic à&amp;nbsp;&lt;/strong&gt;&lt;/h4&gt;&lt;p&gt;&amp;nbsp;&lt;/p&gt;&lt;p&gt;En\n" +
                "                                    raison de &amp;nbsp;Intempéries &amp;nbsp;Ligne 1 est perturbée comme suit :&lt;/p&gt;&lt;p&gt;&amp;nbsp;&lt;/p&gt;&lt;p&gt;&lt;strong&gt;Ligne\n" +
                "                                    1 &lt;/strong&gt;est coupée entre XXX et YYY&lt;/p&gt;&lt;p&gt;&amp;nbsp;&lt;/p&gt;&lt;p&gt;&amp;nbsp;&lt;/p&gt;\n" +
                "                                </MessageText>\n" +
                "                            </AlertMessage>\n" +
                "                            <AlertMessage>\n" +
                "                                <ChannelName>X</ChannelName>\n" +
                "                                <ChannelType>twitter</ChannelType>\n" +
                "                                <MessageUsage>start</MessageUsage>\n" +
                "                                <SendNotification>true</SendNotification>\n" +
                "                                <NotificationDate>2024-12-18T14:23:23Z</NotificationDate>\n" +
                "                                <MessageText>#Infotrafic Intempéries secteur A #Tram1 coupée entre les stations X et Y\n" +
                "                                </MessageText>\n" +
                "                            </AlertMessage>\n" +
                "                            <AlertMessage>\n" +
                "                                <ChannelName>SIC embarqué</ChannelName>\n" +
                "                                <ChannelType>beacon</ChannelType>\n" +
                "                                <MessageUsage>start</MessageUsage>\n" +
                "                                <SendNotification>true</SendNotification>\n" +
                "                                <NotificationDate>2024-12-18T14:23:23Z</NotificationDate>\n" +
                "                                <MessageText>Intempéries L1 coupée entre X et Y</MessageText>\n" +
                "                            </AlertMessage>\n" +
                "                            <AlertMessage>\n" +
                "                                <ChannelName>Titre</ChannelName>\n" +
                "                                <ChannelType>web</ChannelType>\n" +
                "                                <MessageUsage>start</MessageUsage>\n" +
                "                                <SendNotification>true</SendNotification>\n" +
                "                                <NotificationDate>2024-12-18T14:23:23Z</NotificationDate>\n" +
                "                                <MessageText>Intempéries secteur X</MessageText>\n" +
                "                            </AlertMessage>\n" +
                "                        </AlertMessages>\n" +
                "                    </Alerts>\n" +
                "                </Extensions>\n" +
                "            </GeneralMessage>\n" +
                "            <GeneralMessage formatRef=\"France\">\n" +
                "                <RecordedAtTime>2024-11-22T13:21:01+01:00</RecordedAtTime>\n" +
                "                <ItemIdentifier>3c01c104-a8cc-11ef-8786-0a58a9feac02</ItemIdentifier>\n" +
                "                <InfoMessageIdentifier>3c01c104-a8cc-11ef-8786-0a58a9feac02</InfoMessageIdentifier>\n" +
                "                <InfoChannelRef>Perturbation</InfoChannelRef>\n" +
                "                <ValidUntilTime>2025-03-28T22:00:00+01:00</ValidUntilTime>\n" +
                "                <SituationRef>\n" +
                "                    <SituationSimpleRef>3c01c104-a8cc-11ef-8786-0a58a9feac02</SituationSimpleRef>\n" +
                "                </SituationRef>\n" +
                "                <ns7:Content xmlns:ns7=\"http://www.siri.org.uk/siri\" xsi:type=\"siri:FrGeneralMessageStructure\">\n" +
                "                    <ns7:LineRef>93</ns7:LineRef>\n" +
                "                    <ns7:StopPointRef>CCDI3</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>CCDI2</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>ARO93</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>CCDI1</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>BOU72</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>AROR3</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>BOU71</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>OCAN1</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>DMUR1</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>OCAN2</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>CCD99</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>DMUR2</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>BOUE2</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>BOUE1</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>JCAR1</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>JCAR2</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>FMIT7</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>FMI77</ns7:StopPointRef>\n" +
                "                    <ns7:StopPointRef>FMIT4</ns7:StopPointRef>\n" +
                "                    <ns7:Message>\n" +
                "                        <ns7:MessageType>textOnly</ns7:MessageType>\n" +
                "                        <ns7:MessageText>Du lundi 25 novembre 2024 au vendredi 28 mars 2025 En raison de Travaux rue des\n" +
                "                            Piliers de la Chauvinière à St-Herblain, la ligne 93 est déviée dans les 2 sens entre les\n" +
                "                            arrêts Armor et Internat Laënnec. Où prendre votre bus: &gt; vers Hôpital Laënnec - arrêt\n" +
                "                            Cochardières reporté à l'arrêt Cochardières vers Hauts de Coueron et Bobby Sands - arrêt Bio\n" +
                "                            Ouest reporté à l'arrêt provisoire situé rue des Piliers de la Chauvinière &gt; vers Hauts\n" +
                "                            de Couëron et Bobby Sands - arrêt Bio Ouest reporté à l'arrêt provisoire situé rue des\n" +
                "                            Piliers de la Chauvinière 24/838\n" +
                "                        </ns7:MessageText>\n" +
                "                    </ns7:Message>\n" +
                "                </ns7:Content>\n" +
                "                <Extensions>\n" +
                "                    <Alerts>\n" +
                "                        <SendNotifications>true</SendNotifications>\n" +
                "                        <NotificationsDate>2025-01-15T14:26:53Z</NotificationsDate>\n" +
                "                        <AlertMessages>\n" +
                "                            <AlertMessage>\n" +
                "                                <ChannelName>Site web</ChannelName>\n" +
                "                                <ChannelType>web</ChannelType>\n" +
                "                                <MessageUsage>start</MessageUsage>\n" +
                "                                <NotificationDate>2025-01-15T14:26:53Z</NotificationDate>\n" +
                "                                <MessageText>&lt;p&gt;&lt;strong&gt;Du lundi 25 novembre 2024 au vendredi 28 mars 2025&lt;/strong&gt;&lt;/p&gt;&lt;p&gt;En\n" +
                "                                    raison de Travaux rue des Piliers de la Chauvinière à St-Herblain, la ligne 93 est\n" +
                "                                    déviée dans les 2 sens entre les arrêts Armor et Internat Laënnec.&amp;nbsp;&lt;/p&gt;&lt;p&gt;&amp;nbsp;&lt;/p&gt;&lt;p&gt;&lt;strong&gt;Où\n" +
                "                                    prendre votre bus:&lt;/strong&gt;&lt;/p&gt;&lt;p&gt;&amp;gt; &amp;nbsp;vers Hôpital\n" +
                "                                    Laënnec&amp;nbsp;&lt;br&gt;- arrêt Cochardières reporté à l'arrêt Cochardières vers\n" +
                "                                    Hauts de Coueron et Bobby Sands&lt;br&gt;- arrêt Bio Ouest reporté à l'arrêt\n" +
                "                                    provisoire situé rue des Piliers de la Chauvinière&lt;br&gt;&amp;gt; vers Hauts de\n" +
                "                                    Couëron et Bobby Sands&lt;br&gt;- arrêt Bio Ouest reporté à l'arrêt provisoire situé\n" +
                "                                    rue des Piliers de la Chauvinière&lt;/p&gt;&lt;p&gt;&amp;nbsp;&lt;/p&gt;&lt;p&gt;&amp;nbsp;&lt;/p&gt;&lt;p&gt;24/838&lt;/p&gt;&lt;p&gt;&amp;nbsp;&lt;/p&gt;\n" +
                "                                </MessageText>\n" +
                "                            </AlertMessage>\n" +
                "                            <AlertMessage>\n" +
                "                                <ChannelName>BIV Decaux</ChannelName>\n" +
                "                                <ChannelType>notification</ChannelType>\n" +
                "                                <MessageUsage>start</MessageUsage>\n" +
                "                                <NotificationDate>2025-01-15T14:26:53Z</NotificationDate>\n" +
                "                                <MessageText>Travaux L93 déviée Armor - Internat Laënnec jusqu'au vendredi 28 mars\n" +
                "                                    2025\n" +
                "                                </MessageText>\n" +
                "                            </AlertMessage>\n" +
                "                            <AlertMessage>\n" +
                "                                <ChannelName>BIV Decaux</ChannelName>\n" +
                "                                <ChannelType>notification</ChannelType>\n" +
                "                                <MessageUsage>end</MessageUsage>\n" +
                "                                <NotificationDate>2025-01-15T14:26:53Z</NotificationDate>\n" +
                "                                <MessageText>L93 fin de perturbation, retour au parcours normal</MessageText>\n" +
                "                            </AlertMessage>\n" +
                "                            <AlertMessage>\n" +
                "                                <ChannelName>Notifications</ChannelName>\n" +
                "                                <ChannelType>notification</ChannelType>\n" +
                "                                <MessageUsage>start</MessageUsage>\n" +
                "                                <NotificationDate>2025-01-15T14:26:53Z</NotificationDate>\n" +
                "                                <MessageText>&lt;p&gt;Travaux rue des Piliers de la Chauvinière - ST HERBLAIN, ligne 93\n" +
                "                                    déviée dans les 2 sens entre les arrêts Armor et Internat Laënnec du lundi 25\n" +
                "                                    novembre 2024 au vendredi 28 mars 2025.&lt;/p&gt;\n" +
                "                                </MessageText>\n" +
                "                            </AlertMessage>\n" +
                "                            <AlertMessage>\n" +
                "                                <ChannelName>Notifications</ChannelName>\n" +
                "                                <ChannelType>notification</ChannelType>\n" +
                "                                <MessageUsage>end</MessageUsage>\n" +
                "                                <NotificationDate>2025-01-15T14:26:53Z</NotificationDate>\n" +
                "                                <MessageText>&lt;p&gt;L93 Déviation Travaux terminée, retour au parcours normal.&lt;/p&gt;</MessageText>\n" +
                "                            </AlertMessage>\n" +
                "                            <AlertMessage>\n" +
                "                                <ChannelName>X</ChannelName>\n" +
                "                                <ChannelType>twitter</ChannelType>\n" +
                "                                <MessageUsage>start</MessageUsage>\n" +
                "                                <NotificationDate>2025-01-15T14:26:53Z</NotificationDate>\n" +
                "                                <MessageText>#Infotrafic Travaux rue des Piliers de la Chauvinière #Bus 93 dévié dans\n" +
                "                                    les 2 sens entre les arrêts Armor et Internat Laënnec du lundi 25 novembre 2024 au\n" +
                "                                    vendredi 28 mars 2025\n" +
                "                                </MessageText>\n" +
                "                            </AlertMessage>\n" +
                "                            <AlertMessage>\n" +
                "                                <ChannelName>X</ChannelName>\n" +
                "                                <ChannelType>twitter</ChannelType>\n" +
                "                                <MessageUsage>end</MessageUsage>\n" +
                "                                <NotificationDate>2025-01-15T14:26:53Z</NotificationDate>\n" +
                "                                <MessageText>#Infotrafic Fin de perturbation rue des Piliers de la Chauvinière #Bus93\n" +
                "                                    retour au parcours normal dans le secteur\n" +
                "                                </MessageText>\n" +
                "                            </AlertMessage>\n" +
                "                            <AlertMessage>\n" +
                "                                <ChannelName>Titre</ChannelName>\n" +
                "                                <ChannelType>web</ChannelType>\n" +
                "                                <MessageUsage>start</MessageUsage>\n" +
                "                                <NotificationDate>2025-01-15T14:26:53Z</NotificationDate>\n" +
                "                                <MessageText>Travaux rue des Piliers de la Chauvinière</MessageText>\n" +
                "                            </AlertMessage>\n" +
                "                            <AlertMessage>\n" +
                "                                <ChannelName>SIC à quai</ChannelName>\n" +
                "                                <ChannelType>beacon</ChannelType>\n" +
                "                                <MessageUsage>start</MessageUsage>\n" +
                "                                <NotificationDate>2025-01-15T14:26:53Z</NotificationDate>\n" +
                "                                <MessageText>Travaux L93 déviée entre Armor et Internat Laënnec</MessageText>\n" +
                "                            </AlertMessage>\n" +
                "                            <AlertMessage>\n" +
                "                                <ChannelName>SIC à quai</ChannelName>\n" +
                "                                <ChannelType>beacon</ChannelType>\n" +
                "                                <MessageUsage>end</MessageUsage>\n" +
                "                                <NotificationDate>2025-01-15T14:26:53Z</NotificationDate>\n" +
                "                                <MessageText>L93 fin de perturbation retour au parcours normal</MessageText>\n" +
                "                            </AlertMessage>\n" +
                "                            <AlertMessage>\n" +
                "                                <ChannelName>SIC embarqué</ChannelName>\n" +
                "                                <ChannelType>beacon</ChannelType>\n" +
                "                                <MessageUsage>start</MessageUsage>\n" +
                "                                <NotificationDate>2025-01-15T14:26:53Z</NotificationDate>\n" +
                "                                <MessageText>Travaux L93 déviée entre Armor et Internat Laënnec</MessageText>\n" +
                "                            </AlertMessage>\n" +
                "                        </AlertMessages>\n" +
                "                    </Alerts>\n" +
                "                </Extensions>\n" +
                "            </GeneralMessage>\n" +
                "        </GeneralMessageDelivery>\n" +
                "    </ServiceDelivery>\n" +
                "</Siri>\n" +
                "\n" +
                "\n" +
                "\n" +
                "\n";

        String soapResult = CustomSiriXml.subscriptionRawToSoap(siriString);
        assertTrue(soapResult.contains("<soapenv:Body>"));
        assertTrue(soapResult.contains("NotifyGeneralMessage"));
        assertTrue(soapResult.contains("<Notification xmlns=\"\">"));
        assertTrue(soapResult.contains("<siri:GeneralMessageDelivery xmlns:siri=\"http://www.siri.org.uk/siri\""));
        assertTrue(soapResult.contains("<GeneralMessage xmlns=\"http://www.siri.org.uk/siri\" "));
        assertTrue(soapResult.contains("Content xmlns=\"\""));
    }

    @Test
    public void testChannelFilter() {
        GeneralMessage msg = TestObjectFactory.createGeneralMessage();
        Content content1 = new Content();
        content1.setStopPointRefs(List.of("stop1"));
        msg.setContent(content1);

        //adding gm with 1 stopRef
        generalMessages.add("test", msg);
        assertEquals(1, generalMessages.getAll().size());

        InfoChannelRefStructure infoChannelRefStructure = new InfoChannelRefStructure();
        infoChannelRefStructure.setValue("Information");
        Siri siri = generalMessages.createServiceDelivery("reqRef", null, "name", 20, List.of(infoChannelRefStructure));
        assertEquals(0, getGeneralMessagesFromSiri(siri).size(), "Delevery should be empty because a 'perturbation' message was added to the cache and we are asking for 'information' messages");


        infoChannelRefStructure.setValue("Perturbation");
        siri = generalMessages.createServiceDelivery("reqRef", "test", "name", 20, List.of(infoChannelRefStructure));
        assertEquals(1, getGeneralMessagesFromSiri(siri).size(), "Delevery should return the msg because we are asking the correct channel");
    }

    @Test
    public void testDatasetFilter() {
        GeneralMessage msg = TestObjectFactory.createGeneralMessage();
        Content content1 = new Content();
        content1.setStopPointRefs(List.of("stop1"));
        msg.setContent(content1);

        //adding gm with 1 stopRef
        generalMessages.add("test", msg);
        assertEquals(1, generalMessages.getAll().size());

        InfoChannelRefStructure infoChannelRefStructure = new InfoChannelRefStructure();
        infoChannelRefStructure.setValue("Perturbation");

        Siri siri = generalMessages.createServiceDelivery("reqRef", "wrongDataset", "name", 20, List.of(infoChannelRefStructure));
        assertEquals(0, getGeneralMessagesFromSiri(siri).size(), "Delevery should be empty because a  message was added to the cache with datasetId 'test' and we are asking for dataset 'wrongDataset'");


        siri = generalMessages.createServiceDelivery("reqRef", "test", "name", 20, List.of(infoChannelRefStructure));
        assertEquals(1, getGeneralMessagesFromSiri(siri).size(), "Delevery should return the msg because we are asking the correct datasetId");
    }

    @Test
    public void testLumiplanFormat() {
        GeneralMessage msg = TestObjectFactory.createGeneralMessage();
        Content content1 = new Content();
        content1.setStopPointRefs(List.of("stop1"));
        msg.setContent(content1);

        //adding gm with 1 stopRef
        generalMessages.add("test", msg);
        assertEquals(1, generalMessages.getAll().size());

        Siri siri = generalMessages.createServiceDelivery("test", "test", "name", 20, new ArrayList<>());

        GeneralMessage recoveredGeneralMessage = getGeneralMessagesFromSiri(siri).get(0);
        assertEquals(recoveredGeneralMessage.getFormatRef(), "STIF-IDF");
        assertNotNull(recoveredGeneralMessage.getRecordedAtTime());
        assertNotNull(recoveredGeneralMessage.getItemIdentifier());
        assertNotNull(recoveredGeneralMessage.getValidUntilTime());
    }

    @Test
    public void testCancellations() throws UnmarshalException {
        generalMessagesCancellations.clearAll();
        generalMessages.clearAll();

        GeneralMessage msg = TestObjectFactory.createGeneralMessage();
        Content content1 = new Content();
        content1.setStopPointRefs(List.of("stop1"));
        msg.setContent(content1);

        //adding gm with 1 stopRef
        generalMessages.add("test", msg);

        GeneralMessageCancellation msgCancel = createGeneralMessageCancellation();

        generalMessagesCancellations.add("test", msgCancel);
        assertEquals(1, generalMessages.getAll().size());
        assertEquals(1, generalMessagesCancellations.getAll().size());

        String stringXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:ns2=\"http://www.ifopt.org.uk/acsb\" xmlns:ns3=\"http://www.ifopt.org.uk/ifopt\" xmlns:ns4=\"http://datex2.eu/schema/2_0RC1/2_0\" version=\"2.0\">\n" +
                "    <ServiceRequest>\n" +
                "        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>\n" +
                "        <GeneralMessageRequest version=\"2.0\">\n" +
                "        </GeneralMessageRequest>\n" +
                "    </ServiceRequest>\n" +
                "</Siri>";

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);

        IncomingSiriParameters params = new IncomingSiriParameters();
        params.setIncomingSiriStream(xml);
        params.setDatasetId("TEST");
        params.setOutboundIdMappingPolicy(SiriHandler.getIdMappingPolicy("false", "true"));
        params.setMaxSize(-1);

        Siri response = handler.handleIncomingSiri(params);
        assertNotNull(response);

        //Check that response contains the general Message + the general Message cancellation
        assertEquals(response.getServiceDelivery().getGeneralMessageDeliveries().size(), 2);
        GeneralMessageDeliveryStructure first = response.getServiceDelivery().getGeneralMessageDeliveries().get(0);
        GeneralMessageDeliveryStructure second = response.getServiceDelivery().getGeneralMessageDeliveries().get(1);
        assertNotNull(first.getGeneralMessages());
        assertEquals(first.getGeneralMessages().size(), 1);
        assertNotNull(second.getGeneralMessageCancellations());
        assertEquals(second.getGeneralMessageCancellations().size(), 1);

    }

    @Test
    public void testSXCancellationMessage() throws InterruptedException {
        String datasetId = "TEST";
        List<PtSituationElement> incomingSituations = new ArrayList<>();
        PtSituationElement newOpensituation = new PtSituationElement();
        SituationNumber sitNumber = new SituationNumber();
        sitNumber.setValue("SIT1");
        newOpensituation.setSituationNumber(sitNumber);
        newOpensituation.setProgress(WorkflowStatusEnumeration.OPEN);

        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime nowMinusTen = now.minusMinutes(10);
        HalfOpenTimestampOutputRangeStructure publicationWindow = new HalfOpenTimestampOutputRangeStructure();
        publicationWindow.setStartTime(nowMinusTen);
        publicationWindow.setEndTime(nowMinusTen.plusHours(2));
        newOpensituation.getPublicationWindows().add(publicationWindow);


        incomingSituations.add(newOpensituation);


        // ingesting an open situation
        situationExchangeInbound.ingestSituations(datasetId, incomingSituations, false);
        Assertions.assertEquals(1, situations.getAll().size());
        Assertions.assertEquals(1, generalMessages.getAll().size());

        newOpensituation.setProgress(WorkflowStatusEnumeration.CLOSED);
        situationExchangeInbound.ingestSituations(datasetId, incomingSituations, false);

        // After ingesting the closed situation, general info must have been removed from cache
        Assertions.assertEquals(0, generalMessages.getAll().size());

    }


    private Content getContentFromGeneralMessage(GeneralMessage generalMessage) {
        if (generalMessage == null) {
            return null;
        }
        return (Content) generalMessage.getContent();
    }


    private List<GeneralMessage> getGeneralMessagesFromSiri(Siri siri) {

        List<GeneralMessage> resultList = new ArrayList<>();

        if (siri.getServiceDelivery().getGeneralMessageDeliveries() == null || siri.getServiceDelivery().getGeneralMessageDeliveries().size() == 0) {
            return new ArrayList<>();
        }

        for (GeneralMessageDeliveryStructure generalMessageDelivery : siri.getServiceDelivery().getGeneralMessageDeliveries()) {
            resultList.addAll(generalMessageDelivery.getGeneralMessages());
        }
        return resultList;
    }


    private GeneralMessageCancellation createGeneralMessageCancellation() {
        return createGeneralMessageCancellation("Perturbation");
    }

    private GeneralMessageCancellation createGeneralMessageCancellation(String infoChannel) {
        GeneralMessageCancellation msg = new GeneralMessageCancellation();
        InfoMessageRefStructure identifier = new InfoMessageRefStructure();
        identifier.setValue(UUID.randomUUID().toString());
        msg.setInfoMessageIdentifier(identifier);
        InfoChannelRefStructure RefStruct = new InfoChannelRefStructure();
        RefStruct.setValue(infoChannel);
        msg.setInfoChannelRef(RefStruct);

        return msg;
    }


}
