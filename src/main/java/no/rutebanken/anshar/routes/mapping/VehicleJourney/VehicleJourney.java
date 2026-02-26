package no.rutebanken.anshar.routes.mapping.VehicleJourney;

import lombok.Data;

@Data
public class VehicleJourney {
    private final String vehicleJourneyId;
    private final String dateyyyyMMdd;
    private final String timeHHmmss;
    private final Integer position;
}

