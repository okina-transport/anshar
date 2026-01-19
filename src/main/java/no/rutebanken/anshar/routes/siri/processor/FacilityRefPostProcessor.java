package no.rutebanken.anshar.routes.siri.processor;

import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.routes.mapping.ParkingIdsService;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import uk.org.siri.siri21.FacilityConditionStructure;
import uk.org.siri.siri21.FacilityMonitoringDeliveryStructure;
import uk.org.siri.siri21.Siri;

import java.util.List;
import java.util.Optional;

@Slf4j
public class FacilityRefPostProcessor extends ValueAdapter implements PostProcessor {

    private final String operator;
    private final OutboundIdMappingPolicy outboundIdMappingPolicy;
    private ParkingIdsService parkingIdsService;

    public FacilityRefPostProcessor(String operator, OutboundIdMappingPolicy outboundIdMappingPolicy) {
        this.operator = operator;
        this.outboundIdMappingPolicy = outboundIdMappingPolicy;
    }

    @Override
    protected String apply(String text) {
        return null;
    }

    @Override
    public void process(Siri siri) {
        if (OutboundIdMappingPolicy.ALT_ID == outboundIdMappingPolicy || OutboundIdMappingPolicy.ORIGINAL_ID == outboundIdMappingPolicy) {
            return;
        }
        if (siri == null || siri.getServiceDelivery() == null) {
            return;
        }
        if (parkingIdsService == null) {
            parkingIdsService = ApplicationContextHolder.getContext().getBean(ParkingIdsService.class);
        }
        List<FacilityMonitoringDeliveryStructure> fmds = siri.getServiceDelivery().getFacilityMonitoringDeliveries();
        if (CollectionUtils.isNotEmpty(fmds)) {
            for (FacilityMonitoringDeliveryStructure fmd : fmds) {
                List<FacilityConditionStructure> facilityConditions = fmd.getFacilityConditions();
                if (facilityConditions != null) {
                    for (FacilityConditionStructure facilityConditionStructure : facilityConditions) {
                        if (facilityConditionStructure.getFacilityRef() != null && StringUtils.isNotBlank(facilityConditionStructure.getFacilityRef().getValue())) {
                            Optional<String> netexId = parkingIdsService.getNetexParkingIdByOperatorAndOriginalId(operator,
                                    facilityConditionStructure.getFacilityRef().getValue());
                            if (netexId.isEmpty()) {
                                log.warn("FacilityRef operator {} / originalId {} not in parking mapping file",
                                        operator,
                                        facilityConditionStructure.getFacilityRef().getValue());
                            } else {
                                facilityConditionStructure.getFacilityRef().setValue(netexId.get());
                            }
                        }
                    }
                }
            }
        }
    }
}
