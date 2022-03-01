package no.rutebanken.anshar.okinaDisruptions.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class OkinaVehicleJourney extends NeptuneIdentifiedObject{

    private String name;
    private String publishedJourneyName;
    private OkinaRoute okinaRoute;


}
