package no.rutebanken.anshar.subscription.fakedata;

import no.rutebanken.anshar.routes.outbound.OutboundSubscriptionSetup;
import uk.org.siri.siri21.Siri;

import java.util.List;

public interface FakeDataProducer {

    List<Siri> produce(OutboundSubscriptionSetup sub);

}
