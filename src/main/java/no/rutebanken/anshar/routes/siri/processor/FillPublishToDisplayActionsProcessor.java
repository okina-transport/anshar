package no.rutebanken.anshar.routes.siri.processor;

import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import uk.org.siri.siri21.*;

@Slf4j
public class FillPublishToDisplayActionsProcessor extends ValueAdapter implements PostProcessor {
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

        for (PublishToDisplayAction publishToDisplayAction : originalPublishingActions.getPublishToDisplayActions()) {
            if (!hasActionData(publishToDisplayAction)) {
                continue;
            }

            for (ActionDataStructure actionData : publishToDisplayAction.getActionDatas()) {

                if (StringUtils.isEmpty(actionData.getName())) {
                    continue;
                }

                if (actionData.getName().contains("SOL")) {
                    publishToDisplayAction.setOnBoard(false);
                    publishToDisplayAction.setOnPlace(true);
                } else if (actionData.getName().contains("EMB")) {
                    publishToDisplayAction.setOnBoard(true);
                    publishToDisplayAction.setOnPlace(false);
                }

            }
        }
    }


    private boolean hasActionData(PublishToDisplayAction publishToDisplayAction) {
        return publishToDisplayAction != null && CollectionUtils.isNotEmpty(publishToDisplayAction.getActionDatas());
    }

    @Override
    protected String apply(String value) {
        return "";
    }
}
