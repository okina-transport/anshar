package no.rutebanken.anshar.ishtar.converter;

import no.rutebanken.anshar.api.GtfsRTApi;
import no.rutebanken.anshar.ishtar.model.GtfsRTApiDto;
import no.rutebanken.anshar.ishtar.model.PublishToDisplayAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class GtfsRTApiDtoConverterTest {

    @InjectMocks
    private GtfsRTApiDtoConverter converter;

    @Test
    void test_gtfs_rt_api_dto_to_gtfs_rt_api_conversion() {
        GtfsRTApiDto dto = new GtfsRTApiDto();
        dto.setId(1L);
        dto.setDatasetId("DATASET");
        dto.setType("JSON");
        dto.setUrl("https://jsonplaceholder.typicode.com/todos/1");
        dto.setActive(Boolean.TRUE);
        dto.setUpdateDatetime(new Date());
        dto.setValidated(Boolean.TRUE);
        dto.setRouteIdList("A,B,C");
        dto.setCloseMissingAlerts(Boolean.FALSE);
        dto.setGenerateActivePeriod(Boolean.FALSE);
        dto.setActivePeriodDays(0);
        dto.setPublishedLineNameMapping("LINE_NAME");
        dto.setCurrentStatus("UNKNOWN");
        dto.setApiKey("titi-toto-tata");
        dto.setPublishToDisplayAction(PublishToDisplayAction.ON_PLACE);

        GtfsRTApi domainObject = converter.convert(dto);

        assertThat(domainObject).isNotNull();
        assertThat(domainObject.getId()).isEqualTo(dto.getId());
        assertThat(domainObject.getType().name()).isEqualTo(dto.getType());
        assertThat(domainObject.getUrl()).isEqualTo(dto.getUrl());
        assertThat(domainObject.getActive()).isEqualTo(dto.getActive());
        assertThat(domainObject.getLastUpdate()).isZero();
        assertThat(domainObject.getValidated()).isEqualTo(dto.getValidated());
        assertThat(domainObject.getRouteIdList()).isEqualTo(dto.getRouteIdList());
        assertThat(domainObject.getCloseMissingAlerts()).isEqualTo(dto.getCloseMissingAlerts());
        assertThat(domainObject.getGenerateActivePeriod()).isEqualTo(dto.getGenerateActivePeriod());
        assertThat(domainObject.getActivePeriodDays()).isEqualTo(dto.getActivePeriodDays());
        assertThat(domainObject.getPublishedLineNameMapping().name()).isEqualTo(dto.getPublishedLineNameMapping());
        assertThat(domainObject.getStatus()).isNull();
        assertThat(domainObject.getApiKey()).isEqualTo(dto.getApiKey());
        assertThat(domainObject.getPublishToDisplayAction()).isEqualTo(dto.getPublishToDisplayAction());
    }

}