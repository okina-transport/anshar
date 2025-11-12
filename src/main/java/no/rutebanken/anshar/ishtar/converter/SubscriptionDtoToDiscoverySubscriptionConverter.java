package no.rutebanken.anshar.ishtar.converter;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.config.DiscoverySubscription;
import no.rutebanken.anshar.ishtar.model.CustomHeaderDto;
import no.rutebanken.anshar.ishtar.model.SubscriptionDto;
import no.rutebanken.anshar.ishtar.model.UrlMapDto;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import org.apache.camel.TypeConverters;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Convert ISHTAR SubscriptionDto into ANSHAR DiscoverySubscription.
 */
@Component
@Slf4j
public class SubscriptionDtoToDiscoverySubscriptionConverter implements Converter<SubscriptionDto, DiscoverySubscription>, TypeConverters {

    @Override
    @org.apache.camel.Converter
    public DiscoverySubscription convert(@NonNull SubscriptionDto source) {
        log.debug("source: {}", source);
        DiscoverySubscription target = null;
        if (BooleanUtils.isFalse(source.getDiscoverySubscription())) {
            log.warn("Subscription is not a discovery subscription, discard it: {}", source);
        } else {
            Optional<UrlMapDto> discoveryUrl = isDiscoveryUrlDefined(source);
            if (discoveryUrl.isPresent()) {
                target = new DiscoverySubscription();
                target.setDatasetId(source.getDatasetId());
                target.setUrl(discoveryUrl.get().getUrl());
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
                target.setValidated(BooleanUtils.isTrue(source.getValidated()));
                target.setServiceType(SubscriptionSetup.ServiceType.valueOf(source.getServiceType()));
                target.setVersion(source.getVersion());
                target.setMappingAdapterId(source.getMappingAdapterId());
                target.setActive(BooleanUtils.isTrue(source.getActive()));
            } else {
                log.warn("Subscription has no discovery url defined, discard it: {}", source);
            }
        }
        log.debug("target: {}", target);
        return target;
    }

    private Optional<UrlMapDto> isDiscoveryUrlDefined(SubscriptionDto source) {
        Optional<UrlMapDto> discoveryUrl = Optional.empty();
        if (CollectionUtils.isNotEmpty(source.getUrlMaps())) {
            discoveryUrl = source.getUrlMaps()
                    .stream()
                    .filter(urlMapDto -> urlMapDto.getName() == RequestType.SUBSCRIBE)
                    .findFirst();
        }
        return discoveryUrl;
    }

}
