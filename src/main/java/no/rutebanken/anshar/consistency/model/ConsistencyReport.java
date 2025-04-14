package no.rutebanken.anshar.consistency.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.ZonedDateTimeSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import no.rutebanken.anshar.subscription.SiriDataType;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class ConsistencyReport {

    @JsonSerialize(using = ZonedDateTimeSerializer.class)
    private ZonedDateTime start;                                            // start timestamp of consistency report generation
    @JsonSerialize(using = ZonedDateTimeSerializer.class)
    private ZonedDateTime end;                                              // end timestamp of consistency report generation
    private String dataset;                                                 // dataset concerned by this consistency report
    private Map<SiriDataType, Consistency> consistencies = new HashMap<>(); // consistencies by SIRI type

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Consistency {
        private MatchResult lines;
        private MatchResult stops;
        private MatchResult vehicleJourneys;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MatchResult {
        private int nbMatch;                                    // nb of matched ids from TH & RT
        private List<String> unmatchedIds = new ArrayList<>();  // unmatched ids
    }
}
