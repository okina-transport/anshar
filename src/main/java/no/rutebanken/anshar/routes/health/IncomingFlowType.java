package no.rutebanken.anshar.routes.health;

import lombok.Getter;

@Getter
public enum IncomingFlowType {
    GTFS("GTFS-RT"),
    SIRI("SIRI");

    private final String code;

    IncomingFlowType(String code) {
        this.code = code;
    }
}