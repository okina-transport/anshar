package no.rutebanken.anshar.ishtar.converter;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.api.GtfsRTApi;
import no.rutebanken.anshar.api.PublishedLineNameMapping;
import no.rutebanken.anshar.config.GTFSRTType;
import no.rutebanken.anshar.ishtar.model.GtfsRTApiDto;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.core.convert.converter.Converter;

/**
 * Convert ISHTAR GtfsRTApiDto into ANSHAR GtfsRTApi.
 */
@Slf4j
public class GtfsRTApiDtoConverter implements Converter<GtfsRTApiDto, GtfsRTApi> {

    @Override
    public GtfsRTApi convert(@NonNull GtfsRTApiDto source) {
        log.debug("source: {}", source);
        GtfsRTApi target = new GtfsRTApi();
        target.setActive(source.getActive());
        target.setUrl(source.getUrl());
        target.setDatasetId(source.getDatasetId());
        target.setType(GTFSRTType.valueOf(source.getType()));
        target.setValidated(BooleanUtils.isTrue(source.getValidated()));
        target.setRouteIdList(source.getRouteIdList());
        target.setId(source.getId());
        target.setCloseMissingAlerts(BooleanUtils.isTrue(source.getCloseMissingAlerts()));
        target.setGenerateActivePeriod(BooleanUtils.isTrue(source.getGenerateActivePeriod()));
        if (source.getActivePeriodDays() != null) {
            target.setActivePeriodDays(source.getActivePeriodDays());
        }
        try {
            target.setPublishedLineNameMapping(PublishedLineNameMapping.valueOf(source.getPublishedLineNameMapping()));
        } catch (IllegalArgumentException e) {
            log.warn("Error mapping publishedLineNameMapping (source: {})", source.getPublishedLineNameMapping());
            log.warn("Set publishedLineNameMapping to {}", PublishedLineNameMapping.LINE_NAME.name());
            target.setPublishedLineNameMapping(PublishedLineNameMapping.LINE_NAME);
        }
        log.debug("target: {}", target);
        return target;
    }

}
