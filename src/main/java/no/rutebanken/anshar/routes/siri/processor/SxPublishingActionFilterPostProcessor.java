package no.rutebanken.anshar.routes.siri.processor;


import lombok.extern.slf4j.Slf4j;

import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import org.apache.commons.collections4.CollectionUtils;
import uk.org.siri.siri21.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


@Slf4j
public class SxPublishingActionFilterPostProcessor extends ValueAdapter implements PostProcessor {

    private final String publishingActionName;

    public SxPublishingActionFilterPostProcessor(String publishingActionName) {
        this.publishingActionName = publishingActionName;
    }

    @Override
    public void process(Siri siri) {
        if (siri == null || siri.getServiceDelivery() == null || siri.getServiceDelivery().getSituationExchangeDeliveries() == null || siri.getServiceDelivery().getSituationExchangeDeliveries().isEmpty()) {
            return;
        }


        for (SituationExchangeDeliveryStructure situationExchangeDelivery : siri.getServiceDelivery().getSituationExchangeDeliveries()) {

            List<PtSituationElement> filteredSituations = new ArrayList<>();

            if (situationExchangeDelivery.getSituations() == null || CollectionUtils.isEmpty(situationExchangeDelivery.getSituations().getPtSituationElements())) {
                continue;
            }

            for (PtSituationElement ptSituationElement : situationExchangeDelivery.getSituations().getPtSituationElements()) {
                if (ptSituationElement.getPublishingActions() == null) {
                    continue;
                }

                Optional<String> promptOpt = getPromptRelatedToName(ptSituationElement);

                promptOpt.ifPresent(prompt -> {
                    ptSituationElement.getDescriptions().clear();
                    DefaultedTextStructure newDesc = new DefaultedTextStructure();
                    newDesc.setValue(prompt);
                    ptSituationElement.getDescriptions().add(newDesc);
                    filteredSituations.add(ptSituationElement);
                });

            }

            situationExchangeDelivery.getSituations().getPtSituationElements().clear();
            situationExchangeDelivery.getSituations().getPtSituationElements().addAll(filteredSituations);
        }
    }

    private Optional<String> getPromptRelatedToName(PtSituationElement ptSituationElement) {
        if (ptSituationElement.getPublishingActions() == null) {
            return Optional.empty();
        }

        ActionsStructure publishingActions = ptSituationElement.getPublishingActions();

        if (publishingActions.getPublishToTvActions() != null) {
            for (PublishToTvAction publishAction : ptSituationElement.getPublishingActions().getPublishToTvActions()) {
                Optional<String> promptOpt = getPromptRelatedToName(publishAction);
                if (promptOpt.isPresent()) {
                    return promptOpt;
                }
            }
        }

        if (publishingActions.getPublishToAlertsActions() != null) {
            for (PublishToAlertsAction publishAction : ptSituationElement.getPublishingActions().getPublishToAlertsActions()) {
                Optional<String> promptOpt = getPromptRelatedToName(publishAction);
                if (promptOpt.isPresent()) {
                    return promptOpt;
                }
            }
        }

        if (publishingActions.getPublishToDisplayActions() != null) {
            for (PublishToDisplayAction publishAction : ptSituationElement.getPublishingActions().getPublishToDisplayActions()) {
                Optional<String> promptOpt = getPromptRelatedToName(publishAction);
                if (promptOpt.isPresent()) {
                    return promptOpt;
                }
            }
        }

        if (publishingActions.getPublishToMobileActions() != null) {
            for (PublishToMobileAction publishAction : ptSituationElement.getPublishingActions().getPublishToMobileActions()) {
                Optional<String> promptOpt = getPromptRelatedToName(publishAction);
                if (promptOpt.isPresent()) {
                    return promptOpt;
                }
            }
        }

        if (publishingActions.getPublishToWebActions() != null) {
            for (PublishToWebAction publishAction : ptSituationElement.getPublishingActions().getPublishToWebActions()) {
                Optional<String> promptOpt = getPromptRelatedToName(publishAction);
                if (promptOpt.isPresent()) {
                    return promptOpt;
                }
            }
        }

        return Optional.empty();

    }

    private Optional<String> getPromptRelatedToName(ParameterisedActionStructure actionStruct) {
        if (actionStruct.getActionDatas().isEmpty() || actionStruct.getActionDatas().getFirst().getPrompts().isEmpty() || !publishingActionName.equals(actionStruct.getActionDatas().getFirst().getName())) {
            return Optional.empty();
        }

        return Optional.ofNullable(actionStruct.getActionDatas().getFirst().getPrompts().getFirst().getValue());
    }


    @Override
    protected String apply(String value) {
        return null;
    }


}

