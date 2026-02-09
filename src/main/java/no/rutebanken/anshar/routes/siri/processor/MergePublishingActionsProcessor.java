package no.rutebanken.anshar.routes.siri.processor;

import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import uk.org.siri.siri21.*;

import java.util.List;

@Slf4j
public class MergePublishingActionsProcessor extends ValueAdapter implements PostProcessor {
    @Override
    public void process(Siri siri) {

        if (siri.getServiceDelivery() == null || CollectionUtils.isEmpty(siri.getServiceDelivery().getSituationExchangeDeliveries())) {
            return;
        }

        siri.getServiceDelivery().getSituationExchangeDeliveries().forEach(this::mergePublishingActions);
    }

    private void mergePublishingActions(SituationExchangeDeliveryStructure situationExchangeDeliveryStructure) {
        if (situationExchangeDeliveryStructure.getSituations() == null || CollectionUtils.isEmpty(situationExchangeDeliveryStructure.getSituations().getPtSituationElements())) {
            return;
        }
        situationExchangeDeliveryStructure.getSituations().getPtSituationElements().forEach(this::mergePublishingActions);

    }

    private void mergePublishingActions(PtSituationElement ptSituationElement) {
        ActionsStructure originalPublishingActions = ptSituationElement.getPublishingActions();
        if (originalPublishingActions == null || CollectionUtils.isEmpty(originalPublishingActions.getPublishToDisplayActions())) {
            return;
        }

        ActionsStructure newPublishingActions = new ActionsStructure();
        List<PublishToDisplayAction> originalPublishToDisplayActions = originalPublishingActions.getPublishToDisplayActions();

        for (PublishToDisplayAction originalPublishToDisplayAction : originalPublishToDisplayActions) {
            if (!isAMessage(originalPublishToDisplayAction)) {
                continue;
            }

            // creating a new publishToDisplay action to merge "message" action and "title" action
            PublishToDisplayAction mergedPublishToDisplayAction = new PublishToDisplayAction();
            String title = getTitle(originalPublishToDisplayAction, originalPublishToDisplayActions);
            ActionDataStructure actionData = new ActionDataStructure();
            actionData.setName(title);
            NaturalLanguageStringStructure prompt = new NaturalLanguageStringStructure();
            prompt.setValue(getPrompt(originalPublishToDisplayAction));
            actionData.getPrompts().add(prompt);
            mergedPublishToDisplayAction.getActionDatas().add(actionData);

            String messageActionCode = getName(originalPublishToDisplayAction);

            if (messageActionCode.contains("_SOL")) {
                mergedPublishToDisplayAction.setOnBoard(false);
                mergedPublishToDisplayAction.setOnPlace(true);
            } else if (messageActionCode.contains("_EMB")) {
                mergedPublishToDisplayAction.setOnBoard(true);
                mergedPublishToDisplayAction.setOnPlace(false);
            }
            newPublishingActions.getPublishToDisplayActions().add(mergedPublishToDisplayAction);
        }

        ptSituationElement.setPublishingActions(newPublishingActions);
    }

    private String getName(PublishToDisplayAction originalPublishToDisplayAction) {
        return CollectionUtils.isEmpty(originalPublishToDisplayAction.getActionDatas()) || StringUtils.isEmpty(originalPublishToDisplayAction.getActionDatas().getFirst().getName()) ? "" :
                originalPublishToDisplayAction.getActionDatas().getFirst().getName();

    }


    private String getPrompt(PublishToDisplayAction originalPublishToDisplayAction) {
        return CollectionUtils.isEmpty(originalPublishToDisplayAction.getActionDatas()) || CollectionUtils.isEmpty(originalPublishToDisplayAction.getActionDatas().getFirst().getPrompts()) ? "" :
                originalPublishToDisplayAction.getActionDatas().getFirst().getPrompts().getFirst().getValue();

    }

    private String getTitle(PublishToDisplayAction messageAction, List<PublishToDisplayAction> originalPublishToDisplayActions) {

        if (CollectionUtils.isEmpty(messageAction.getActionDatas())) {
            return null;
        }

        String actionNameCode = messageAction.getActionDatas().getFirst().getName();
        String actionTitleCode = actionNameCode.substring(0, actionNameCode.length() - 1) + "T";

        for (PublishToDisplayAction publishToDisplayAction : originalPublishToDisplayActions) {
            if (CollectionUtils.isEmpty(publishToDisplayAction.getActionDatas())) {
                continue;
            }

            if (actionTitleCode.equals(publishToDisplayAction.getActionDatas().getFirst().getName())) {
                List<NaturalLanguageStringStructure> prompts = publishToDisplayAction.getActionDatas().getFirst().getPrompts();
                if (CollectionUtils.isNotEmpty(prompts)) {
                    return prompts.getFirst().getValue();
                }
            }
        }
        return null;
    }

    private boolean isAMessage(PublishToDisplayAction publishToDisplayAction) {
        return publishToDisplayAction != null && CollectionUtils.isNotEmpty(publishToDisplayAction.getActionDatas()) && publishToDisplayAction.getActionDatas().getFirst().getName().endsWith("M");
    }

    @Override
    protected String apply(String value) {
        return "";
    }
}
