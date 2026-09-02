package no.rutebanken.anshar.mapping;

import jakarta.xml.bind.JAXBException;

import no.rutebanken.anshar.data.util.CustomSiriXml;
import no.rutebanken.anshar.routes.siri.processor.FillPublishToDisplayActionsProcessor;


import org.apache.commons.collections4.CollectionUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.*;

import javax.xml.transform.TransformerException;
import java.io.FileNotFoundException;
import java.util.List;

public class FillPublishToDisplayActionsTest {

    @Test
    public void test_no_error_on_empty_siri() {
        FillPublishToDisplayActionsProcessor processor = new FillPublishToDisplayActionsProcessor();
        Siri siri = new Siri();
        processor.process(siri);
    }

    @Test
    public void test_no_error_when_publishing_actions_is_null() {
        FillPublishToDisplayActionsProcessor processor = new FillPublishToDisplayActionsProcessor();
        Siri siri = buildSiriWithSituation(new PtSituationElement());
        processor.process(siri);

        PtSituationElement situation = firstSituation(siri);
        Assertions.assertNull(situation.getPublishingActions());
    }

    @Test
    public void test_no_error_when_publish_to_display_actions_is_empty() {
        FillPublishToDisplayActionsProcessor processor = new FillPublishToDisplayActionsProcessor();
        PtSituationElement situation = new PtSituationElement();
        situation.setPublishingActions(new ActionsStructure());
        Siri siri = buildSiriWithSituation(situation);

        processor.process(siri);

        Assertions.assertTrue(CollectionUtils.isEmpty(situation.getPublishingActions().getPublishToDisplayActions()));
    }

    @Test
    public void test_action_data_name_containing_SOL_sets_on_place() {
        PublishToDisplayAction action = buildPublishToDisplayAction("LUM_TEST_SOLM", "Ceci est le message d'une perturbation");
        PtSituationElement situation = buildSituation(action);
        Siri siri = buildSiriWithSituation(situation);

        new FillPublishToDisplayActionsProcessor().process(siri);

        Assertions.assertEquals(Boolean.TRUE, action.isOnPlace());
        Assertions.assertEquals(Boolean.FALSE, action.isOnBoard());
    }

    @Test
    public void test_action_data_name_containing_EMB_sets_on_board() {
        PublishToDisplayAction action = buildPublishToDisplayAction("LUM_TEST_EMBM", "Perturbation à prévoir");
        PtSituationElement situation = buildSituation(action);
        Siri siri = buildSiriWithSituation(situation);

        new FillPublishToDisplayActionsProcessor().process(siri);

        Assertions.assertEquals(Boolean.TRUE, action.isOnBoard());
        Assertions.assertEquals(Boolean.FALSE, action.isOnPlace());
    }

    @Test
    public void test_action_data_name_without_SOL_or_EMB_is_left_untouched() {
        PublishToDisplayAction action = buildPublishToDisplayAction("LUM_TEST_OTHER", "Message quelconque");
        PtSituationElement situation = buildSituation(action);
        Siri siri = buildSiriWithSituation(situation);

        new FillPublishToDisplayActionsProcessor().process(siri);

        Assertions.assertNull(action.isOnPlace());
        Assertions.assertNull(action.isOnBoard());
    }

    @Test
    public void test_action_data_with_empty_name_is_skipped() {
        PublishToDisplayAction action = new PublishToDisplayAction();
        ActionDataStructure actionData = new ActionDataStructure();
        actionData.setName("");
        action.getActionDatas().add(actionData);
        PtSituationElement situation = buildSituation(action);
        Siri siri = buildSiriWithSituation(situation);

        new FillPublishToDisplayActionsProcessor().process(siri);

        Assertions.assertNull(action.isOnPlace());
        Assertions.assertNull(action.isOnBoard());
    }

    @Test
    public void test_publish_to_display_action_without_action_data_is_ignored() {
        PublishToDisplayAction action = new PublishToDisplayAction();
        PtSituationElement situation = buildSituation(action);
        Siri siri = buildSiriWithSituation(situation);

        new FillPublishToDisplayActionsProcessor().process(siri);

        Assertions.assertNull(action.isOnPlace());
        Assertions.assertNull(action.isOnBoard());
    }

    @Test
    public void test_action_datas_are_preserved_and_not_merged() {
        // With the new behaviour, actions are updated in place: title and message
        // action data are no longer merged into a single new action.
        PublishToDisplayAction embMessage = buildPublishToDisplayAction("LUM_TEST_EMBM", "Perturbation à prévoir");
        PublishToDisplayAction embTitle = buildPublishToDisplayAction("LUM_TEST_EMBT", "F2 INCIDENT");
        PublishToDisplayAction solMessage = buildPublishToDisplayAction("LUM_TEST_SOLM", "Ceci est le message d'une perturbation");
        PublishToDisplayAction solTitle = buildPublishToDisplayAction("LUM_TEST_SOLT", "Ceci est le titre d'une perturbation");

        ActionsStructure actionsStructure = new ActionsStructure();
        actionsStructure.getPublishToDisplayActions().add(embMessage);
        actionsStructure.getPublishToDisplayActions().add(embTitle);
        actionsStructure.getPublishToDisplayActions().add(solMessage);
        actionsStructure.getPublishToDisplayActions().add(solTitle);

        PtSituationElement situation = new PtSituationElement();
        situation.setPublishingActions(actionsStructure);
        Siri siri = buildSiriWithSituation(situation);

        new FillPublishToDisplayActionsProcessor().process(siri);

        // the 4 incoming actions must remain 4 actions after processing
        Assertions.assertEquals(4, situation.getPublishingActions().getPublishToDisplayActions().size());

        Assertions.assertEquals(Boolean.TRUE, embMessage.isOnBoard());
        Assertions.assertEquals(Boolean.FALSE, embMessage.isOnPlace());
        Assertions.assertEquals("Perturbation à prévoir", embMessage.getActionDatas().getFirst().getPrompts().getFirst().getValue());

        Assertions.assertEquals(Boolean.TRUE, embTitle.isOnBoard());
        Assertions.assertEquals(Boolean.FALSE, embTitle.isOnPlace());
        Assertions.assertEquals("F2 INCIDENT", embTitle.getActionDatas().getFirst().getPrompts().getFirst().getValue());

        Assertions.assertEquals(Boolean.TRUE, solMessage.isOnPlace());
        Assertions.assertEquals(Boolean.FALSE, solMessage.isOnBoard());
        Assertions.assertEquals("Ceci est le message d'une perturbation", solMessage.getActionDatas().getFirst().getPrompts().getFirst().getValue());

        Assertions.assertEquals(Boolean.TRUE, solTitle.isOnPlace());
        Assertions.assertEquals(Boolean.FALSE, solTitle.isOnBoard());
        Assertions.assertEquals("Ceci est le titre d'une perturbation", solTitle.getActionDatas().getFirst().getPrompts().getFirst().getValue());
    }

    @Test
    public void test_last_matching_action_data_wins_when_action_has_several_action_datas() {
        PublishToDisplayAction action = new PublishToDisplayAction();
        action.getActionDatas().add(actionData("LUM_TEST_SOLM"));
        action.getActionDatas().add(actionData("LUM_TEST_EMBM"));
        PtSituationElement situation = buildSituation(action);
        Siri siri = buildSiriWithSituation(situation);

        new FillPublishToDisplayActionsProcessor().process(siri);

        Assertions.assertEquals(Boolean.TRUE, action.isOnBoard());
        Assertions.assertEquals(Boolean.FALSE, action.isOnPlace());
    }

    @Test
    public void test_can_serialize_to_xml_after_processing() throws JAXBException, FileNotFoundException, TransformerException {
        PublishToDisplayAction action = buildPublishToDisplayAction("LUM_TEST_SOLM", "Ceci est le message d'une perturbation");
        PtSituationElement situation = buildSituation(action);
        Siri siri = buildSiriWithSituation(situation);

        new FillPublishToDisplayActionsProcessor().process(siri);

        String res = CustomSiriXml.toXml(siri);
        String soapMsg = CustomSiriXml.subscriptionRawToSoap(res);

        Assertions.assertTrue(CollectionUtils.isNotEmpty(List.of(res, soapMsg)));
    }

    private PublishToDisplayAction buildPublishToDisplayAction(String actionDataName, String promptValue) {
        PublishToDisplayAction action = new PublishToDisplayAction();
        ActionDataStructure actionData = actionData(actionDataName);
        NaturalLanguageStringStructure prompt = new NaturalLanguageStringStructure();
        prompt.setValue(promptValue);
        actionData.getPrompts().add(prompt);
        action.getActionDatas().add(actionData);
        return action;
    }

    private ActionDataStructure actionData(String name) {
        ActionDataStructure actionData = new ActionDataStructure();
        actionData.setName(name);
        actionData.setType("Text");
        actionData.getValues().add("Message");
        return actionData;
    }

    private PtSituationElement buildSituation(PublishToDisplayAction... actions) {
        ActionsStructure actionsStructure = new ActionsStructure();
        for (PublishToDisplayAction action : actions) {
            actionsStructure.getPublishToDisplayActions().add(action);
        }
        PtSituationElement situation = new PtSituationElement();
        situation.setPublishingActions(actionsStructure);
        return situation;
    }

    private Siri buildSiriWithSituation(PtSituationElement situation) {
        Siri siri = new Siri();
        ServiceDelivery serviceDelivery = new ServiceDelivery();
        SituationExchangeDeliveryStructure structure = new SituationExchangeDeliveryStructure();
        SituationExchangeDeliveryStructure.Situations situations = new SituationExchangeDeliveryStructure.Situations();
        situations.getPtSituationElements().add(situation);
        structure.setSituations(situations);
        serviceDelivery.getSituationExchangeDeliveries().add(structure);
        siri.setServiceDelivery(serviceDelivery);
        return siri;
    }

    private PtSituationElement firstSituation(Siri siri) {
        return siri.getServiceDelivery().getSituationExchangeDeliveries().getFirst()
                .getSituations().getPtSituationElements().getFirst();
    }
}
