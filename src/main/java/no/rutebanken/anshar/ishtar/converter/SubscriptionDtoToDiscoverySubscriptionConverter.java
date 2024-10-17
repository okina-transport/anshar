package no.rutebanken.anshar.ishtar.converter;

import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.config.DiscoverySubscription;
import no.rutebanken.anshar.ishtar.model.CustomHeaderDto;
import no.rutebanken.anshar.ishtar.model.SubscriptionDto;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.core.convert.converter.Converter;

import java.util.stream.Collectors;

/**
 * Convert ISHTAR SubscriptionDto into ANSHAR DiscoverySubscription.
 */
@Slf4j
public class SubscriptionDtoToDiscoverySubscriptionConverter implements Converter<SubscriptionDto, DiscoverySubscription> {

    @Override
    public DiscoverySubscription convert(SubscriptionDto source) {
        log.debug("source: {}", source);
        DiscoverySubscription target = null;
        if (BooleanUtils.isFalse(source.getDiscoverySubscription())) {
            log.warn("Subscription is not a discovery subscription, discard it: {}", source);
        } else {
            target = new DiscoverySubscription();
            target.setDatasetId(source.getDatasetId());
            target.setUrl(source.getUrlMaps().get(0).getUrl());
            target.setDiscoveryType(SiriDataType.valueOf(source.getSubscriptionType()));
            target.setRequestorRef(source.getRequestorRef());
            target.setHeartbeatIntervalSeconds(source.getHeartbeatIntervalSeconds());
            target.setChangeBeforeUpdatesSeconds(source.getChangeBeforeUpdatesSeconds());
            target.setUpdateIntervalSeconds(source.getUpdateIntervalSeconds());
            target.setPreviewIntervalSeconds(source.getPreviewIntervalSeconds());
            target.setCustomHeaders(ListUtils.emptyIfNull(source.getCustomHeaders())
                    .stream()
                    .collect(Collectors.toMap(CustomHeaderDto::getName, CustomHeaderDto::getValue))
            );
            target.setSubscriptionMode(SubscriptionSetup.SubscriptionMode.valueOf(source.getSubscriptionMode()));
            target.setDurationOfSubscriptionHours(source.getDurationOfSubscriptionHours());
            target.setVendorBaseName(source.getVendor());
            target.setSubscriptionIdBase(source.getSubscriptionId());
        }
        log.debug("target: {}", target);
        return target;
    }

}
