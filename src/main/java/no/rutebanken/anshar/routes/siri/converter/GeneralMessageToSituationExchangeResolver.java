package no.rutebanken.anshar.routes.siri.converter;

import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.frGeneralMessageStructure.Content;
import no.rutebanken.anshar.data.frGeneralMessageStructure.MessageType;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.stereotype.Component;
import uk.org.ifopt.siri21.StopPlaceRef;
import uk.org.siri.siri21.AffectedLineStructure;
import uk.org.siri.siri21.AffectedStopPlaceStructure;
import uk.org.siri.siri21.AffectedStopPointStructure;
import uk.org.siri.siri21.AffectsScopeStructure;
import uk.org.siri.siri21.DefaultedTextStructure;
import uk.org.siri.siri21.GeneralMessage;
import uk.org.siri.siri21.HalfOpenTimestampOutputRangeStructure;
import uk.org.siri.siri21.InfoMessageRefStructure;
import uk.org.siri.siri21.LineRef;
import uk.org.siri.siri21.PtSituationElement;
import uk.org.siri.siri21.RequestorRef;
import uk.org.siri.siri21.SituationNumber;
import uk.org.siri.siri21.StopPointRefStructure;
import uk.org.siri.siri21.WorkflowStatusEnumeration;

import java.util.List;
import java.util.Optional;

@Component
public class GeneralMessageToSituationExchangeResolver {

    private final SubscriptionConfig subscriptionConfig;
    private final StopPlaceUpdaterService stopPlaceUpdaterService;

    public GeneralMessageToSituationExchangeResolver(SubscriptionConfig subscriptionConfig,
                                                      StopPlaceUpdaterService stopPlaceUpdaterService) {
        this.subscriptionConfig = subscriptionConfig;
        this.stopPlaceUpdaterService = stopPlaceUpdaterService;
    }

    public PtSituationElement map(GeneralMessage generalMessage, String datasetId) {
        if (generalMessage == null) {
            return null;
        }

        PtSituationElement sxElement = new PtSituationElement();
        sxElement.setSituationNumber(buildSituationNumber(generalMessage.getInfoMessageIdentifier()));
        sxElement.setCreationTime(generalMessage.getRecordedAtTime());
        sxElement.setProgress(WorkflowStatusEnumeration.OPEN);
        sxElement.setParticipantRef(buildRequestorRef(datasetId));

        HalfOpenTimestampOutputRangeStructure period = new HalfOpenTimestampOutputRangeStructure();
        period.setStartTime(generalMessage.getRecordedAtTime());
        period.setEndTime(generalMessage.getValidUntilTime());
        sxElement.getValidityPeriods().add(period);
        sxElement.getPublicationWindows().add(period);

        if (generalMessage.getInfoChannelRef() != null) {
            sxElement.getKeywords().add(generalMessage.getInfoChannelRef().getValue());
        }

        if (generalMessage.getContent() instanceof Content content) {
            content.getMessages().stream()
                    .filter(m -> MessageType.TEXT_ONLY.equals(m.getMsgType()) && m.getMsgText() != null)
                    .findFirst()
                    .ifPresent(m -> {
                        DefaultedTextStructure summary = new DefaultedTextStructure();
                        summary.setValue(m.getMsgText());
                        sxElement.getSummaries().add(summary);
                    });

            AffectsScopeStructure affects = buildAffects(content, datasetId);
            if (affects != null) {
                sxElement.setAffects(affects);
            }
        }

        return sxElement;
    }

    private static SituationNumber buildSituationNumber(InfoMessageRefStructure infoMessageIdentifier) {
        SituationNumber situationNumber = new SituationNumber();
        if (infoMessageIdentifier != null) {
            situationNumber.setValue(infoMessageIdentifier.getValue());
        }
        return situationNumber;
    }

    private static RequestorRef buildRequestorRef(String datasetId) {
        RequestorRef requestorRef = new RequestorRef();
        if (StringUtils.isNotBlank(datasetId)) {
            requestorRef.setValue(datasetId+ "-from-gm");
        }
        return requestorRef;
    }

    private AffectsScopeStructure buildAffects(Content content, String datasetId) {
        List<String> lineRefs = content.getLineRefs();
        List<String> stopPointRefs = content.getStopPointRefs();
        List<String> stopPlaceRefs = resolveStopPlaceRefs(content, datasetId);

        if (CollectionUtils.isEmpty(lineRefs) && CollectionUtils.isEmpty(stopPointRefs) && CollectionUtils.isEmpty(stopPlaceRefs)) {
            return null;
        }

        AffectsScopeStructure affects = new AffectsScopeStructure();

        if (CollectionUtils.isNotEmpty(lineRefs)) {
            AffectsScopeStructure.Networks networks = new AffectsScopeStructure.Networks();
            AffectsScopeStructure.Networks.AffectedNetwork affectedNetwork = new AffectsScopeStructure.Networks.AffectedNetwork();
            for (String lineRefValue : lineRefs) {
                AffectedLineStructure affectedLine = new AffectedLineStructure();
                LineRef lineRef = new LineRef();
                lineRef.setValue(lineRefValue);
                affectedLine.setLineRef(lineRef);
                affectedNetwork.getAffectedLines().add(affectedLine);
            }
            networks.getAffectedNetworks().add(affectedNetwork);
            affects.setNetworks(networks);
        }

        if (CollectionUtils.isNotEmpty(stopPointRefs)) {
            AffectsScopeStructure.StopPoints stopPoints = new AffectsScopeStructure.StopPoints();
            for (String stopPointRefValue : stopPointRefs) {
                AffectedStopPointStructure affectedStopPoint = new AffectedStopPointStructure();
                StopPointRefStructure stopPointRef = new StopPointRefStructure();
                stopPointRef.setValue(stopPointRefValue);
                affectedStopPoint.setStopPointRef(stopPointRef);
                stopPoints.getAffectedStopPoints().add(affectedStopPoint);
            }
            affects.setStopPoints(stopPoints);
        }

        if (CollectionUtils.isNotEmpty(stopPlaceRefs)) {
            AffectsScopeStructure.StopPlaces stopPlaces = new AffectsScopeStructure.StopPlaces();
            for (String stopPlaceRefValue : stopPlaceRefs) {
                AffectedStopPlaceStructure affectedStopPlace = new AffectedStopPlaceStructure();
                StopPlaceRef stopPlaceRef = new StopPlaceRef();
                stopPlaceRef.setValue(stopPlaceRefValue);
                affectedStopPlace.setStopPlaceRef(stopPlaceRef);
                stopPlaces.getAffectedStopPlaces().add(affectedStopPlace);
            }
            affects.setStopPlaces(stopPlaces);
        }

        return affects;
    }

    private List<String> resolveStopPlaceRefs(Content content, String datasetId) {
        if (CollectionUtils.isNotEmpty(content.getStopPlaceRefs())) {
            return content.getStopPlaceRefs();
        }
        if (CollectionUtils.isEmpty(content.getStopPointRefs())) {
            return List.of();
        }
        return content.getStopPointRefs().stream()
                .map(stopPointRef -> resolveParentStopPlaceRef(stopPointRef, datasetId))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .distinct()
                .toList();
    }

    private Optional<String> resolveParentStopPlaceRef(String bareStopPointRef, String datasetId) {
        Optional<IdProcessingParameters> idProcessingParamsOpt = subscriptionConfig.getIdParametersForDataset(datasetId, ObjectType.STOP);
        if (idProcessingParamsOpt.isEmpty()) {
            return Optional.empty();
        }
        IdProcessingParameters idProcessingParams = idProcessingParamsOpt.get();

        String producerQuayRef = addOutputPrefixAndSuffix(idProcessingParams, bareStopPointRef);
        String quayMobiitiId = stopPlaceUpdaterService.get(producerQuayRef);
        if (quayMobiitiId == null) {
            return Optional.empty();
        }

        String stopPlaceMobiitiId = stopPlaceUpdaterService.getParentStopPlace(quayMobiitiId);
        if (stopPlaceMobiitiId == null) {
            return Optional.empty();
        }

        List<String> reverseIds = stopPlaceUpdaterService.getReverse(stopPlaceMobiitiId, datasetId);
        if (reverseIds.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(idProcessingParams.removeOutputPrefixAndSuffix(reverseIds.getFirst()));
    }

    private static String addOutputPrefixAndSuffix(IdProcessingParameters idProcessingParams, String bareId) {
        String withPrefix = Strings.CS.prependIfMissing(bareId, idProcessingParams.getOutputPrefixToAdd());
        return Strings.CS.appendIfMissing(withPrefix, idProcessingParams.getOutputSuffixToAdd());
    }

}
