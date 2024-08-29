package no.rutebanken.anshar.data;

import no.rutebanken.anshar.data.frGeneralMessageStructure.Content;
import no.rutebanken.anshar.data.util.GeneralMessageMapper;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import org.apache.xerces.impl.xs.opti.ElementImpl;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import uk.org.siri.siri20.*;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class GeneralMessageMapperTest {

    @Test
    public void testGeneralMessageMapperTest() {

        String situationNumber = "sitNumber";
        ZonedDateTime from = ZonedDateTime.now();
        ZonedDateTime to = ZonedDateTime.now().plusHours(1);

        PtSituationElement ptSituationElt = createPtSituationElement("partRef", situationNumber, from, to);

        GeneralMessage convertedMessage = GeneralMessageMapper.mapToGeneralMessage(ptSituationElt);
        assertEquals("France", convertedMessage.getFormatRef());
        assertNotNull(convertedMessage.getRecordedAtTime());
        assertEquals(situationNumber, convertedMessage.getItemIdentifier());
        assertEquals(situationNumber, convertedMessage.getInfoMessageIdentifier().getValue());
        assertEquals(situationNumber, convertedMessage.getSituationRef().getSituationSimpleRef().getValue());
        assertEquals("Perturbation", convertedMessage.getInfoChannelRef().getValue());
        assertEquals(to, convertedMessage.getValidUntilTime());

        Content content = (Content) convertedMessage.getContent();
        assertEquals("networkRef", content.getGroupOfLinesRefs().get(0));
        assertEquals("lineRef", content.getLineRefs().get(0));
        assertEquals("sp1", content.getStopPointRefs().get(0));
        assertEquals("infotrafic à En raison de Incident voyageur, la ligne 3 est coupée entre les stations X et Y",
                content.getMessage().getMsgText());
        assertEquals("textOnly",
                content.getMessage().getMsgType());

        assertEquals(1, convertedMessage.getExtensions().getAnies().size());
        assertEquals("okay", convertedMessage.getExtensions().getAnies().get(0).getTagName());
    }


    private PtSituationElement createPtSituationElement(String participantRef, String situationNumber, ZonedDateTime startTime, ZonedDateTime endTime) {
        PtSituationElement element = new PtSituationElement();
        element.setCreationTime(ZonedDateTime.now());
        HalfOpenTimestampOutputRangeStructure period = new HalfOpenTimestampOutputRangeStructure();
        period.setStartTime(startTime);

        element.setParticipantRef(SiriObjectFactory.createRequestorRef(participantRef));

        SituationNumber sn = new SituationNumber();
        sn.setValue(situationNumber);
        element.setSituationNumber(sn);

        AffectsScopeStructure.Networks.AffectedNetwork affectedNetwork = new AffectsScopeStructure.Networks.AffectedNetwork();


        NetworkRefStructure networkRefStruct = new NetworkRefStructure();
        networkRefStruct.setValue("networkRef");
        affectedNetwork.setNetworkRef(networkRefStruct);

        AffectedLineStructure affLineStruct = new AffectedLineStructure();
        LineRef lineRef = new LineRef();
        lineRef.setValue("lineRef");
        affLineStruct.setLineRef(lineRef);

        affectedNetwork.getAffectedLines().add(affLineStruct);


        AffectsScopeStructure affectStruct = new AffectsScopeStructure();
        AffectsScopeStructure.Networks networks = new AffectsScopeStructure.Networks();
        networks.getAffectedNetworks().add(affectedNetwork);
        affectStruct.setNetworks(networks);


        AffectsScopeStructure.StopPoints affectedStops = new AffectsScopeStructure.StopPoints();
        AffectedStopPointStructure affPointStruct = new AffectedStopPointStructure();
        StopPointRef stopPointRef = new StopPointRef();
        stopPointRef.setValue("sp1");
        affPointStruct.setStopPointRef(stopPointRef);
        affectedStops.getAffectedStopPoints().add(affPointStruct);
        affectStruct.setStopPoints(affectedStops);

        element.setAffects(affectStruct);

        //ValidityPeriod has already expired
        period.setEndTime(endTime);
        element.getValidityPeriods().add(period);

        var description = new DefaultedTextStructure();
        description.setValue("<p><strong>infotrafic à&nbsp;</strong></p><p>&nbsp;</p><p>En raison de Incident voyageur, la ligne 3 est coupée entre les stations X et Y</p><p>&nbsp;</p><p>&nbsp;</p>");
        element.getDescriptions().add(description);

        Extensions extensions = new Extensions();
        Element e = new ElementImpl("are", "you", "okay", "any", 0, 0, 0);
        extensions.getAnies().add(e);
        element.setExtensions(extensions);

        return element;
    }
}
