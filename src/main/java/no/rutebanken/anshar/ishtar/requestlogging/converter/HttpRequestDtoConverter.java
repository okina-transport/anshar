package no.rutebanken.anshar.ishtar.requestlogging.converter;

import no.rutebanken.anshar.ishtar.model.GtfsRTApiDto;
import no.rutebanken.anshar.ishtar.model.SiriApiDto;
import no.rutebanken.anshar.ishtar.requestlogging.model.HttpRequestDto;
import org.apache.camel.Converter;
import org.apache.camel.TypeConverters;
import org.apache.commons.lang.StringUtils;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class HttpRequestDtoConverter implements TypeConverters {

    @Converter
    public HttpRequestDto toHttpRequestDto(GtfsRTApiDto source) {
        HttpRequestDto target = new HttpRequestDto();
        target.setMethod(HttpMethod.GET.name());
        target.setUrl(source.getUrl());
        target.setBody(StringUtils.EMPTY);
        target.setHeaders(Map.of());
        return target;
    }

    @Converter
    public HttpRequestDto toHttpRequestDto(SiriApiDto source) {
        HttpRequestDto target = new HttpRequestDto();
        target.setMethod(HttpMethod.GET.name());
        target.setUrl(source.getUrl());
        target.setBody(StringUtils.EMPTY);
        target.setHeaders(Map.of());
        return target;
    }

}
