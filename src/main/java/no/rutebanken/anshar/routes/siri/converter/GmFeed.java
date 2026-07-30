package no.rutebanken.anshar.routes.siri.converter;

import no.rutebanken.anshar.ishtar.model.PublishToDisplayAction;
import uk.org.siri.siri21.GeneralMessage;
import uk.org.siri.siri21.GeneralMessageCancellation;

import java.util.List;

public record GmFeed(String datasetId, List<GeneralMessage> generalMessage, List<GeneralMessageCancellation> cancellations, PublishToDisplayAction publishToDisplayAction) {
}
