package no.rutebanken.anshar.data;

import no.rutebanken.anshar.data.frGeneralMessageStructure.Content;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri20.*;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GeneralMessageTest extends SpringBootBaseTest {

    @Autowired
    private GeneralMessages generalMessages;

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
