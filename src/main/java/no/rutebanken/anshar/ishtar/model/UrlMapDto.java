package no.rutebanken.anshar.ishtar.model;

import lombok.Data;
import no.rutebanken.anshar.subscription.helpers.RequestType;

@Data
public class UrlMapDto {

    private Long id;
    private RequestType name;
    private String url;

}
