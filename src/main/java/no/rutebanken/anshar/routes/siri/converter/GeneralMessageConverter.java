package no.rutebanken.anshar.routes.siri.converter;

import no.rutebanken.anshar.data.SiriObjectStorageKey;
import no.rutebanken.anshar.data.Situations;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;
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
