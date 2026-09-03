package no.rutebanken.anshar.ishtar.converter;

import no.rutebanken.anshar.ishtar.model.SubscriptionDto;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SubscriptionDtoToSubscriptionSetupConverterTest {

    private final SubscriptionDtoToSubscriptionSetupConverter converter = new SubscriptionDtoToSubscriptionSetupConverter();

    @BeforeEach
    public void setUp() {
        ReflectionTestUtils.setField(converter, "inboundUrl", "http://localhost:8080/anshar");
    }

    private SubscriptionDto baseDto() {
        SubscriptionDto dto = new SubscriptionDto();
        dto.setSubscriptionId("sub-1");
        dto.setDatasetId("TST");
        dto.setActive(true);
        dto.setSubscriptionType("SITUATION_EXCHANGE");
        dto.setSubscriptionMode("REQUEST_RESPONSE");
        dto.setServiceType("REST");
        dto.setDurationOfSubscriptionHours(1);
        dto.setHeartbeatIntervalSeconds(60);
        dto.setChangeBeforeUpdatesSeconds(0);
        dto.setUpdateIntervalSeconds(60);
        dto.setPreviewIntervalSeconds(0);
        dto.setOverrideDestinationName(false);
        dto.setUrlMaps(java.util.Collections.emptyList());
        return dto;
    }

    @Test
    public void convert_mapsOperatorAndNetworkRef() {
        SubscriptionDto dto = baseDto();
        dto.setOperatorRef("OPERATOR:TARGET");
        dto.setNetworkRef("NETWORK:TARGET");

        SubscriptionSetup target = converter.convert(dto);

        assertEquals("OPERATOR:TARGET", target.getOperatorRefValue());
        assertEquals("NETWORK:TARGET", target.getNetworkRefValue());
    }

    @Test
    public void convert_withoutOperatorAndNetworkRef_leavesThemNull() {
        SubscriptionDto dto = baseDto();

        SubscriptionSetup target = converter.convert(dto);

        assertNull(target.getOperatorRefValue());
        assertNull(target.getNetworkRefValue());
    }
}
