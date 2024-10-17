package no.rutebanken.anshar.ishtar.converter;

import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.api.GtfsRTApi;
import no.rutebanken.anshar.config.GTFSRTType;
import no.rutebanken.anshar.ishtar.model.GtfsRTApiDto;
import org.springframework.core.convert.converter.Converter;

/**
 * Convert ISHTAR GtfsRTApiDto into ANSHAR GtfsRTApi.
 */
@Slf4j
public class GtfsRTApiDtoConverter implements Converter<GtfsRTApiDto, GtfsRTApi> {

    @Override
    public GtfsRTApi convert(GtfsRTApiDto source) {
        log.debug("source: {}", source);
        GtfsRTApi target = new GtfsRTApi();
        target.setActive(source.getActive());
        target.setUrl(source.getUrl());
        target.setDatasetId(source.getDatasetId());
        target.setType(GTFSRTType.valueOf(source.getType()));
        log.debug("target: {}", target);
        return target;
    }

}
