package no.rutebanken.anshar.data;

import no.rutebanken.anshar.data.frGeneralMessageStructure.Content;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri20.*;

import javax.xml.bind.UnmarshalException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class GeneralMessageTest extends SpringBootBaseTest {

    @Autowired
    private GeneralMessages generalMessages;

    @Autowired
    private GeneralMessagesCancellations generalMessagesCancellations;

    @Autowired
    private SiriHandler handler;

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
        GeneralMessage msg = createGeneralMessage();
        generalMessages.add("test", msg);
        assertTrue(generalMessages.getAll().size() == previousSize + 1);
    }

    @Test
    public void testUpdate() {


        GeneralMessage msg = createGeneralMessage();
        Content content1 = new Content();
        content1.setStopPointRefs(Arrays.asList("stop1"));
        msg.setContent(content1);

        //adding gm with 1 stopRef
        generalMessages.add("test", msg);
        assertTrue(generalMessages.getAll().size() == 1);

        Siri siri = generalMessages.createServiceDelivery("test", null, "name", 20, new ArrayList<>());


        Content recoveredContent = getContentFromGeneralMessage(getGeneralMessagesFromSiri(siri).get(0));


        assertEquals(1, recoveredContent.getStopPointRefs().size());
        assertEquals("stop1", recoveredContent.getStopPointRefs().get(0));

        Content content2 = new Content();
        content2.setStopPointRefs(Arrays.asList("stop1", "stop2"));
        msg.setContent(content2);

        //adding an update of the msg with 2 stopRefs
        generalMessages.add("test", msg);
        assertTrue(generalMessages.getAll().size() == 1);

        siri = generalMessages.createServiceDelivery("test", null, "name", 20, new ArrayList<>());

        recoveredContent = getContentFromGeneralMessage(getGeneralMessagesFromSiri(siri).get(0));
        assertEquals(2, recoveredContent.getStopPointRefs().size());
        assertEquals("stop1", recoveredContent.getStopPointRefs().get(0));
        assertEquals("stop2", recoveredContent.getStopPointRefs().get(1));

    }

    @Test
    public void testChannelFilter() {
        GeneralMessage msg = createGeneralMessage();
        Content content1 = new Content();
        content1.setStopPointRefs(Arrays.asList("stop1"));
        msg.setContent(content1);

        //adding gm with 1 stopRef
        generalMessages.add("test", msg);
        assertTrue(generalMessages.getAll().size() == 1);

        InfoChannelRefStructure infoChannelRefStructure = new InfoChannelRefStructure();
        infoChannelRefStructure.setValue("Information");
        Siri siri = generalMessages.createServiceDelivery("reqRef", null, "name", 20, Arrays.asList(infoChannelRefStructure));
        assertEquals(0, getGeneralMessagesFromSiri(siri).size(), "Delevery should be empty because a 'perturbation' message was added to the cache and we are asking for 'information' messages");


        infoChannelRefStructure.setValue("Perturbation");
        siri = generalMessages.createServiceDelivery("reqRef", null, "name", 20, Arrays.asList(infoChannelRefStructure));
        assertEquals(1, getGeneralMessagesFromSiri(siri).size(), "Delevery should return the msg because we are asking the correct channel");
    }

    @Test
    public void testDatasetFilter() {
        GeneralMessage msg = createGeneralMessage();
        Content content1 = new Content();
        content1.setStopPointRefs(Arrays.asList("stop1"));
        msg.setContent(content1);

        //adding gm with 1 stopRef
        generalMessages.add("test", msg);
        assertTrue(generalMessages.getAll().size() == 1);

        InfoChannelRefStructure infoChannelRefStructure = new InfoChannelRefStructure();
        infoChannelRefStructure.setValue("Perturbation");

        Siri siri = generalMessages.createServiceDelivery("reqRef", "wrongDataset", "name", 20, Arrays.asList(infoChannelRefStructure));
        assertEquals(0, getGeneralMessagesFromSiri(siri).size(), "Delevery should be empty because a  message was added to the cache with datasetId 'test' and we are asking for dataset 'wrongDataset'");


        siri = generalMessages.createServiceDelivery("reqRef", null, "name", 20, Arrays.asList(infoChannelRefStructure));
        assertEquals(1, getGeneralMessagesFromSiri(siri).size(), "Delevery should return the msg because we are asking the correct datasetId");
    }

    @Test
    public void testLumiplanFormat() {
        GeneralMessage msg = createGeneralMessage();
        Content content1 = new Content();
        content1.setStopPointRefs(Arrays.asList("stop1"));
        msg.setContent(content1);

        //adding gm with 1 stopRef
        generalMessages.add("test", msg);
        assertTrue(generalMessages.getAll().size() == 1);

        Siri siri = generalMessages.createServiceDelivery("test", null, "name", 20, new ArrayList<>());

        GeneralMessage recoveredGeneralMessage = getGeneralMessagesFromSiri(siri).get(0);
        assertEquals(recoveredGeneralMessage.getFormatRef(), "STIF-IDF");
        assertNotNull(recoveredGeneralMessage.getRecordedAtTime());
        assertNotNull(recoveredGeneralMessage.getItemIdentifier());
        assertNotNull(recoveredGeneralMessage.getValidUntilTime());
    }

    @Test
    public void testCancellations() throws UnmarshalException {
        GeneralMessage msg = createGeneralMessage();
        Content content1 = new Content();
        content1.setStopPointRefs(Arrays.asList("stop1"));
        msg.setContent(content1);

        //adding gm with 1 stopRef
        generalMessages.add("test", msg);

        GeneralMessageCancellation msgCancel = createGeneralMessageCancellation();

        generalMessagesCancellations.add("test", msgCancel);
        assertTrue(generalMessages.getAll().size() == 1);
        assertTrue(generalMessagesCancellations.getAll().size() == 1);

        String stringXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:ns2=\"http://www.ifopt.org.uk/acsb\" xmlns:ns3=\"http://www.ifopt.org.uk/ifopt\" xmlns:ns4=\"http://datex2.eu/schema/2_0RC1/2_0\" version=\"2.0\">\n" +
                "    <ServiceRequest>\n" +
                "        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>\n" +
                "        <GeneralMessageRequest version=\"2.0\">\n" +
                "        </GeneralMessageRequest>\n" +
                "    </ServiceRequest>\n" +
                "</Siri>";

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);
        ;

        Siri response = handler.handleIncomingSiri(null, xml, "TEST", SiriHandler.getIdMappingPolicy("false", "true"), -1, null);
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

    private GeneralMessage createGeneralMessage() {
        return createGeneralMessage("Perturbation");
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

    private GeneralMessage createGeneralMessage(String infoChannel) {

        GeneralMessage msg = new GeneralMessage();
        InfoMessageRefStructure identifier = new InfoMessageRefStructure();
        identifier.setValue(UUID.randomUUID().toString());
        msg.setInfoMessageIdentifier(identifier);
        InfoChannelRefStructure RefStruct = new InfoChannelRefStructure();
        RefStruct.setValue(infoChannel);
        msg.setInfoChannelRef(RefStruct);

        return msg;
    }
}
