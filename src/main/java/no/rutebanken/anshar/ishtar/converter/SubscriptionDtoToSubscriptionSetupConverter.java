package no.rutebanken.anshar.ishtar.converter;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.ishtar.model.*;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.SubscriptionStatus;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import org.apache.camel.TypeConverters;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Convert ISHTAR SubscriptionDto into ANSHAR SubscriptionSetup.
 */
@Component
@Slf4j
public class SubscriptionDtoToSubscriptionSetupConverter implements Converter<SubscriptionDto, SubscriptionSetup>, TypeConverters {

    @Value("${anshar.inbound.url}")
    private String inboundUrl;

    @Override
    @org.apache.camel.Converter
    public SubscriptionSetup convert(@NonNull SubscriptionDto source) {
        log.debug("source: {}", source);
        SubscriptionSetup target = null;
        if (BooleanUtils.isTrue(source.getDiscoverySubscription())) {
            log.warn("Subscription is a discovery subscription, discard it: {}", source);
        } else {
            target = new SubscriptionSetup();
            target.setSubscriptionId(source.getSubscriptionId());
            target.setDatasetId(source.getDatasetId());
            target.setInternalId(source.getId() != null ? source.getId() : 0L);
            target.setActive(BooleanUtils.isTrue(source.getActive()));
            target.setSubscriptionType(SiriDataType.valueOf(source.getSubscriptionType()));
            target.setIdMappingPrefixes(ListUtils.emptyIfNull(source.getIdMappingPrefixes()).stream().map(IdMappingPrefixDto::getValue).collect(Collectors.toList()));
            target.setStopMonitoringRefValue(ListUtils.emptyIfNull(source.getSubscriptionStops()).stream().map(SubscriptionStopDto::getStopRef).collect(Collectors.toList()));
            target.setName(source.getName());
            target.setVendor(source.getVendor());
            target.setRequestorRef(source.getRequestorRef());
            target.setDurationOfSubscriptionHours(source.getDurationOfSubscriptionHours());
            target.setSubscriptionMode(SubscriptionSetup.SubscriptionMode.valueOf(source.getSubscriptionMode()));
            target.setUrlMap(ListUtils.emptyIfNull(source.getUrlMaps()).stream().collect(Collectors.toMap(UrlMapDto::getName, UrlMapDto::getUrl)));
            target.setServiceType(SubscriptionSetup.ServiceType.valueOf(source.getServiceType()));
            target.setContentType(source.getContentType());
            target.setHeartbeatIntervalSeconds(source.getHeartbeatIntervalSeconds());
            target.setVersion(source.getVersion());
            target.setOperatorNamespace(source.getOperatorNamespace());
            target.setRestartTime(source.getRestartTime());
            target.setRevertIds(source.getRevertIds());
            target.setChangeBeforeUpdatesSeconds(source.getChangeBeforeUpdatesSeconds());
            target.setCustomHeaders(ListUtils.emptyIfNull(source.getCustomHeaders())
                    .stream()
                    .collect(Collectors.toMap(CustomHeaderDto::getName, CustomHeaderDto::getValue))
            );
            target.setLineRefValues(ListUtils.emptyIfNull(source.getSubscriptionLines()).stream().map(SubscriptionLineDto::getLineRef).collect(Collectors.toList()));
            target.setSiteRefValues(ListUtils.emptyIfNull(source.getSubscriptionSites()).stream().map(SubscriptionSiteDto::getSiteRef).collect(Collectors.toList()));
            target.setMappingAdapterId(source.getMappingAdapterId());
            target.setUpdateIntervalSeconds(source.getUpdateIntervalSeconds());
            target.setPreviewIntervalSeconds(source.getPreviewIntervalSeconds());

            target.setValidated(BooleanUtils.isTrue(source.getValidated()));
            target.setGenerateSX(BooleanUtils.isTrue(source.getGenerateSX()));
            target.setSkipHeader(BooleanUtils.isTrue(source.getSkipHeader()));
            target.setUseNamespaceForAnswerParsing(BooleanUtils.isTrue(source.getUseNamespaceForAnswerParsing()));

            if (!SubscriptionSetup.SubscriptionMode.SUBSCRIBE.equals(target.getSubscriptionMode())) {
                target.setStatus(SubscriptionStatus.RUNNING);
            }

            if (!target.isActive()) {
                target.setStatus(SubscriptionStatus.STOPPED);
            }

            target.setOverrideDestinationName(source.getOverrideDestinationName());
            // required to avoid NPE on to SubscriptionSetup#buildUrl()
            target.setAddress(inboundUrl);

            if (StringUtils.isNotEmpty(source.getPublishToDisplayAction())) {
                target.setPublishToDisplayAction(PublishToDisplayAction.valueOf(source.getPublishToDisplayAction()));
            }


            if (target.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.SUBSCRIBE && StringUtils.isEmpty(MapUtils.getString(target.getUrlMap(), RequestType.SUBSCRIBE))) {
                log.warn("subscription has no subscribe URL, discard it: {}", source);
                target = null;
            }


        }
        log.debug("target: {}", target);
        return target;
    }

}
