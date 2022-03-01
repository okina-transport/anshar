package no.rutebanken.anshar.okinaDisruptions.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class JourneyPattern  extends NeptuneIdentifiedObject {

    private String name;
    private String comment;
    private String registrationNumber;
    private String publishedName;
    private SectionStatusEnum sectionStatus;
    private List<OkinaVehicleJourney> vehicleJourneys = new ArrayList<>(0);


}
