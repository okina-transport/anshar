package no.rutebanken.anshar.data.util;

import com.hazelcast.query.Predicate;
import no.rutebanken.anshar.data.SiriObjectStorageKey;
import uk.org.siri.siri21.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SiriObjectStorageKeyUtil {


    public static Predicate<SiriObjectStorageKey, MonitoredStopVisit> getStopPredicate(Set<String> searchedStopRefs, String datasetId, List<String> excludedDatasetIds) {
        return entry -> isKeyCompliantWithFilters(entry.getKey(), null, null, searchedStopRefs, datasetId, excludedDatasetIds, null, null);
    }

    public static Predicate<SiriObjectStorageKey, VehicleActivityStructure> getVehiclePredicate(Set<String> linerefSet, Set<String> vehicleRefSet, String datasetId, List<String> excludedDatasetIds) {
        return entry -> isKeyCompliantWithFilters(entry.getKey(), linerefSet, vehicleRefSet, null, datasetId, excludedDatasetIds, null, null);
    }

    public static Predicate<SiriObjectStorageKey, EstimatedVehicleJourney> getEstimateTimetablePredicate(Set<String> linerefSet, Set<String> vehicleRefSet, String datasetId, List<String> excludedDatasetIds) {
        return entry -> isKeyCompliantWithFilters(entry.getKey(), linerefSet, vehicleRefSet, null, datasetId, excludedDatasetIds, null, null);
    }

    public static Predicate<SiriObjectStorageKey, PtSituationElement> getSituationExchangePredicate(String datasetId) {
        return entry -> isKeyCompliantWithFilters(entry.getKey(), null, null, null, datasetId, null, null, null);
    }

    public static Predicate<SiriObjectStorageKey, GeneralMessage> getGeneralMessagePredicate(String datasetId, List<InfoChannelRefStructure> requestedChannels) {

        List<String> typeList = new ArrayList<>();

        if (requestedChannels != null) {
            requestedChannels.stream()
                    .map(InfoChannelRefStructure::getValue)
                    .forEach(typeList::add);
        }


        return entry -> isKeyCompliantWithFilters(entry.getKey(), null, null, null, datasetId, null, typeList, null);
    }

    public static Predicate<SiriObjectStorageKey, GeneralMessageCancellation> getGeneralMessageCancellationsPredicate(String datasetId, List<InfoChannelRefStructure> requestedChannels) {

        List<String> typeList = new ArrayList<>();

        if (requestedChannels != null) {
            requestedChannels.stream()
                    .map(InfoChannelRefStructure::getValue)
                    .forEach(typeList::add);
        }


        return entry -> isKeyCompliantWithFilters(entry.getKey(), null, null, null, datasetId, null, typeList, null);
    }

    public static Predicate<SiriObjectStorageKey, FacilityConditionStructure> getFacilityMonitoringPredicate(String datasetId, Set<String> requestedFacilities,
                                                                                                             Set<String> requestedLineRef, Set<String> requestedVehicleRef, Set<String> requestedStopPoints, List<String> excludeData) {

        return entry -> isKeyCompliantWithFilters(entry.getKey(), requestedLineRef, requestedVehicleRef, requestedStopPoints, datasetId, excludeData, null, requestedFacilities);
    }


    /**
     * Determines if a key is compliant or not with filters given as parameters
     *
     * @param key                The key to check
     * @param linerefSet         The optional filters on lines
     * @param vehicleRefSet      The optional filters on vehicles
     * @param datasetId          The optional filters on datasetId
     * @param excludedDatasetIds The optional filters on excludedDatasetIds
     * @param types              The optional filters on types
     * @return true :the key is compliant with filters
     * false : the key is not compliant
     */
    public static boolean isKeyCompliantWithFilters(SiriObjectStorageKey key, Set<String> linerefSet, Set<String> vehicleRefSet, Set<String> stopRefSet, String datasetId, List<String> excludedDatasetIds, List<String> types,
                                                    Set<String> facilityRefSet) {
        if (datasetId != null && !datasetId.equalsIgnoreCase(key.getCodespaceId())) {
            return false;
        }

        if (excludedDatasetIds != null && !excludedDatasetIds.isEmpty() && excludedDatasetIds.contains(key.getCodespaceId())) {
            return false;
        }

        if (linerefSet != null && !linerefSet.isEmpty() && !linerefSet.contains(key.getLineRef())) {
            return false;
        }

        if (vehicleRefSet != null && !vehicleRefSet.isEmpty() && !vehicleRefSet.contains(key.getKey())) {
            return false;
        }

        if (facilityRefSet != null && !facilityRefSet.isEmpty() && !facilityRefSet.contains(key.getFacilityRef())) {
            return false;
        }

        if (stopRefSet != null && !stopRefSet.isEmpty() && !stopRefSet.contains(key.getStopRef())) {
            return false;
        }

        if (types != null && !types.isEmpty() && !types.contains(key.getType())) {
            return false;
        }

        if (datasetId == null && (stopRefSet == null || stopRefSet.isEmpty()) && (excludedDatasetIds == null || excludedDatasetIds.isEmpty())) {
            return false;
        }

        return true;
    }


}
