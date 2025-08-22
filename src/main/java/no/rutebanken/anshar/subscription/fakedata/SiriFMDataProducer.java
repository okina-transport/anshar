package no.rutebanken.anshar.subscription.fakedata;

import no.rutebanken.anshar.routes.outbound.OutboundSubscriptionSetup;
import org.apache.commons.collections4.MapUtils;
import uk.org.siri.siri21.*;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

public class SiriFMDataProducer implements FakeDataProducer {
    @Override
    public List<Siri> produce(OutboundSubscriptionSetup sub) {
        Siri fm = new Siri();

        ServiceDelivery sd = new ServiceDelivery();

        FacilityMonitoringDeliveryStructure fmds = new FacilityMonitoringDeliveryStructure();
        fmds.setVersion("2.1");

        for (String siteRef : getFacilityRefFilter(sub)) {
            FacilityConditionStructure fcs = new FacilityConditionStructure();

            FacilityRef fr = new FacilityRef();
            fr.setValue(siteRef);
            fcs.setFacilityRef(fr);

            FacilityStructure fs = new FacilityStructure();
            fs.setFacilityCode(siteRef);
            fcs.setFacility(fs);

            HalfOpenTimestampOutputRangeStructure hotors = new HalfOpenTimestampOutputRangeStructure();
            hotors.setEndTimeStatus(EndTimeStatusEnumeration.SHORT_TERM);
            hotors.setStartTime(ZonedDateTime.now().minusHours(1));
            hotors.setEndTime(ZonedDateTime.now().plusHours(1));
            fcs.setValidityPeriod(hotors);

            fmds.getFacilityConditions().add(fcs);
        }

        sd.getFacilityMonitoringDeliveries().add(fmds);
        fm.setServiceDelivery(sd);
        return List.of(fm);
    }

    private Set<String> getFacilityRefFilter(OutboundSubscriptionSetup sub) {
        if (MapUtils.isEmpty(sub.getFilterMap()) || !sub.getFilterMap().containsKey(FacilityRef.class)) {
            return Set.of("MOBIITI:SiteRef:666");
        }
        return sub.getFilterMap().get(FacilityRef.class);
    }
}
