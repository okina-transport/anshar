package no.rutebanken.anshar.routes.health;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum IncomingFlowType {
    GTFS("GTFS-RT"),
    SIRI("SIRI");

    @JsonValue
    private final String code;

    IncomingFlowType(String code) {
        this.code = code;
    }
}