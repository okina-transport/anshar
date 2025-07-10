package no.rutebanken.anshar.routes.health;

import lombok.Data;
import no.rutebanken.anshar.subscription.SiriDataType;

@Data
public class InputSubscriptionData {

    private String dataset;
    private SiriDataType dataType;
    private long nbElements;

}
