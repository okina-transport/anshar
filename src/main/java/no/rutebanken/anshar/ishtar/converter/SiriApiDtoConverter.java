package no.rutebanken.anshar.ishtar.converter;

import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.api.SiriApi;
import no.rutebanken.anshar.ishtar.model.SiriApiDto;
import org.springframework.core.convert.converter.Converter;

/**
 * Convert ISHTAR SiriApiDto into ANSHAR SiriApi.
 */
@Slf4j
public class SiriApiDtoConverter implements Converter<SiriApiDto, SiriApi> {

    @Override
    public SiriApi convert(SiriApiDto source) {
        log.debug("source: {}", source);
        SiriApi target = new SiriApi();
        target.setActive(source.getActive());
        target.setUrl(source.getUrl());
        target.setDatasetId(source.getDatasetId());
        target.setType(source.getType());
        log.debug("target: {}", target);
        return target;
    }

}
