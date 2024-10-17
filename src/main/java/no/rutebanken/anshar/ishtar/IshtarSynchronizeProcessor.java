package no.rutebanken.anshar.ishtar;

import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.api.GtfsRTApi;
import no.rutebanken.anshar.api.SiriApi;
import no.rutebanken.anshar.config.DiscoverySubscription;
import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.ishtar.converter.*;
import no.rutebanken.anshar.ishtar.model.GtfsRTApiDto;
import no.rutebanken.anshar.ishtar.model.IdProcessingParameterDto;
import no.rutebanken.anshar.ishtar.model.SiriApiDto;
import no.rutebanken.anshar.ishtar.model.SubscriptionDto;
import no.rutebanken.anshar.subscription.DiscoverySubscriptionCreator;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@Slf4j
public class IshtarSynchronizeProcessor implements Processor {

    private static final String GET_ALL_SIRI_APIS_URI = "/siri-apis/all";
    private static final String GET_ALL_ID_PROCESSING_PARAMETERS_URI = "/id-processing-parameters/all";
    private static final String GET_ALL_SUBSCRIPTIONS_URI = "/subscriptions/all";
    private static final String GET_ALL_GTFS_RT_APIS_URI = "/gtfs-rt-apis/all";

    private final SubscriptionConfig subscriptionConfig;
    private final DiscoverySubscriptionCreator discoverySubscriptionCreator;

    private final WebClient webClient;

    private final GtfsRTApiDtoConverter gtfsRTApiDtoConverter;
    private final SiriApiDtoConverter siriApiDtoConverter;
    private final IdProcessingParameterDtoConverter idProcessingParameterDtoConverter;
    private final SubscriptionDtoToDiscoverySubscriptionConverter toDiscoverySubscriptionConverter;
    private final SubscriptionDtoToSubscriptionSetupConverter toSubscriptionSetupConverter;

    public IshtarSynchronizeProcessor(SubscriptionConfig subscriptionConfig, DiscoverySubscriptionCreator discoverySubscriptionCreator, @Value("${ishtar.server.url}") URL ishtarUrl) {
        this.subscriptionConfig = subscriptionConfig;
        this.discoverySubscriptionCreator = discoverySubscriptionCreator;
        this.webClient = WebClient.builder().baseUrl(ishtarUrl.toString()).defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE).build();
        this.gtfsRTApiDtoConverter = new GtfsRTApiDtoConverter();
        this.siriApiDtoConverter = new SiriApiDtoConverter();
        this.idProcessingParameterDtoConverter = new IdProcessingParameterDtoConverter();
        this.toDiscoverySubscriptionConverter = new SubscriptionDtoToDiscoverySubscriptionConverter();
        this.toSubscriptionSetupConverter = new SubscriptionDtoToSubscriptionSetupConverter();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        try {
            log.info("--> ISHTAR : get GTFS RT API(s)");
            List<GtfsRTApiDto> gtfsRTApiDtos =
                    ListUtils.emptyIfNull(webClient.get().uri(GET_ALL_GTFS_RT_APIS_URI).retrieve().bodyToFlux(GtfsRTApiDto.class).collectList().block());
            log.info("<-- ISHTAR : retrieved {} GTFS RT API(s)", gtfsRTApiDtos.size());
            List<GtfsRTApi> gtfsRTApis = gtfsRTApiDtos.stream().map(gtfsRTApiDtoConverter::convert).collect(Collectors.toList());
            log.info("Before merge {} GTFS RT API(s) in cache", subscriptionConfig.getGtfsRTApis().size());
            subscriptionConfig.mergeGTFSRTApis(gtfsRTApis);
            log.info("After merge {} GTFS RT API(s) in cache", subscriptionConfig.getGtfsRTApis().size());

            log.info("--> ISHTAR : get Siri API(s)");
            List<SiriApiDto> siriApiDtos =
                    ListUtils.emptyIfNull(webClient.get().uri(GET_ALL_SIRI_APIS_URI).retrieve().bodyToFlux(SiriApiDto.class).collectList().block());
            log.info("<-- ISHTAR : retrieved {} Siri API(s)", siriApiDtos.size());
            List<SiriApi> siriApis = siriApiDtos.stream().map(siriApiDtoConverter::convert).collect(Collectors.toList());
            log.info("Before merge {} Siri API(s) in cache", subscriptionConfig.getSiriApis().size());
            subscriptionConfig.mergeSiriApis(siriApis);
            log.info("After merge {} Siri API(s) in cache", subscriptionConfig.getSiriApis().size());

            log.info("--> ISHTAR : get ID Processing Parameter(s)");
            List<IdProcessingParameterDto> ippDtos =
                    ListUtils.emptyIfNull(webClient.get().uri(GET_ALL_ID_PROCESSING_PARAMETERS_URI).retrieve().bodyToFlux(IdProcessingParameterDto.class).collectList().block());
            log.info("<-- ISHTAR : retrieved {} ID Processing Parameter(s)", ippDtos.size());
            List<IdProcessingParameters> ipp = ippDtos.stream().map(idProcessingParameterDtoConverter::convert).collect(Collectors.toList());
            log.info("Before merge {} ID ProcessingParameter(s) in cache",
                    subscriptionConfig.getIdProcessingParameters().size());
            subscriptionConfig.mergeIdProcessingParams(ipp);
            log.info("After merge {} ID ProcessingParameter(s) in cache",
                    subscriptionConfig.getIdProcessingParameters().size());

            log.info("--> ISHTAR : get Subscription(s)");
            List<SubscriptionDto> subs =
                    ListUtils.emptyIfNull(webClient.get().uri(GET_ALL_SUBSCRIPTIONS_URI).retrieve().bodyToFlux(SubscriptionDto.class).collectList().block());
            log.info("<-- ISHTAR : retrieved {} Subscription(s)", subs.size());

            Map<Boolean, List<SubscriptionDto>> partionedByDiscoverySubs =
                    subs.stream().filter(Objects::nonNull).collect(Collectors.partitioningBy(s -> BooleanUtils.isTrue(s.getDiscoverySubscription())));

            List<SubscriptionSetup> setups =
                    partionedByDiscoverySubs.get(false).stream().map(toSubscriptionSetupConverter::convert).collect(Collectors.toList());
            log.info("Converted {} ISHTAR Subscription(s) into {} ANSHAR Setup Subscription(s)", subs.size(),
                    setups.size());
            log.info("Before merge {} Setup Subscription(s) in cache", subscriptionConfig.getSubscriptions().size());
            subscriptionConfig.mergeSubscriptions(setups);
            log.info("After merge {} Setup Subscription(s) in cache", subscriptionConfig.getSubscriptions().size());

            List<DiscoverySubscription> discoveries =
                    partionedByDiscoverySubs.get(true).stream().map(toDiscoverySubscriptionConverter::convert).collect(Collectors.toList());
            log.info("Converted {} ISHTAR Subscription(s) into {} ANSHAR Discovery Subscription(s)", subs.size(),
                    discoveries.size());
            log.info("Before merge {} Discovery Subscription(s) in cache",
                    subscriptionConfig.getDiscoverySubscriptions().size());
            subscriptionConfig.mergeDiscoverySubscriptions(discoveries);
            log.info("After merge {} Discovery Subscription(s) in cache",
                    subscriptionConfig.getDiscoverySubscriptions().size());
            discoverySubscriptionCreator.createDiscoverySubscriptions();
        } catch (WebClientException e) {
            log.error("--> ISHTAR : error during data synchronization : {}", e.getMessage());
            log.debug("Error during data synchronization", e);
            throw e;
        } catch (Exception e) {
            log.error("--> ISHTAR : error during data synchronization (SHOULD BE FIXED BY DEVS)", e);
            throw e;
        }
    }
}
