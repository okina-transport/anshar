package no.rutebanken.anshar.logging;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LogContentDto {

    @JsonProperty("metadata")
    private String metadata;

    @JsonProperty("object_before")
    private String objectBefore;

    @JsonProperty("object_after")
    private String objectAfter;
}