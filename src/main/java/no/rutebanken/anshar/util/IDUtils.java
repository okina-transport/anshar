package no.rutebanken.anshar.util;

import org.apache.commons.lang3.StringUtils;
import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class IDUtils {


    private static long currentGTFSRTInternalId = 1_000_000;
    private static long currentDiscoveryInternalId = 5_000_000;

    /**
     * Revert searched Ids by user to go back to an original id
     *
     * @param originalMonitoringRefs the original ids that must be reverted
     * @param idParametersOpt        id parameters to apply to go back to original ids
     * @return
     */
    public static Set<String> revertMonitoringRefs(Set<String> originalMonitoringRefs, Optional<IdProcessingParameters> idParametersOpt) {
        if (idParametersOpt.isEmpty()) {
            return originalMonitoringRefs;
        }
        Set<String> revertedIds = new HashSet<>();
        for (String originalMonitoringRef : originalMonitoringRefs) {
            revertedIds.add(revertMonitoringRef(originalMonitoringRef, idParametersOpt));
        }
        return revertedIds;
    }

    public static String revertMonitoringRef(String originalMonitoringRef, Optional<IdProcessingParameters> idParametersOpt) {
        if (idParametersOpt.isEmpty()) {
            return originalMonitoringRef;
        }

        IdProcessingParameters idParams = idParametersOpt.get();


        String outputPrefixToAdd = idParams.getOutputPrefixToAdd();
        if (ObjectType.STOP.equals(idParams.getObjectType()) && idParams.getOutputPrefixToAdd().contains(":Quay:") && originalMonitoringRef.contains(":StopPlace:")) {
            outputPrefixToAdd = outputPrefixToAdd.replace(":Quay:", ":StopPlace:");
        }

        if (StringUtils.isNotEmpty(outputPrefixToAdd) && originalMonitoringRef.startsWith(outputPrefixToAdd)) {
            originalMonitoringRef = originalMonitoringRef.substring(outputPrefixToAdd.length());
        }

        if (StringUtils.isNotEmpty(idParams.getOutputSuffixToAdd()) && originalMonitoringRef.endsWith(idParams.getOutputSuffixToAdd())) {
            originalMonitoringRef = originalMonitoringRef.substring(0, originalMonitoringRef.length() - idParams.getOutputSuffixToAdd().length());
        }

        if (StringUtils.isNotEmpty(idParams.getInputPrefixToRemove())) {
            originalMonitoringRef = idParams.getInputPrefixToRemove() + originalMonitoringRef;
        }

        if (StringUtils.isNotEmpty(idParams.getInputSuffixToRemove())) {
            originalMonitoringRef = originalMonitoringRef + idParams.getInputSuffixToRemove();
        }

        return originalMonitoringRef;
    }

    public static long getUniqueInternalIdForGTFSRT() {
        currentGTFSRTInternalId++;
        return currentGTFSRTInternalId;
    }

    public static long getUniqueInternalIdForDiscoverySubscription() {
        currentDiscoveryInternalId++;
        return currentDiscoveryInternalId;
    }

}
