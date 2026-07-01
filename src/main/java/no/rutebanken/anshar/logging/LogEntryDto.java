package no.rutebanken.anshar.logging;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
public class LogEntryDto {

    @JsonProperty("event_timestamp")
    private Instant eventTimestamp;

    @JsonProperty("action_type")
    private String actionType;

    @JsonProperty("user")
    private String user;

    @JsonProperty("object_id")
    private String objectId;

    @JsonProperty("organization")
    private String organization;

    @JsonProperty("service")
    private String service;

    @JsonProperty("log_content")
    private LogContentDto logContent;
}