package no.rutebanken.anshar.routes.outbound.model;

import lombok.Data;

@Data
public class ConsumerErrorDTO {

    private String url;
    private int count;

    public ConsumerErrorDTO(String url, int count) {
        this.url = url;
        this.count = count;
    }
}
