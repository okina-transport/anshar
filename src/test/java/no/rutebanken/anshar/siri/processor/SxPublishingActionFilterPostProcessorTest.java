package no.rutebanken.anshar.siri.processor;

import no.rutebanken.anshar.routes.siri.processor.SxPublishingActionFilterPostProcessor;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.ActionDataStructure;
import uk.org.siri.siri21.ActionsStructure;
import uk.org.siri.siri21.DefaultedTextStructure;
import uk.org.siri.siri21.NaturalLanguageStringStructure;
import uk.org.siri.siri21.ParameterisedActionStructure;
import uk.org.siri.siri21.PtSituationElement;
import uk.org.siri.siri21.PublishToAlertsAction;
import uk.org.siri.siri21.PublishToDisplayAction;
import uk.org.siri.siri21.PublishToMobileAction;
import uk.org.siri.siri21.PublishToTvAction;
import uk.org.siri.siri21.PublishToWebAction;
import uk.org.siri.siri21.ServiceDelivery;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.SituationExchangeDeliveryStructure;

import static org.assertj.core.api.Assertions.assertThat;

class SxPublishingActionFilterPostProcessorTest {

    private static final String MATCHING_ACTION_NAME = "LUM_PUBLISHING_ACTION";
    private static final String OTHER_ACTION_NAME = "OTHER_PUBLISHING_ACTION";

    SxPublishingActionFilterPostProcessor tested = new SxPublishingActionFilterPostProcessor(MATCHING_ACTION_NAME);

    @Test
    void test_whenSiriIsNull_shouldNotThrow() {
        tested.process(null);
    }

    @Test
    void test_whenServiceDeliveryIsNull_shouldNotThrow() {
        tested.process(new Siri());
    }

    @Test
    void test_whenSituationExchangeDeliveriesIsEmpty_shouldNotThrow() {
        Siri siri = new Siri();
        siri.setServiceDelivery(new ServiceDelivery());

        tested.process(siri);
    }

    @Test
    void test_whenPtSituationElementsIsEmpty_shouldNotThrow() {
        Siri siri = buildSiriWithSituations();

        tested.process(siri);

        assertThat(firstDelivery(siri).getSituations().getPtSituationElements()).isEmpty();
    }

    @Test
    void test_whenPtSituationElementHasNoPublishingActions_shouldBeRemoved() {
        PtSituationElement situation = new PtSituationElement();
        Siri siri = buildSiriWithSituations(situation);

        tested.process(siri);

        assertThat(firstDelivery(siri).getSituations().getPtSituationElements()).isEmpty();
    }

    @Test
    void test_whenPublishToWebActionMatchesName_shouldReplaceDescriptionAndKeepSituation() {
        PublishToWebAction action = buildAction(new PublishToWebAction(), MATCHING_ACTION_NAME, "Prompt web");
        PtSituationElement situation = situationWithWebActions(action);
        Siri siri = buildSiriWithSituations(situation);

        tested.process(siri);

        assertThat(firstDelivery(siri).getSituations().getPtSituationElements()).containsExactly(situation);
        assertThat(situation.getDescriptions()).extracting(DefaultedTextStructure::getValue).containsExactly("Prompt web");
    }

    @Test
    void test_whenPublishToTvActionMatchesName_shouldReplaceDescriptionAndKeepSituation() {
        PublishToTvAction action = buildAction(new PublishToTvAction(), MATCHING_ACTION_NAME, "Prompt tv");
        ActionsStructure actionsStructure = new ActionsStructure();
        actionsStructure.getPublishToTvActions().add(action);
        PtSituationElement situation = new PtSituationElement();
        situation.setPublishingActions(actionsStructure);
        Siri siri = buildSiriWithSituations(situation);

        tested.process(siri);

        assertThat(firstDelivery(siri).getSituations().getPtSituationElements()).containsExactly(situation);
        assertThat(situation.getDescriptions()).extracting(DefaultedTextStructure::getValue).containsExactly("Prompt tv");
    }

    @Test
    void test_whenPublishToAlertsActionMatchesName_shouldReplaceDescriptionAndKeepSituation() {
        PublishToAlertsAction action = buildAction(new PublishToAlertsAction(), MATCHING_ACTION_NAME, "Prompt alerts");
        ActionsStructure actionsStructure = new ActionsStructure();
        actionsStructure.getPublishToAlertsActions().add(action);
        PtSituationElement situation = new PtSituationElement();
        situation.setPublishingActions(actionsStructure);
        Siri siri = buildSiriWithSituations(situation);

        tested.process(siri);

        assertThat(firstDelivery(siri).getSituations().getPtSituationElements()).containsExactly(situation);
        assertThat(situation.getDescriptions()).extracting(DefaultedTextStructure::getValue).containsExactly("Prompt alerts");
    }

    @Test
    void test_whenPublishToDisplayActionMatchesName_shouldReplaceDescriptionAndKeepSituation() {
        PublishToDisplayAction action = buildAction(new PublishToDisplayAction(), MATCHING_ACTION_NAME, "Prompt display");
        ActionsStructure actionsStructure = new ActionsStructure();
        actionsStructure.getPublishToDisplayActions().add(action);
        PtSituationElement situation = new PtSituationElement();
        situation.setPublishingActions(actionsStructure);
        Siri siri = buildSiriWithSituations(situation);

        tested.process(siri);

        assertThat(firstDelivery(siri).getSituations().getPtSituationElements()).containsExactly(situation);
        assertThat(situation.getDescriptions()).extracting(DefaultedTextStructure::getValue).containsExactly("Prompt display");
    }

    @Test
    void test_whenPublishToMobileActionMatchesName_shouldReplaceDescriptionAndKeepSituation() {
        PublishToMobileAction action = buildAction(new PublishToMobileAction(), MATCHING_ACTION_NAME, "Prompt mobile");
        ActionsStructure actionsStructure = new ActionsStructure();
        actionsStructure.getPublishToMobileActions().add(action);
        PtSituationElement situation = new PtSituationElement();
        situation.setPublishingActions(actionsStructure);
        Siri siri = buildSiriWithSituations(situation);

        tested.process(siri);

        assertThat(firstDelivery(siri).getSituations().getPtSituationElements()).containsExactly(situation);
        assertThat(situation.getDescriptions()).extracting(DefaultedTextStructure::getValue).containsExactly("Prompt mobile");
    }

    @Test
    void test_whenActionNameDoesNotMatch_shouldRemoveSituation() {
        PublishToWebAction action = buildAction(new PublishToWebAction(), OTHER_ACTION_NAME, "Prompt web");
        PtSituationElement situation = situationWithWebActions(action);
        Siri siri = buildSiriWithSituations(situation);

        tested.process(siri);

        assertThat(firstDelivery(siri).getSituations().getPtSituationElements()).isEmpty();
    }

    @Test
    void test_whenActionDataHasNoPrompts_shouldRemoveSituation() {
        PublishToWebAction action = new PublishToWebAction();
        ActionDataStructure actionData = new ActionDataStructure();
        actionData.setName(MATCHING_ACTION_NAME);
        action.getActionDatas().add(actionData);
        PtSituationElement situation = situationWithWebActions(action);
        Siri siri = buildSiriWithSituations(situation);

        tested.process(siri);

        assertThat(firstDelivery(siri).getSituations().getPtSituationElements()).isEmpty();
    }

    @Test
    void test_whenActionDatasIsEmpty_shouldRemoveSituation() {
        PublishToWebAction action = new PublishToWebAction();
        PtSituationElement situation = situationWithWebActions(action);
        Siri siri = buildSiriWithSituations(situation);

        tested.process(siri);

        assertThat(firstDelivery(siri).getSituations().getPtSituationElements()).isEmpty();
    }

    @Test
    void test_whenPromptValueIsNull_shouldRemoveSituation() {
        PublishToWebAction action = buildAction(new PublishToWebAction(), MATCHING_ACTION_NAME, null);
        PtSituationElement situation = situationWithWebActions(action);
        DefaultedTextStructure oldDescription = new DefaultedTextStructure();
        oldDescription.setValue("Old description");
        situation.getDescriptions().add(oldDescription);
        Siri siri = buildSiriWithSituations(situation);

        tested.process(siri);

        assertThat(firstDelivery(siri).getSituations().getPtSituationElements()).isEmpty();
        assertThat(situation.getDescriptions()).extracting(DefaultedTextStructure::getValue).containsExactly("Old description");
    }

    @Test
    void test_whenFirstMatchingPromptValueIsNull_shouldUseNextMatchingPrompt() {
        PublishToWebAction nullPrompt = buildAction(new PublishToWebAction(), MATCHING_ACTION_NAME, null);
        PublishToWebAction validPrompt = buildAction(new PublishToWebAction(), MATCHING_ACTION_NAME, "Prompt valid");
        PtSituationElement situation = situationWithWebActions(nullPrompt, validPrompt);
        Siri siri = buildSiriWithSituations(situation);

        tested.process(siri);

        assertThat(firstDelivery(siri).getSituations().getPtSituationElements()).containsExactly(situation);
        assertThat(situation.getDescriptions()).extracting(DefaultedTextStructure::getValue).containsExactly("Prompt valid");
    }

    @Test
    void test_whenSeveralPublishToWebActionsAndOnlySecondMatches_shouldUseMatchingPrompt() {
        PublishToWebAction nonMatching = buildAction(new PublishToWebAction(), OTHER_ACTION_NAME, "Prompt other");
        PublishToWebAction matching = buildAction(new PublishToWebAction(), MATCHING_ACTION_NAME, "Prompt matching");
        PtSituationElement situation = situationWithWebActions(nonMatching, matching);
        Siri siri = buildSiriWithSituations(situation);

        tested.process(siri);

        assertThat(firstDelivery(siri).getSituations().getPtSituationElements()).containsExactly(situation);
        assertThat(situation.getDescriptions()).extracting(DefaultedTextStructure::getValue).containsExactly("Prompt matching");
    }

    @Test
    void test_whenExistingDescriptionsArePresent_shouldBeReplacedByPrompt() {
        PublishToWebAction action = buildAction(new PublishToWebAction(), MATCHING_ACTION_NAME, "New prompt");
        PtSituationElement situation = situationWithWebActions(action);
        DefaultedTextStructure oldDescription = new DefaultedTextStructure();
        oldDescription.setValue("Old description");
        situation.getDescriptions().add(oldDescription);
        Siri siri = buildSiriWithSituations(situation);

        tested.process(siri);

        assertThat(situation.getDescriptions()).extracting(DefaultedTextStructure::getValue).containsExactly("New prompt");
    }

    @Test
    void test_whenMultipleSituationsInSameDelivery_shouldKeepOnlyMatchingOnes() {
        PublishToWebAction matchingAction = buildAction(new PublishToWebAction(), MATCHING_ACTION_NAME, "Prompt matching");
        PtSituationElement matchingSituation = situationWithWebActions(matchingAction);

        PublishToWebAction nonMatchingAction = buildAction(new PublishToWebAction(), OTHER_ACTION_NAME, "Prompt other");
        PtSituationElement nonMatchingSituation = situationWithWebActions(nonMatchingAction);

        Siri siri = buildSiriWithSituations(matchingSituation, nonMatchingSituation);

        tested.process(siri);

        assertThat(firstDelivery(siri).getSituations().getPtSituationElements()).containsExactly(matchingSituation);
    }


    private <T extends ParameterisedActionStructure> T buildAction(T action, String actionDataName, String promptValue) {
        ActionDataStructure actionData = new ActionDataStructure();
        actionData.setName(actionDataName);
        NaturalLanguageStringStructure prompt = new NaturalLanguageStringStructure();
        prompt.setValue(promptValue);
        actionData.getPrompts().add(prompt);
        action.getActionDatas().add(actionData);
        return action;
    }

    private PtSituationElement situationWithWebActions(PublishToWebAction... actions) {
        ActionsStructure actionsStructure = new ActionsStructure();
        for (PublishToWebAction action : actions) {
            actionsStructure.getPublishToWebActions().add(action);
        }
        PtSituationElement situation = new PtSituationElement();
        situation.setPublishingActions(actionsStructure);
        return situation;
    }

    private Siri buildSiriWithSituations(PtSituationElement... situations) {
        SituationExchangeDeliveryStructure.Situations situationsWrapper = new SituationExchangeDeliveryStructure.Situations();
        for (PtSituationElement situation : situations) {
            situationsWrapper.getPtSituationElements().add(situation);
        }
        SituationExchangeDeliveryStructure delivery = new SituationExchangeDeliveryStructure();
        delivery.setSituations(situationsWrapper);

        ServiceDelivery serviceDelivery = new ServiceDelivery();
        serviceDelivery.getSituationExchangeDeliveries().add(delivery);

        Siri siri = new Siri();
        siri.setServiceDelivery(serviceDelivery);
        return siri;
    }

    private SituationExchangeDeliveryStructure firstDelivery(Siri siri) {
        return siri.getServiceDelivery().getSituationExchangeDeliveries().getFirst();
    }
}
