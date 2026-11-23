package no.rutebanken.anshar.routes.siri.adapters;

import no.rutebanken.anshar.routes.siri.processor.ScheduledStopPointToQuayProcessor;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SubscriptionSetup;

import java.util.ArrayList;
import java.util.List;

@Mapping(id = "resolveQuayFromScheduledStopPoint")
public class ResolveQuayFromScheduledStopPointPreprocess extends MappingAdapter {

    private static final String SNCF_SCHEDULED_STOP_POINT_PREFIX = "FR:ScheduledStopPoint::";

    @Override
    public List<ValueAdapter> getValueAdapters(SubscriptionSetup subscriptionSetup) {
        List<ValueAdapter> valueAdapters = new ArrayList<>();
        valueAdapters.add(new ScheduledStopPointToQuayProcessor(subscriptionSetup.getDatasetId(), SNCF_SCHEDULED_STOP_POINT_PREFIX));
        return valueAdapters;
    }
}
