package no.rutebanken.anshar.routes.siri.converter;

import uk.org.siri.siri21.GeneralMessage;
import uk.org.siri.siri21.GeneralMessageCancellation;

import java.util.List;

public record GmFeed(String datasetId, List<GeneralMessage> generalMessage, List<GeneralMessageCancellation> cancellations) {
}
