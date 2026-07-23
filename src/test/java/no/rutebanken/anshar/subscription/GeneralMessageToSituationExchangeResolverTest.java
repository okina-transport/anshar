package no.rutebanken.anshar.subscription;

import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.frGeneralMessageStructure.Content;
import no.rutebanken.anshar.data.frGeneralMessageStructure.Message;
import no.rutebanken.anshar.data.frGeneralMessageStructure.MessageType;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.siri.converter.GeneralMessageToSituationExchangeResolver;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.GeneralMessage;
import uk.org.siri.siri21.InfoChannelRefStructure;
import uk.org.siri.siri21.InfoMessageRefStructure;
import uk.org.siri.siri21.PtSituationElement;
import uk.org.siri.siri21.WorkflowStatusEnumeration;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GeneralMessageToSituationExchangeResolverTest {

    private final SubscriptionConfig subscriptionConfig = new SubscriptionConfig();
    private final StopPlaceUpdaterService stopPlaceUpdaterService = new StopPlaceUpdaterService();
    private final GeneralMessageToSituationExchangeResolver resolver =
            new GeneralMessageToSituationExchangeResolver(subscriptionConfig, stopPlaceUpdaterService);

    private static final ZonedDateTime RECORDED_AT_TIME = ZonedDateTime.of(2024, 11, 20, 10, 0, 0, 0, ZoneId.of("Europe/Paris"));
    private static final ZonedDateTime VALID_UNTIL_TIME = ZonedDateTime.of(2024, 11, 21, 10, 0, 0, 0, ZoneId.of("Europe/Paris"));

    @Test
    void map_null_generalMessage_returns_null() {
        assertThat(resolver.map(null, "DAT1")).isNull();
    }

    @Test
    void map_full_generalMessage_maps_all_fields() {
        Content content = new Content();
        content.setLineRefs(List.of("LINE:1", "LINE:2"));
        content.setStopPointRefs(List.of("STOP:1"));
        Message message = new Message();
        message.setMsgType(MessageType.TEXT_ONLY);
        message.setMsgText("Travaux sur la ligne 1");
        content.getMessages().add(message);

        GeneralMessage generalMessage = buildGeneralMessage("SIT1", "Perturbation", content);

        PtSituationElement result = resolver.map(generalMessage, "DAT1");

        assertThat(result.getSituationNumber().getValue()).isEqualTo("SIT1");
        assertThat(result.getCreationTime()).isEqualTo(RECORDED_AT_TIME);
        assertThat(result.getProgress()).isEqualTo(WorkflowStatusEnumeration.OPEN);
        assertThat(result.getParticipantRef().getValue()).isEqualTo("DAT1-from-gm");

        assertThat(result.getValidityPeriods()).hasSize(1);
        assertThat(result.getValidityPeriods().getFirst().getStartTime()).isEqualTo(RECORDED_AT_TIME);
        assertThat(result.getValidityPeriods().getFirst().getEndTime()).isEqualTo(VALID_UNTIL_TIME);

        assertThat(result.getPublicationWindows()).hasSize(1);
        assertThat(result.getPublicationWindows().getFirst().getStartTime()).isEqualTo(RECORDED_AT_TIME);
        assertThat(result.getPublicationWindows().getFirst().getEndTime()).isEqualTo(VALID_UNTIL_TIME);

        assertThat(result.getKeywords()).containsExactly("Perturbation");

        assertThat(result.getSummaries()).hasSize(1);
        assertThat(result.getSummaries().getFirst().getValue()).isEqualTo("Travaux sur la ligne 1");

        assertThat(result.getAffects().getNetworks().getAffectedNetworks()).hasSize(1);
        List<String> mappedLineRefs = result.getAffects().getNetworks().getAffectedNetworks().getFirst().getAffectedLines()
                .stream().map(line -> line.getLineRef().getValue()).toList();
        assertThat(mappedLineRefs).containsExactlyInAnyOrder("LINE:1", "LINE:2");

        assertThat(result.getAffects().getStopPoints().getAffectedStopPoints()).hasSize(1);
        assertThat(result.getAffects().getStopPoints().getAffectedStopPoints().getFirst().getStopPointRef().getValue()).isEqualTo("STOP:1");
    }

    @Test
    void map_without_infoMessageIdentifier_returns_situationNumber_with_null_value() {
        GeneralMessage generalMessage = new GeneralMessage();
        generalMessage.setRecordedAtTime(RECORDED_AT_TIME);

        PtSituationElement result = resolver.map(generalMessage, "DAT1");

        assertThat(result.getSituationNumber().getValue()).isNull();
    }

    @Test
    void map_without_infoChannelRef_produces_no_keywords() {
        GeneralMessage generalMessage = new GeneralMessage();
        generalMessage.setRecordedAtTime(RECORDED_AT_TIME);

        PtSituationElement result = resolver.map(generalMessage, "DAT1");

        assertThat(result.getKeywords()).isEmpty();
    }

    @Test
    void map_without_content_produces_no_summary_and_no_affects() {
        GeneralMessage generalMessage = buildGeneralMessage("SIT1", "Perturbation", null);

        PtSituationElement result = resolver.map(generalMessage, "DAT1");

        assertThat(result.getSummaries()).isEmpty();
        assertThat(result.getAffects()).isNull();
    }

    @Test
    void map_with_content_but_no_message_text_produces_no_summary() {
        Content content = new Content();
        content.getMessages().add(new Message());

        GeneralMessage generalMessage = buildGeneralMessage("SIT1", "Perturbation", content);

        PtSituationElement result = resolver.map(generalMessage, "DAT1");

        assertThat(result.getSummaries()).isEmpty();
    }

    @Test
    void map_with_content_but_no_refs_produces_no_affects() {
        Content content = new Content();

        GeneralMessage generalMessage = buildGeneralMessage("SIT1", "Perturbation", content);

        PtSituationElement result = resolver.map(generalMessage, "DAT1");

        assertThat(result.getAffects()).isNull();
    }

    @Test
    void map_with_only_lineRefs_sets_networks_but_not_stopPoints() {
        Content content = new Content();
        content.setLineRefs(List.of("LINE:1"));

        GeneralMessage generalMessage = buildGeneralMessage("SIT1", "Perturbation", content);

        PtSituationElement result = resolver.map(generalMessage, "DAT1");

        assertThat(result.getAffects().getNetworks().getAffectedNetworks().getFirst().getAffectedLines().getFirst().getLineRef().getValue())
                .isEqualTo("LINE:1");
        assertThat(result.getAffects().getStopPoints()).isNull();
    }

    @Test
    void map_with_only_stopPointRefs_sets_stopPoints_but_not_networks() {
        Content content = new Content();
        content.setStopPointRefs(List.of("STOP:1"));

        GeneralMessage generalMessage = buildGeneralMessage("SIT1", "Perturbation", content);

        PtSituationElement result = resolver.map(generalMessage, "DAT1");

        assertThat(result.getAffects().getStopPoints().getAffectedStopPoints().getFirst().getStopPointRef().getValue())
                .isEqualTo("STOP:1");
        assertThat(result.getAffects().getNetworks()).isNull();
        assertThat(result.getAffects().getStopPlaces()).isNull();
    }

    @Test
    void map_with_stopPlaceRefs_sets_stopPlaces_directly() {
        Content content = new Content();
        content.setStopPlaceRefs(List.of("PLACE:1", "PLACE:2"));

        GeneralMessage generalMessage = buildGeneralMessage("SIT1", "Perturbation", content);

        PtSituationElement result = resolver.map(generalMessage, "DAT1");

        List<String> mappedStopPlaceRefs = result.getAffects().getStopPlaces().getAffectedStopPlaces().stream()
                .map(affectedStopPlace -> affectedStopPlace.getStopPlaceRef().getValue())
                .toList();
        assertThat(mappedStopPlaceRefs).containsExactlyInAnyOrder("PLACE:1", "PLACE:2");
    }

    @Test
    void map_with_stopPlaceRefs_and_stopPointRefs_prefers_stopPlaceRefs_without_deriving() {
        Content content = new Content();
        content.setStopPlaceRefs(List.of("PLACE:1"));
        // No IdProcessingParameters/StopPlaceUpdaterService mapping configured: derivation would fail if attempted
        content.setStopPointRefs(List.of("QUAY1"));

        GeneralMessage generalMessage = buildGeneralMessage("SIT1", "Perturbation", content);

        PtSituationElement result = resolver.map(generalMessage, "DAT1");

        assertThat(result.getAffects().getStopPlaces().getAffectedStopPlaces()).hasSize(1);
        assertThat(result.getAffects().getStopPlaces().getAffectedStopPlaces().getFirst().getStopPlaceRef().getValue())
                .isEqualTo("PLACE:1");
    }

    @Test
    void map_with_only_stopPointRefs_derives_parent_stopPlaceRef_when_mapping_available() {
        IdProcessingParameters idProcessingParameters = new IdProcessingParameters();
        idProcessingParameters.setDatasetId("DAT1");
        idProcessingParameters.setObjectType(ObjectType.STOP);
        idProcessingParameters.setOutputPrefixToAdd("DAT1:Quay:");
        subscriptionConfig.getIdProcessingParameters().add(idProcessingParameters);

        stopPlaceUpdaterService.addStopPlaceMappings(Map.of("DAT1:Quay:QUAY1", Pair.of("MOBIITI:Quay:100", "quay name")));
        stopPlaceUpdaterService.addStopPlaceQuayAssociations(Map.of("MOBIITI:StopPlace:200", List.of("MOBIITI:Quay:100")));
        stopPlaceUpdaterService.addStopPlaceReverseMappings(Map.of("MOBIITI:StopPlace:200", Set.of("DAT1:StopPlace:PLACE1")));

        Content content = new Content();
        content.setStopPointRefs(List.of("QUAY1"));

        GeneralMessage generalMessage = buildGeneralMessage("SIT1", "Perturbation", content);

        PtSituationElement result = resolver.map(generalMessage, "DAT1");

        assertThat(result.getAffects().getStopPlaces().getAffectedStopPlaces()).hasSize(1);
        assertThat(result.getAffects().getStopPlaces().getAffectedStopPlaces().getFirst().getStopPlaceRef().getValue())
                .isEqualTo("PLACE1");
        assertThat(result.getAffects().getStopPoints().getAffectedStopPoints().getFirst().getStopPointRef().getValue())
                .isEqualTo("QUAY1");
    }

    @Test
    void map_with_only_stopPointRefs_and_no_idProcessingParameters_produces_no_stopPlaces() {
        Content content = new Content();
        content.setStopPointRefs(List.of("QUAY1"));

        GeneralMessage generalMessage = buildGeneralMessage("SIT1", "Perturbation", content);

        PtSituationElement result = resolver.map(generalMessage, "DAT1");

        assertThat(result.getAffects().getStopPlaces()).isNull();
    }

    @Test
    void map_with_blank_datasetId_returns_participantRef_with_null_value() {
        GeneralMessage generalMessage = buildGeneralMessage("SIT1", "Perturbation", null);

        PtSituationElement result = resolver.map(generalMessage, "");

        assertThat(result.getParticipantRef().getValue()).isNull();
    }

    private static GeneralMessage buildGeneralMessage(String situationNumber, String infoChannelRef, Content content) {
        GeneralMessage generalMessage = new GeneralMessage();

        InfoMessageRefStructure infoMessageIdentifier = new InfoMessageRefStructure();
        infoMessageIdentifier.setValue(situationNumber);
        generalMessage.setInfoMessageIdentifier(infoMessageIdentifier);

        InfoChannelRefStructure infoChannelRefStructure = new InfoChannelRefStructure();
        infoChannelRefStructure.setValue(infoChannelRef);
        generalMessage.setInfoChannelRef(infoChannelRefStructure);

        generalMessage.setRecordedAtTime(RECORDED_AT_TIME);
        generalMessage.setValidUntilTime(VALID_UNTIL_TIME);
        generalMessage.setContent(content);

        return generalMessage;
    }
}
