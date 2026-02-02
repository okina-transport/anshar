package no.rutebanken.anshar.data.util;


import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.frGeneralMessageStructure.Content;
import no.rutebanken.anshar.data.frGeneralMessageStructure.Message;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.org.siri.siri21.*;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Class that maps a Situation to a GeneralMessage
 */
@Component
public class GeneralMessageMapper {


    @Autowired
    SubscriptionConfig subscriptionConfig;

    @Autowired
    StopPlaceUpdaterService stopPlaceUpdaterService;


    /**
     * Maps a situation to a general message
     *
     * @param situation situation to convert
     * @return the created GeneralMessage
     */
    public GeneralMessage mapToGeneralMessage(String datasetId, PtSituationElement situation) {
        GeneralMessage generalMessage = new GeneralMessage();

        generalMessage.setFormatRef("France");
        generalMessage.setRecordedAtTime(situation.getCreationTime());

        mapInfoId(generalMessage, situation);
        mapInfoChannelRef(generalMessage);
        mapValidUntil(generalMessage, situation);
        mapContent(datasetId, generalMessage, situation);

        generalMessage.setExtensions(situation.getExtensions());

        return generalMessage;

    }

    private void mapContent(String datasetId, GeneralMessage generalMessage, PtSituationElement situation) {
        Content content = new Content();
        Message msg = new Message();

        msg.setMsgText(getMsgText(situation));
        msg.setMsgType("textOnly");

        if (situation.getAffects() != null) {
            mapAffects(datasetId, content, situation);
        }

        content.setMessage(msg);
        generalMessage.setContent(content);
    }

    public void mapAffects(String datasetId, Content content, PtSituationElement situation) {
        Set<String> groupOfLineRefs = new HashSet<>();
        Set<String> lineRefs = new HashSet<>();
        Set<String> stopPointRefs = new HashSet<>();
        if (situation.getAffects().getNetworks() != null) {
            for (var affectedNetwork : situation.getAffects().getNetworks().getAffectedNetworks()) {
                if (affectedNetwork.getNetworkRef() != null) {
                    groupOfLineRefs.add(affectedNetwork.getNetworkRef().getValue());
                }
                for (var affectedRoute : affectedNetwork.getSelectedRoutes()) {
                    mapAffectedRoute(affectedRoute, stopPointRefs);
                }
                for (var affectedLine : affectedNetwork.getAffectedLines()) {
                    if (affectedLine.getLineRef() != null) {
                        lineRefs.add(affectedLine.getLineRef().getValue());
                    }
                    if (affectedLine.getRoutes() != null) {
                        for (var affectedRoute : affectedLine.getRoutes().getAffectedRoutes()) {
                            mapAffectedRoute(affectedRoute, stopPointRefs);
                        }
                    }
                }

            }
        }
        if (situation.getAffects().getStopPoints() != null) {
            for (var affectedStopPoint : situation.getAffects().getStopPoints().getAffectedStopPoints()) {
                stopPointRefs.add(affectedStopPoint.getStopPointRef().getValue());
            }
        }

        if (situation.getAffects().getStopPlaces() != null) {
            for (AffectedStopPlaceStructure affectedStopPlace : situation.getAffects().getStopPlaces().getAffectedStopPlaces()) {
                String stopPlaceRef = affectedStopPlace.getStopPlaceRef().getValue();

                Optional<IdProcessingParameters> idProcessingParamsOpt = subscriptionConfig.getIdParametersForDataset(datasetId, ObjectType.STOP);

                if (idProcessingParamsOpt.isPresent()) {
                    IdProcessingParameters idProcessingParams = idProcessingParamsOpt.get();
                    stopPlaceRef = idProcessingParams.applyTransformationToString(stopPlaceRef);
                    stopPlaceRef = stopPlaceRef.replace(":Quay:", ":StopPlace:");
                    String stopPlaceMobiitiId = stopPlaceUpdaterService.get(stopPlaceRef);
                    List<String> children = stopPlaceUpdaterService.getStopPlaceChildren(stopPlaceMobiitiId);
                    if (children.isEmpty()) {
                        continue;
                    }

                    for (String child : children) {
                        List<String> childIds = stopPlaceUpdaterService.getReverse(child, datasetId);
                        String rawChildId = idProcessingParams.removeOutputPrefixAndSuffix(childIds.getFirst());
                        stopPointRefs.add(rawChildId);
                    }
                }
            }
        }
        content.setGroupOfLinesRefs(new ArrayList<>(groupOfLineRefs));
        content.setLineRefs(new ArrayList<>(lineRefs));
        content.setStopPointRefs(new ArrayList<>(stopPointRefs));
    }

    private static void mapAffectedRoute(AffectedRouteStructure affectedRoute, Set<String> stopPointRefs) {
        if (affectedRoute.getStopPoints() == null) {
            return;
        }
        stopPointRefs.addAll(affectedRoute.getStopPoints().getAffectedStopPointsAndLinkProjectionToNextStopPoints()
                .stream()
                .filter(e -> e instanceof AffectedStopPointStructure && ((AffectedStopPointStructure) e).getStopPointRef() != null)
                .map(e -> ((AffectedStopPointStructure) e).getStopPointRef().getValue())
                .collect(Collectors.toList()));
    }

    private static String getMsgText(PtSituationElement situation) {
        // Get descriptions without HTML tags / line breaks
        return situation.getDescriptions().stream().filter(d -> StringUtils.isNotBlank(d.getValue())).map(d -> Jsoup.parse(d.getValue()).text()).collect(Collectors.joining(", "));
    }

    private static void mapValidUntil(GeneralMessage generalMessage, PtSituationElement situation) {
        ZonedDateTime currentMax = null;

        for (HalfOpenTimestampOutputRangeStructure validityPeriod : situation.getValidityPeriods()) {
            if (currentMax == null || currentMax.isBefore(validityPeriod.getEndTime())) {
                currentMax = validityPeriod.getEndTime();
            }
        }

        if (currentMax == null) {
            currentMax = ZonedDateTime.now().plusYears(100);
        }

        generalMessage.setValidUntilTime(currentMax);
    }

    private static void mapInfoId(GeneralMessage generalMessage, PtSituationElement situation) {
        String msgId = situation.getSituationNumber().getValue();
        generalMessage.setItemIdentifier(msgId);

        InfoMessageRefStructure infoMess = new InfoMessageRefStructure();
        infoMess.setValue(msgId);
        generalMessage.setInfoMessageIdentifier(infoMess);

        SituationRef situationRef = new SituationRef();
        SituationSimpleRef simpleRef = new SituationSimpleRef();
        simpleRef.setValue(msgId);
        situationRef.setSituationSimpleRef(simpleRef);
        generalMessage.setSituationRef(situationRef);
    }

    private static void mapInfoChannelRef(GeneralMessage generalMessage) {
        InfoChannelRefStructure infoChannelRef = new InfoChannelRefStructure();
        infoChannelRef.setValue("Perturbation");
        generalMessage.setInfoChannelRef(infoChannelRef);
    }
}
