package no.rutebanken.anshar.data;

import no.rutebanken.anshar.data.frGeneralMessageStructure.Content;
import no.rutebanken.anshar.data.util.GeneralMessageMapper;
import org.apache.commons.collections.CollectionUtils;
import org.entur.siri21.util.SiriXml;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.GeneralMessage;
import uk.org.siri.siri21.Siri;

import jakarta.xml.bind.JAXBException;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GeneralMessageMapperTest {


    @Test
    public void test_mapToGeneralMessage_acceptance() throws IOException, XMLStreamException, JAXBException {
        // Arrange
        Path siriSxFile = Path.of("src/test/resources/siri-sx-GeneralMessageMapper-acceptance.xml");
        System.setProperty("default.time.zone", "UTC"); // required to unmarshal string timestamp intto ZonedDateTime
        Siri siri = SiriXml.parseXml(Files.readString(siriSxFile));

        // Act
        GeneralMessage output =
                GeneralMessageMapper.mapToGeneralMessage(siri.getServiceDelivery().getSituationExchangeDeliveries().get(0).getSituations().getPtSituationElements().get(0));

        // Assert
        assertEquals("France", output.getFormatRef());
        assertEquals(ZonedDateTime.of(2024, 10, 25, 16, 52, 25, 0, ZoneId.of("Europe/Paris")),
                output.getRecordedAtTime());
        assertEquals("bf80862a-92e0-11ef-8a59-0a58a9feac02", output.getItemIdentifier());
        assertEquals("bf80862a-92e0-11ef-8a59-0a58a9feac02", output.getInfoMessageIdentifier().getValue());
        assertEquals("bf80862a-92e0-11ef-8a59-0a58a9feac02", output.getSituationRef().getSituationSimpleRef().getValue());
        assertEquals("Perturbation", output.getInfoChannelRef().getValue());
        assertEquals(ZonedDateTime.of(2024, 11, 29, 19, 50, 0, 0, ZoneId.of("Europe/Paris")),
                output.getValidUntilTime());
        Content outputContent = (Content) output.getContent();
        assertEquals("Travaux prévus, Ligne C2 et 28 impactées", outputContent.getMessage().getMsgText());
        assertEquals("textOnly", outputContent.getMessage().getMsgType());
        assertEquals(0, outputContent.getGroupOfLinesRefs().size());
        assertTrue(CollectionUtils.isEqualCollection(List.of("NAOLIBORG:Line:C2:LOC", "NAOLIBORG:Line:28:LOC"),
                outputContent.getLineRefs()), "should map 2 line refs");
        assertEquals(0, outputContent.getRouteRefs().size(), "should not map route refs");
        assertTrue(CollectionUtils.isEqualCollection(List.of("FR_NAOLIB:Quay:297", "FR_NAOLIB:Quay:351", "FR_NAOLIB" +
                        ":Quay:111", "FR_NAOLIB:Quay:112"),
                outputContent.getStopPointRefs()), "should map 4 stop point refs");
        assertSame(siri.getServiceDelivery().getSituationExchangeDeliveries().get(0).getSituations().getPtSituationElements().get(0).getExtensions(),
                output.getExtensions(), "should copy extensions");
    }
}

