package no.rutebanken.anshar.util;

import io.micrometer.core.instrument.util.StringUtils;
import no.rutebanken.anshar.config.IdProcessingParameters;

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
        if (!idParametersOpt.isPresent()) {
            return originalMonitoringRefs;
        }

        IdProcessingParameters idParams = idParametersOpt.get();
        Set<String> revertedIds = new HashSet<>();

        for (String originalMonitoringRef : originalMonitoringRefs) {

            if (StringUtils.isNotEmpty(idParams.getOutputPrefixToAdd()) && originalMonitoringRef.startsWith(idParams.getOutputPrefixToAdd())) {
                originalMonitoringRef = originalMonitoringRef.substring(idParams.getOutputPrefixToAdd().length());
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
            revertedIds.add(originalMonitoringRef);
        }
        return revertedIds;
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
