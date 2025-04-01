package no.rutebanken.anshar.ishtar.synchronize;

import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.api.GtfsRTApi;
import no.rutebanken.anshar.api.SiriApi;
import no.rutebanken.anshar.config.DiscoverySubscription;
import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.TokenService;
import no.rutebanken.anshar.ishtar.converter.*;
import no.rutebanken.anshar.ishtar.model.GtfsRTApiDto;
import no.rutebanken.anshar.ishtar.model.IdProcessingParameterDto;
import no.rutebanken.anshar.ishtar.model.SiriApiDto;
import no.rutebanken.anshar.ishtar.model.SubscriptionDto;
import no.rutebanken.anshar.routes.mapping.ExternalIdsService;
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
    private final ExternalIdsService externalIdsService;
    private final TokenService tokenService;

    public IshtarSynchronizeProcessor(SubscriptionConfig subscriptionConfig,
                                      DiscoverySubscriptionCreator discoverySubscriptionCreator,
                                      @Value("${ishtar.server.url}") URL ishtarUrl,
                                      SubscriptionDtoToSubscriptionSetupConverter toSubscriptionSetupConverter,
                                      ExternalIdsService externalIdsService,
                                      TokenService tokenService) {
        this.subscriptionConfig = subscriptionConfig;
        this.discoverySubscriptionCreator = discoverySubscriptionCreator;
        this.webClient = WebClient.builder()
                .baseUrl(ishtarUrl.toString()).defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.externalIdsService = externalIdsService;
        this.tokenService = tokenService;
        this.gtfsRTApiDtoConverter = new GtfsRTApiDtoConverter();
        this.siriApiDtoConverter = new SiriApiDtoConverter();
        this.idProcessingParameterDtoConverter = new IdProcessingParameterDtoConverter();
        this.toDiscoverySubscriptionConverter = new SubscriptionDtoToDiscoverySubscriptionConverter();
        this.toSubscriptionSetupConverter = toSubscriptionSetupConverter;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        getGtfsRTData();
        getSiriAPIData();
        getIdProcessingParameters();
        getSubscriptions();
    }

    private void getSubscriptions() {
        try {
            log.info("--> ISHTAR : get Subscription(s)");
            List<SubscriptionDto> subs =
                    ListUtils.emptyIfNull(getWebClient(GET_ALL_SUBSCRIPTIONS_URI).retrieve().bodyToFlux(SubscriptionDto.class).collectList().block());
            log.info("<-- ISHTAR : retrieved {} Subscription(s)", subs.size());

            Map<Boolean, List<SubscriptionDto>> partionedByDiscoverySubs =
                    subs.stream().filter(Objects::nonNull).collect(Collectors.partitioningBy(s -> BooleanUtils.isTrue(s.getDiscoverySubscription())));

            List<SubscriptionSetup> setups =
                    partionedByDiscoverySubs.get(false).stream().map(toSubscriptionSetupConverter::convert).collect(Collectors.toList());
            log.info("Converted {} ISHTAR Subscription(s) into {} ANSHAR Setup Subscription(s)", subs.size(),
                    setups.size());


            Map<Boolean, List<SubscriptionSetup>> validationMap = setups.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.partitioningBy(SubscriptionSetup::isValidated));

            if (validationMap.containsKey(false) && !validationMap.get(false).isEmpty()) {
                for (SubscriptionSetup unValidatedApi : validationMap.get(false)) {
                    log.info("Unvalidated Subscription : {} - {} ", unValidatedApi.getDatasetId(), unValidatedApi.getSubscriptionId());
                }
            }

            if (!validationMap.containsKey(true) || validationMap.get(true).isEmpty()) {
                log.info("No validated Subscription APIs ");
            } else {
                log.info("Before merge {} Setup Subscription(s) in cache", subscriptionConfig.getSubscriptions().size());
                subscriptionConfig.mergeSubscriptions(validationMap.get(true));
                log.info("After merge {} Setup Subscription(s) in cache", subscriptionConfig.getSubscriptions().size());
            }


            List<DiscoverySubscription> discoveries = partionedByDiscoverySubs.get(true)
                    .stream()
                    .map(toDiscoverySubscriptionConverter::convert)
                    .collect(Collectors.toList());
            log.info("Converted {} ISHTAR Subscription(s) into {} ANSHAR Discovery Subscription(s)", subs.size(), discoveries.size());
            log.info("Before merge {} Discovery Subscription(s) in cache",
                    subscriptionConfig.getDiscoverySubscriptions().size());


            Map<Boolean, List<DiscoverySubscription>> discoveryMap = discoveries.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.partitioningBy(DiscoverySubscription::getValidated));

            if (discoveryMap.containsKey(false) && !discoveryMap.get(false).isEmpty()) {
                for (DiscoverySubscription unValidatedApi : discoveryMap.get(false)) {
                    log.info("Unvalidated discovery subscription :  {} - {}", unValidatedApi.getDatasetId(), unValidatedApi.getUrl());
                }
            }

            if (!discoveryMap.containsKey(true) || discoveryMap.get(true).isEmpty()) {
                log.info("No validated Subscription APIs ");
            } else {
                log.info("Before merge {} discovery Subscription(s) in cache", subscriptionConfig.getSubscriptions().size());
                subscriptionConfig.mergeDiscoverySubscriptions(discoveryMap.get(true));
                log.info("After merge {} discovery Subscription(s) in cache", subscriptionConfig.getSubscriptions().size());
            }
            log.info("After merge {} Discovery Subscription(s) in cache",
                    subscriptionConfig.getDiscoverySubscriptions().size());
            discoverySubscriptionCreator.createDiscoverySubscriptions();
        } catch (WebClientException e) {
            log.error("--> ISHTAR : error during subscriptions synchronization : {}", e.getMessage());
            log.debug("Error during subscriptions synchronization", e);
        } catch (Exception e) {
            log.error("--> ISHTAR : error during subscriptions synchronization (SHOULD BE FIXED BY DEVS)", e);
        }
    }

    private void getIdProcessingParameters() {
        try {
            log.info("--> ISHTAR : get ID Processing Parameter(s)");
            List<IdProcessingParameterDto> ippDtos =
                    ListUtils.emptyIfNull(getWebClient(GET_ALL_ID_PROCESSING_PARAMETERS_URI).retrieve().bodyToFlux(IdProcessingParameterDto.class).collectList().block());
            log.info("<-- ISHTAR : retrieved {} ID Processing Parameter(s)", ippDtos.size());
            List<IdProcessingParameters> ipp = ippDtos.stream().map(idProcessingParameterDtoConverter::convert).collect(Collectors.toList());
            log.info("Before merge {} ID ProcessingParameter(s) in cache",
                    subscriptionConfig.getIdProcessingParameters().size());
            subscriptionConfig.mergeIdProcessingParams(ipp);
            log.info("After merge {} ID ProcessingParameter(s) in cache",
                    subscriptionConfig.getIdProcessingParameters().size());
            externalIdsService.downloadFilesAndRefreshCache();
        } catch (WebClientException e) {
            log.error("--> ISHTAR : error during IdProcessing synchronization : {}", e.getMessage());
            log.debug("Error during IdProcessing synchronization", e);
        } catch (Exception e) {
            log.error("--> ISHTAR : error during IdProcessing synchronization (SHOULD BE FIXED BY DEVS)", e);
        }
    }

    private void getSiriAPIData() {
        try {
            log.info("--> ISHTAR : get Siri API(s)");
            List<SiriApiDto> siriApiDtos =
                    ListUtils.emptyIfNull(getWebClient(GET_ALL_SIRI_APIS_URI).retrieve().bodyToFlux(SiriApiDto.class).collectList().block());
            log.info("<-- ISHTAR : retrieved {} Siri API(s)", siriApiDtos.size());
            List<SiriApi> siriApis = siriApiDtos.stream().map(siriApiDtoConverter::convert).collect(Collectors.toList());

            Map<Boolean, List<SiriApi>> validationMap = siriApis.stream()
                    .collect(Collectors.partitioningBy(SiriApi::getValidated));

            if (validationMap.containsKey(false) && !validationMap.get(false).isEmpty()) {
                for (SiriApi unValidatedApi : validationMap.get(false)) {
                    log.info("Unvalidated Siri API :  {} - {}", unValidatedApi.getDatasetId(), unValidatedApi.getUrl());
                }
            }

            if (!validationMap.containsKey(true) || validationMap.get(true).isEmpty()) {
                log.info("No validated Siri APIs ");
                return;
            }

            log.info("Before merge {} Siri API(s) in cache", subscriptionConfig.getSiriApis().size());
            subscriptionConfig.mergeSiriApis(validationMap.get(true));
            log.info("After merge {} Siri API(s) in cache", subscriptionConfig.getSiriApis().size());
        } catch (WebClientException e) {
            log.error("--> ISHTAR : error during Siri API synchronization : {}", e.getMessage());
            log.debug("Error during Siri API synchronization", e);
        } catch (Exception e) {
            log.error("--> ISHTAR : error during Siri API synchronization (SHOULD BE FIXED BY DEVS)", e);
        }
    }

    private void getGtfsRTData() {
        try {
            log.info("--> ISHTAR : get GTFS RT API(s)");
            List<GtfsRTApiDto> gtfsRTApiDtos =
                    ListUtils.emptyIfNull(getWebClient(GET_ALL_GTFS_RT_APIS_URI).retrieve().bodyToFlux(GtfsRTApiDto.class).collectList().block());
            log.info("<-- ISHTAR : retrieved {} GTFS RT API(s)", gtfsRTApiDtos.size());
            List<GtfsRTApi> gtfsRTApis = gtfsRTApiDtos.stream().map(gtfsRTApiDtoConverter::convert).collect(Collectors.toList());
            Map<Boolean, List<GtfsRTApi>> validationMap = gtfsRTApis.stream()
                    .collect(Collectors.partitioningBy(GtfsRTApi::getValidated));

            if (validationMap.containsKey(false) && !validationMap.get(false).isEmpty()) {
                for (GtfsRTApi unValidatedApi : validationMap.get(false)) {
                    log.info("Unvalidated API :  {} - {}", unValidatedApi.getDatasetId(), unValidatedApi.getUrl());
                }
            }

            if (!validationMap.containsKey(true) || validationMap.get(true).isEmpty()) {
                log.info("No validated GTFSRT APIs ");
                return;
            }
            log.info("Before merge {} GTFS RT API(s) in cache", subscriptionConfig.getGtfsRTApis().size());
            subscriptionConfig.mergeGTFSRTApis(validationMap.get(true));
            log.info("After merge {} GTFS RT API(s) in cache", subscriptionConfig.getGtfsRTApis().size());
        } catch (WebClientException e) {
            log.error("--> ISHTAR : error during GTFS RT API synchronization : {}", e.getMessage());
            log.debug("Error during GTFS RT API synchronization", e);
        } catch (Exception e) {
            log.error("--> ISHTAR : error during GTFS RT API synchronization (SHOULD BE FIXED BY DEVS)", e);
        }

    }

    private WebClient.RequestHeadersSpec<?> getWebClient(String uri) {
        return webClient.get()
                .uri(uri)
                .headers(headers -> headers.setBearerAuth(tokenService.getToken()));
    }
}
