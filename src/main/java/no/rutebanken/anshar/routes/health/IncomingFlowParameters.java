package no.rutebanken.anshar.routes.health;

import lombok.Data;

@Data
public class IncomingFlowParameters {
    private String id;
    private String dataset;
    private String url;
    private IncomingFlowType type;

}
