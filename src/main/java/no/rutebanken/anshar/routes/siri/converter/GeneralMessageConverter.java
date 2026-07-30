package no.rutebanken.anshar.routes.siri.converter;

import no.rutebanken.anshar.data.SiriObjectStorageKey;
import no.rutebanken.anshar.data.Situations;
import no.rutebanken.anshar.ishtar.model.PublishToDisplayAction;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;
import uk.org.siri.siri21.ActionsStructure;
import uk.org.siri.siri21.GeneralMessageCancellation;
import uk.org.siri.siri21.PtSituationElement;
import uk.org.siri.siri21.WorkflowStatusEnumeration;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class GeneralMessageConverter {

    private final GeneralMessageToSituationExchangeResolver gmToSxResolver;

    private final Situations situations;


    public GeneralMessageConverter(GeneralMessageToSituationExchangeResolver gmToSxResolver,
                                   Situations situations) {
        this.gmToSxResolver = gmToSxResolver;
        this.situations = situations;
    }

    public List<PtSituationElement> convertGeneralMessageToSx(GmFeed gmFeed) {
        String datasetId = gmFeed.datasetId();
        List<PtSituationElement> generatedSx = new ArrayList<>();
        List<PtSituationElement> cancelledSx = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(gmFeed.generalMessage())) {
            generatedSx = gmFeed.generalMessage()
                    .stream()
                    .map(gm -> gmToSxResolver.map(gm, datasetId))
                    .collect(Collectors.toList());

            addPublishToDisplayAction(generatedSx, gmFeed.publishToDisplayAction());

        }
        if (CollectionUtils.isNotEmpty(gmFeed.cancellations())) {
            for (GeneralMessageCancellation cancellation : gmFeed.cancellations()) {
                PtSituationElement ptSituationElement = getSxFromCache(cancellation, datasetId);
                if (ptSituationElement != null) {
                    ptSituationElement.setProgress(WorkflowStatusEnumeration.CLOSED);
                    cancelledSx.add(ptSituationElement);
                }
            }
        }
        List<PtSituationElement> situationsToIngest = new ArrayList<>(generatedSx);
        situationsToIngest.addAll(cancelledSx);

        return situationsToIngest;
    }

    private void addPublishToDisplayAction(List<PtSituationElement> generatedSx, PublishToDisplayAction publishToDisplayAction) {
        if (PublishToDisplayAction.NONE.equals(publishToDisplayAction)) {
            return;
        }

        ActionsStructure actionsStructure = new ActionsStructure();
        List<uk.org.siri.siri21.PublishToDisplayAction> publishToDisplayActions = actionsStructure.getPublishToDisplayActions();
        uk.org.siri.siri21.PublishToDisplayAction publishToDisplayActionNetex = buildPublishToDisplayAction(publishToDisplayAction);
        publishToDisplayActions.add(publishToDisplayActionNetex);

        for (PtSituationElement sx : generatedSx) {
            sx.setPublishingActions(actionsStructure);
        }

    }

    private static uk.org.siri.siri21.PublishToDisplayAction buildPublishToDisplayAction(PublishToDisplayAction configuredPublishToDisplayAction) {
        uk.org.siri.siri21.PublishToDisplayAction publishToDisplayAction = new uk.org.siri.siri21.PublishToDisplayAction();

        publishToDisplayAction.setOnPlace(configuredPublishToDisplayAction == PublishToDisplayAction.ON_PLACE_AND_ON_BOARD
                || configuredPublishToDisplayAction == PublishToDisplayAction.ON_PLACE);

        publishToDisplayAction.setOnBoard(configuredPublishToDisplayAction == PublishToDisplayAction.ON_BOARD
                || configuredPublishToDisplayAction == PublishToDisplayAction.ON_PLACE_AND_ON_BOARD);
        return publishToDisplayAction;
    }


    private PtSituationElement getSxFromCache(GeneralMessageCancellation cancellation, String datasetId) {
        SiriObjectStorageKey key;
        String situationNumber = "null";
        if (cancellation.getInfoMessageIdentifier() != null) {
            situationNumber = cancellation.getInfoMessageIdentifier().getValue();
        }
        key = new SiriObjectStorageKey(datasetId, null, String.format("%s:%s", datasetId, situationNumber));
        return situations.getSituationElements().get(key);
    }
}
