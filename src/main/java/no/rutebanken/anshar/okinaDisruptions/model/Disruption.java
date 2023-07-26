package no.rutebanken.anshar.okinaDisruptions.model;


import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


/**
 * Defines a disruption on one or more topology elements
 */
@Getter
@Setter
@NoArgsConstructor
public class Disruption implements Serializable {


    private Long id;

    @NotNull
    private String message;
    private String comment;
    private String imgFileName;
    private byte[] imgFileBinary;
    private String organization;

    private LocalDateTime creationDateTime;

    private LocalDateTime startDateTime;

    private LocalDateTime endDateTime;

    private LocalDateTime deleteDateTime;

    private LocalDateTime publicationStartDateTime;

    private LocalDateTime publicationEndDateTime;

    private String category;
    private String reason;
    private String severity;
    private String effect;

    @Getter
    @Setter
    private List<OkinaLine> lines = new ArrayList<OkinaLine>(0);

    @Getter
    @Setter
    private List<OkinaNetwork> networks = new ArrayList<OkinaNetwork>(0);


    @Getter
    @Setter
    private List<JourneyPattern> journeys = new ArrayList<JourneyPattern>(0);


    @Getter
    @Setter
    private List<OkinaVehicleJourney> courses = new ArrayList<OkinaVehicleJourney>(0);


    @Getter
    @Setter
    private List<OkinaStopArea> stopAreas = new ArrayList<OkinaStopArea>(0);

    @Getter
    @Setter
    private List<DisruptionPeriod> periods = new ArrayList<DisruptionPeriod>(0);




}

