package no.rutebanken.anshar.routes.siri.adapters;

import no.rutebanken.anshar.routes.siri.processor.UpdateVJIdProcessor;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SubscriptionSetup;

import java.util.ArrayList;
import java.util.List;

@Mapping(id = "recomputeVehicleJourneyIdFromTH")
public class RecomputeVJIDFromTHPreprocess extends MappingAdapter {
    @Override
    public List<ValueAdapter> getValueAdapters(SubscriptionSetup subscriptionSetup) {
        List<ValueAdapter> valueAdapters = new ArrayList<>();
        valueAdapters.add(new UpdateVJIdProcessor(subscriptionSetup.getDatasetId()));
        return valueAdapters;
    }
}
