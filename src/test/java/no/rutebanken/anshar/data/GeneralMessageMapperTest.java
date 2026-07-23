package no.rutebanken.anshar.data;

import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.frGeneralMessageStructure.Content;
import no.rutebanken.anshar.data.frGeneralMessageStructure.MessageType;
import no.rutebanken.anshar.data.util.GeneralMessageMapper;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.entur.siri21.util.SiriXml;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.ifopt.siri21.StopPlaceRef;
import uk.org.siri.siri21.*;

import jakarta.xml.bind.JAXBException;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class GeneralMessageMapperTest extends SpringBootBaseTest {

    @Autowired
    private GeneralMessageMapper mapper;

    @Autowired
    private SubscriptionConfig subscriptionConfig;

    @Autowired
    private StopPlaceUpdaterService stopPlaceUpdaterService;


    @Test
    public void test_mapToGeneralMessage_acceptance() throws IOException, XMLStreamException, JAXBException {
        // Arrange
        Path siriSxFile = Path.of("src/test/resources/siri-sx-GeneralMessageMapper-acceptance.xml");
        System.setProperty("default.time.zone", "UTC"); // required to unmarshal string timestamp intto ZonedDateTime
        Siri siri = SiriXml.parseXml(Files.readString(siriSxFile));

        // Act
        GeneralMessage output =
                mapper.mapToGeneralMessage("DAT1", siri.getServiceDelivery().getSituationExchangeDeliveries().get(0).getSituations().getPtSituationElements().get(0));

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
        assertEquals("Travaux prévus, Ligne C2 et 28 impactées", outputContent.getMessages().getFirst().getMsgText());
        assertEquals(MessageType.TEXT_ONLY, outputContent.getMessages().getFirst().getMsgType());
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

    @Test
    public void test_affected_stop_place_converted_to_stop_points() {
        String datasetId = "PROD1";
        IdProcessingParameters idProcessingParameters = new IdProcessingParameters();
        idProcessingParameters.setDatasetId(datasetId);
        idProcessingParameters.setOutputPrefixToAdd("PROD1:Quay:");
        idProcessingParameters.setObjectType(ObjectType.STOP);
        subscriptionConfig.getIdProcessingParameters().add(idProcessingParameters);


        PtSituationElement situation = new PtSituationElement();
        AffectsScopeStructure affectScope = new AffectsScopeStructure();
        AffectsScopeStructure.StopPlaces stopPlaces = new AffectsScopeStructure.StopPlaces();
        AffectedStopPlaceStructure stopPlaceStruct = new AffectedStopPlaceStructure();
        StopPlaceRef stopPlaceRef = new StopPlaceRef();
        stopPlaceRef.setValue("SP1");
        stopPlaceStruct.setStopPlaceRef(stopPlaceRef);
        stopPlaces.getAffectedStopPlaces().add(stopPlaceStruct);
        affectScope.setStopPlaces(stopPlaces);
        situation.setAffects(affectScope);
        SituationNumber situationNumber = new SituationNumber();
        situationNumber.setValue("sit1");
        situation.setSituationNumber(situationNumber);


        String QUAY1_REF_MOBI = "MOBIITI:Quay:1";
        String QUAY1_REF = "PROD1:Quay:toto";
        String QUAY2_REF_MOBI = "MOBIITI:Quay:2";
        String QUAY2_REF = "PROD1:Quay:titi";
        String STOP_PLACE_REF = datasetId + ":StopPlace:SP1";
        String STOP_PLACE_REF_MOBI = "MOBIITI:StopPlace:3475";

        Map<String, Pair<String, String>> stopPlaceMap = new HashMap<>();
        stopPlaceMap.put(STOP_PLACE_REF, Pair.of(STOP_PLACE_REF_MOBI, "SP name"));
        stopPlaceMap.put(QUAY1_REF, Pair.of(QUAY1_REF_MOBI, "q1 name"));
        stopPlaceMap.put(QUAY2_REF, Pair.of(QUAY2_REF_MOBI, "q2 name"));
        stopPlaceUpdaterService.addStopPlaceMappings(stopPlaceMap);

        Map<String, Set<String>> stopPlaceReverseMap = new HashMap<>();

        for (Map.Entry<String, Pair<String, String>> mappingEntry : stopPlaceMap.entrySet()) {
            Set<String> providerIds = new HashSet<>();
            providerIds.add(mappingEntry.getKey());
            stopPlaceReverseMap.put(mappingEntry.getValue().getLeft(), providerIds);
        }

        stopPlaceUpdaterService.addStopPlaceReverseMappings(stopPlaceReverseMap);

        Map<String, List<String>> stopPlaceQuayAssociations = new HashMap<>();
        List<String> children = new ArrayList<>();
        children.add(QUAY1_REF_MOBI);
        children.add(QUAY2_REF_MOBI);
        stopPlaceQuayAssociations.put(STOP_PLACE_REF_MOBI, children);


        stopPlaceUpdaterService.addStopPlaceQuayAssociations(stopPlaceQuayAssociations);


        GeneralMessage generalMessage = mapper.mapToGeneralMessage(datasetId, situation);
        Content content = (Content) generalMessage.getContent();


        List<String> stopPoints = content.getStopPointRefs();


        List<String> expectedResults = new ArrayList<>();
        expectedResults.add("toto");
        expectedResults.add("titi");
        Assert.assertTrue(expectedResults.containsAll(stopPoints));

    }
}

