package no.rutebanken.anshar.ishtar.requestlogging.model;

import lombok.Data;

import java.util.Map;

@Data
public class HttpRequestDto {

    String method;
    String url;
    String body;
    Map<String, Object> headers;

}
