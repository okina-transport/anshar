package no.rutebanken.anshar.routes.siri.handlers;

import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.routes.mapping.ExternalIdsService;
import no.rutebanken.anshar.routes.mapping.LineUpdaterService;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.*;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class Utils {

    private static final Logger logger = LoggerFactory.getLogger(Utils.class);

    @Autowired
    private ExternalIdsService externalIdsService;

    @Autowired
    private SubscriptionConfig subscriptionConfig;

    @Autowired
    private LineUpdaterService lineUpdaterService;


    /**
     * Read a siri and checks all line references : if a reference to a flexibleLine is found, replaces ":Line:" by "FlexibleLine"
     *
     * @param siri Siri object to update with flexible lines modifications
     */
    public void handleFlexibleLines(Siri siri) {


        if (siri.getServiceDelivery() == null) {
            return;
        }

        ServiceDelivery delivery = siri.getServiceDelivery();

        if (delivery.getSituationExchangeDeliveries() != null && !delivery.getSituationExchangeDeliveries().isEmpty()) {
            handleFlexibleLineInSX(delivery.getSituationExchangeDeliveries());
        }
    }

    private void handleFlexibleLineInSX(List<SituationExchangeDeliveryStructure> situationExchangeDeliveries) {

        for (SituationExchangeDeliveryStructure situationExchangeDelivery : situationExchangeDeliveries) {

            if (situationExchangeDelivery.getSituations() == null || situationExchangeDelivery.getSituations().getPtSituationElements().isEmpty()) {
                continue;
            }

            for (PtSituationElement ptSituationElement : situationExchangeDelivery.getSituations().getPtSituationElements()) {

                if (ptSituationElement.getAffects() == null || ptSituationElement.getAffects().getNetworks() == null || ptSituationElement.getAffects().getNetworks().getAffectedNetworks().size() == 0) {
                    continue;
                }

                for (AffectsScopeStructure.Networks.AffectedNetwork affectedNetwork : ptSituationElement.getAffects().getNetworks().getAffectedNetworks()) {
                    handleFlexibleLineInAffectedNetworks(affectedNetwork);
                }
            }
        }
    }

    private void handleFlexibleLineInAffectedNetworks(AffectsScopeStructure.Networks.AffectedNetwork affectedNetwork) {

        if (affectedNetwork.getAffectedLines() == null || affectedNetwork.getAffectedLines().size() == 0) {
            return;
        }

        for (AffectedLineStructure affectedLine : affectedNetwork.getAffectedLines()) {
            if (affectedLine.getLineRef() == null) {
                continue;
            }

            LineRef lineRef = affectedLine.getLineRef();
            String originalLineId = lineRef.getValue();
            if (lineUpdaterService.isLineFlexible(originalLineId.replace(":LOC", ""))) {
                lineRef.setValue(originalLineId.replace(":Line", ":FlexibleLine"));
            }
        }
    }

    public Set<String> convertFromAltIdsToImportedIdsLine(Set<String> originalMonitoringRefs, String datasetId) {
        return originalMonitoringRefs.stream()
                .flatMap(id -> externalIdsService.getReverseAltIdLines(datasetId, id).stream().flatMap(String::lines))
                .collect(Collectors.toSet());
    }

    /**
     * Creates a json-string containing all potential errormessage-values
     *
     * @param errorCondition the error condition to filter
     * @return the error contents
     */
    public String getErrorContents(ServiceDeliveryErrorConditionElement errorCondition) {
        String errorContents = "";
        if (errorCondition != null) {
            Map<String, String> errorMap = new HashMap<>();
            String accessNotAllowed = getErrorText(errorCondition.getAccessNotAllowedError());
            String allowedResourceUsageExceeded = getErrorText(errorCondition.getAllowedResourceUsageExceededError());
            String beyondDataHorizon = getErrorText(errorCondition.getBeyondDataHorizon());
            String capabilityNotSupportedError = getErrorText(errorCondition.getCapabilityNotSupportedError());
            String endpointDeniedAccessError = getErrorText(errorCondition.getEndpointDeniedAccessError());
            String endpointNotAvailableAccessError = getErrorText(errorCondition.getEndpointNotAvailableAccessError());
            String invalidDataReferencesError = getErrorText(errorCondition.getInvalidDataReferencesError());
            String parametersIgnoredError = getErrorText(errorCondition.getParametersIgnoredError());
            String serviceNotAvailableError = getErrorText(errorCondition.getServiceNotAvailableError());
            String unapprovedKeyAccessError = getErrorText(errorCondition.getUnapprovedKeyAccessError());
            String unknownEndpointError = getErrorText(errorCondition.getUnknownEndpointError());
            String unknownExtensionsError = getErrorText(errorCondition.getUnknownExtensionsError());
            String unknownParticipantError = getErrorText(errorCondition.getUnknownParticipantError());
            String noInfoForTopicError = getErrorText(errorCondition.getNoInfoForTopicError());
            String otherError = getErrorText(errorCondition.getOtherError());

            String description = getDescriptionText(errorCondition.getDescription());

            if (accessNotAllowed != null) {
                errorMap.put("accessNotAllowed", accessNotAllowed);
            }
            if (allowedResourceUsageExceeded != null) {
                errorMap.put("allowedResourceUsageExceeded", allowedResourceUsageExceeded);
            }
            if (beyondDataHorizon != null) {
                errorMap.put("beyondDataHorizon", beyondDataHorizon);
            }
            if (capabilityNotSupportedError != null) {
                errorMap.put("capabilityNotSupportedError", capabilityNotSupportedError);
            }
            if (endpointDeniedAccessError != null) {
                errorMap.put("endpointDeniedAccessError", endpointDeniedAccessError);
            }
            if (endpointNotAvailableAccessError != null) {
                errorMap.put("endpointNotAvailableAccessError", endpointNotAvailableAccessError);
            }
            if (invalidDataReferencesError != null) {
                errorMap.put("invalidDataReferencesError", invalidDataReferencesError);
            }
            if (parametersIgnoredError != null) {
                errorMap.put("parametersIgnoredError", parametersIgnoredError);
            }
            if (serviceNotAvailableError != null) {
                errorMap.put("serviceNotAvailableError", serviceNotAvailableError);
            }
            if (unapprovedKeyAccessError != null) {
                errorMap.put("unapprovedKeyAccessError", unapprovedKeyAccessError);
            }
            if (unknownEndpointError != null) {
                errorMap.put("unknownEndpointError", unknownEndpointError);
            }
            if (unknownExtensionsError != null) {
                errorMap.put("unknownExtensionsError", unknownExtensionsError);
            }
            if (unknownParticipantError != null) {
                errorMap.put("unknownParticipantError", unknownParticipantError);
            }
            if (noInfoForTopicError != null) {
                errorMap.put("noInfoForTopicError", noInfoForTopicError);
            }
            if (otherError != null) {
                errorMap.put("otherError", otherError);
            }
            if (description != null) {
                errorMap.put("description", description);
            }

            errorContents = JSONObject.toJSONString(errorMap);
        }
        return errorContents;
    }

    private String getErrorText(ErrorCodeStructure accessNotAllowedError) {
        if (accessNotAllowedError != null) {
            return accessNotAllowedError.getErrorText();
        }
        return null;
    }

    private String getDescriptionText(ErrorDescriptionStructure description) {
        if (description != null) {
            return description.getValue();
        }
        return null;
    }

    /**
     * Builds a map with key = datasetId and value = idProcessingParams for this dataset and objectType = stop
     *
     * @param datasetList
     * @return
     */
    public Map<String, IdProcessingParameters> buildIdProcessingMap(List<String> datasetList, ObjectType objectType) {
        Map<String, IdProcessingParameters> resultMap = new HashMap<>();

        for (String dataset : datasetList) {
            Optional<IdProcessingParameters> idParamsOpt = subscriptionConfig.getIdParametersForDataset(dataset, objectType);
            idParamsOpt.ifPresent(idParams -> resultMap.put(dataset, idParams));
        }
        return resultMap;
    }

    /**
     * Check if there are invalid references and write them to server response
     *
     * @param siri                  the response that will be sent to client
     * @param invalidDataReferences list of invalid data references (invalid references are references requested by client that doesn't exist in subcriptions)
     */
    public void handleInvalidDataReferences(Siri siri, List<String> invalidDataReferences) {
        if (invalidDataReferences.isEmpty() || siri.getServiceDelivery().getVehicleMonitoringDeliveries().isEmpty())
            return;

        //Error references need to be inserted in server response to inform client
        ServiceDeliveryErrorConditionElement errorCondition = new ServiceDeliveryErrorConditionElement();
        InvalidDataReferencesErrorStructure invalidDataStruct = new InvalidDataReferencesErrorStructure();
        invalidDataStruct.setErrorText("InvalidDataReferencesError");
        errorCondition.setInvalidDataReferencesError(invalidDataStruct);
        ErrorDescriptionStructure errorDesc = new ErrorDescriptionStructure();
        errorDesc.setValue(String.join(",", invalidDataReferences));
        errorCondition.setDescription(errorDesc);
        siri.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).setErrorCondition(errorCondition);
        String requestMsgRef = siri.getServiceDelivery().getRequestMessageRef().getValue();
        siri.getServiceDelivery().getVehicleMonitoringDeliveries().forEach(vm -> logger.info("requestorRef:" + requestMsgRef + " - " + getErrorContents(vm.getErrorCondition())));

    }

    /**
     * Count the number of vehicleActivities existing in the response
     *
     * @param siri
     * @return the number of vehicle activities
     */
    public int countVehicleActivityResults(Siri siri) {
        int nbOfResults = 0;
        if (siri.getServiceDelivery() != null && siri.getServiceDelivery().getVehicleMonitoringDeliveries() != null) {
            for (VehicleMonitoringDeliveryStructure vehicleMonitoringDelivery : siri.getServiceDelivery().getVehicleMonitoringDeliveries()) {
                if (vehicleMonitoringDelivery.getVehicleActivities() == null)
                    continue;

                nbOfResults = nbOfResults + vehicleMonitoringDelivery.getVehicleActivities().size();
            }
        }
        return nbOfResults;
    }

    /**
     * Read incoming data to find the stopRefs (to identify which stops has received data)
     *
     * @param incomingData incoming message
     * @return A string with all stops found in the incoming message
     */
    public String getStopRefs(Siri incomingData) {
        List<StopMonitoringDeliveryStructure> stopMonitoringDeliveries = incomingData.getServiceDelivery().getStopMonitoringDeliveries();
        List<String> stopRefs = new ArrayList<>();


        for (StopMonitoringDeliveryStructure stopMonitoringDelivery : stopMonitoringDeliveries) {
            for (MonitoredStopVisit monitoredStopVisit : stopMonitoringDelivery.getMonitoredStopVisits()) {
                stopRefs.add(monitoredStopVisit.getMonitoringRef().getValue());
            }
        }

        return stopRefs.stream()
                .distinct()
                .collect(Collectors.joining(","));
    }

}
