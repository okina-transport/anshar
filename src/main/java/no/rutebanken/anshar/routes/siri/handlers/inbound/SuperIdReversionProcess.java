package no.rutebanken.anshar.routes.siri.handlers.inbound;

import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.*;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;


/**
 * Class used to revert super ids (e.g:MOBIITI:Quay:1234) to provider id (PROV1:Quay:xxxx)
 * (some of our clients are sending back our own ids. We need to revert them because we only store provider ids in memory)
 */
@Service
public class SuperIdReversionProcess {

    @Autowired
    StopPlaceUpdaterService stopPlaceUpdaterService;

    @Value("${anshar.super.identifier}")
    private String superIdentifier;

    @Autowired
    SubscriptionConfig subscriptionConfig;


    /**
     * Revert all ids contained in a siri (lines, stops, etc)
     *
     * @param incoming  input siri to process
     * @param datasetId
     * @return the siri with all reverted ids
     */
    public Siri revertIds(Siri incoming, String datasetId) {

        if (incoming.getServiceDelivery() == null && incoming.getServiceDelivery().getSituationExchangeDeliveries() == null && incoming.getServiceDelivery().getSituationExchangeDeliveries().size() == 0) {
            return incoming;
        }

        for (SituationExchangeDeliveryStructure situationExchangeDelivery : incoming.getServiceDelivery().getSituationExchangeDeliveries()) {
            revertIdsInSituationExchangeDelivery(situationExchangeDelivery, datasetId);
        }
        return incoming;
    }

    private void revertIdsInSituationExchangeDelivery(SituationExchangeDeliveryStructure situationExchangeDelivery, String datasetId) {
        if (situationExchangeDelivery.getSituations() == null || situationExchangeDelivery.getSituations().getPtSituationElements() == null || situationExchangeDelivery.getSituations().getPtSituationElements().size() == 0) {
            return;
        }

        for (PtSituationElement ptSituationElement : situationExchangeDelivery.getSituations().getPtSituationElements()) {
            if (ptSituationElement.getAffects() == null) {
                continue;
            }

            AffectsScopeStructure affects = ptSituationElement.getAffects();
            if (affects.getStopPoints() != null && affects.getStopPoints().getAffectedStopPoints() != null) {
                for (AffectedStopPointStructure affectedStopPoint : affects.getStopPoints().getAffectedStopPoints()) {
                    revertIdInStopPoint(affectedStopPoint, datasetId);
                }
            }

            if (affects.getNetworks() != null && affects.getNetworks().getAffectedNetworks() != null) {
                for (AffectsScopeStructure.Networks.AffectedNetwork affectedNetwork : affects.getNetworks().getAffectedNetworks()) {
                    revertIdInNetwork(affectedNetwork, datasetId);
                }
            }
        }
    }

    private void revertIdInNetwork(AffectsScopeStructure.Networks.AffectedNetwork affectedNetwork, String datasetId) {
        if (affectedNetwork.getAffectedLines() != null) {
            affectedNetwork.getAffectedLines().forEach(affectedLine -> revertIdInLine(affectedLine, datasetId));
        }

        if (affectedNetwork.getNetworkRef() != null) {
            String networkRef = affectedNetwork.getNetworkRef().getValue();

            String prefix = superIdentifier + ":Network:";

            if (networkRef.startsWith(prefix)) {
                networkRef = networkRef.substring(prefix.length());
            }
            affectedNetwork.getNetworkRef().setValue(networkRef);

        }
    }

    private void revertIdInLine(AffectedLineStructure affectedLine, String datasetId) {

        if (affectedLine.getLineRef() != null) {
            String lineRef = affectedLine.getLineRef().getValue();

            String suffix = ":LOC";
            if (lineRef.endsWith(suffix)) {
                lineRef = lineRef.substring(0, lineRef.length() - suffix.length());
            }

            String prefix = superIdentifier + ":Line:";
            if (lineRef.startsWith(prefix)) {
                lineRef = lineRef.substring(prefix.length());
            }
            affectedLine.getLineRef().setValue(lineRef);
        }

        if (affectedLine.getRoutes() != null) {
            affectedLine.getRoutes().getAffectedRoutes().forEach(affectedRoute -> revertIdInRoute(affectedRoute, datasetId));
        }
    }

    private void revertIdInRoute(AffectedRouteStructure affectedRoute, String datasetId) {
        if (affectedRoute.getStopPoints() != null) {
            for (Serializable affectedStopPointsAndLinkProjectionToNextStopPoint : affectedRoute.getStopPoints().getAffectedStopPointsAndLinkProjectionToNextStopPoints()) {
                if (affectedStopPointsAndLinkProjectionToNextStopPoint instanceof AffectedStopPointStructure) {
                    AffectedStopPointStructure affectedStopPoint = (AffectedStopPointStructure) affectedStopPointsAndLinkProjectionToNextStopPoint;
                    revertIdInStopPoint(affectedStopPoint, datasetId);
                }
            }
        }
    }

    private void revertIdInStopPoint(AffectedStopPointStructure affectedStopPoint, String datasetId) {
        if (affectedStopPoint.getStopPointRef() == null) {
            return;
        }

        String oldValue = affectedStopPoint.getStopPointRef().getValue();
        List<String> providerIds = stopPlaceUpdaterService.getReverse(oldValue, datasetId);
        if (!providerIds.isEmpty()) {
            String idFromProfider = providerIds.get(0);
            Optional<IdProcessingParameters> idParamsOpt = subscriptionConfig.getIdParametersForDataset(datasetId, ObjectType.STOP);
            if (idParamsOpt.isPresent()) {
                IdProcessingParameters idParams = idParamsOpt.get();
                // reverted ids will have the format : "OUTPUTPREFIX xxxx OUTPUTSUFFIX". We need to remove prefix and suffix to store the raw code in memory
                idFromProfider = idParams.removeOutputPrefixAndSuffix(idFromProfider);
            }
            affectedStopPoint.getStopPointRef().setValue(idFromProfider);
        }
    }


}
