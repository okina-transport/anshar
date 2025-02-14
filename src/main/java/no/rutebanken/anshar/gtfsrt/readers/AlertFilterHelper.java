package no.rutebanken.anshar.gtfsrt.readers;

import lombok.Getter;
import no.rutebanken.anshar.routes.mapping.StopTimesService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

public class AlertFilterHelper {

    private final StopTimesService stopTimesService;

    private final List<String> routeIdToFilter;

    private final String datasetId;

    private final Set<String> agencies;

    private final Set<String> affectedStops;

    private final Map<String, Boolean> mapRouteId;

    private final Map<String, Boolean> mapTripId;

    private final Set<Integer> routeType;

    @Getter
    private boolean shouldKeepAlert = false;

    public AlertFilterHelper(StopTimesService stopTimesService, String datasetId,List<String> routeIdList) {
        this.stopTimesService = stopTimesService;
        this.routeIdToFilter = routeIdList;
        this.datasetId = datasetId;
        this.agencies = new HashSet<>();
        this.affectedStops = new HashSet<>();
        this.routeType = new HashSet<>();
        this.mapRouteId = new HashMap<>();
        this.mapTripId = new HashMap<>();
    }

    public void addAgencyId(String agencyId) {
        this.shouldKeepAlert = true;
        this.agencies.add(agencyId);
    }

    public void addAffectedStops(String stopId) {
        this.affectedStops.add(stopId);
    }

    public void addRoute(String routeId) {
        String routeIdInCache = "";
        if (stopTimesService != null) {
            routeIdInCache = stopTimesService.checkIfKnownRouteId(datasetId, routeId).orElse("");
        }
        boolean shouldBeFiltered = false;
        if (StringUtils.isNotBlank(routeIdInCache)) {
            shouldBeFiltered = !routeIdToFilter.contains(routeIdInCache);
            shouldKeepAlert = !shouldBeFiltered;
        }
        this.mapRouteId.put(routeId, shouldBeFiltered);
    }

    public void addTrip(String tripId) {
        String routeIdInCache = "";
        if (stopTimesService != null) {
            routeIdInCache = stopTimesService.getRouteId(datasetId, tripId).orElse("");
        }
        boolean shouldBeFiltered = false;
        if (StringUtils.isNotBlank(routeIdInCache)) {
            shouldBeFiltered = !routeIdToFilter.contains(routeIdInCache);
            shouldKeepAlert = !shouldBeFiltered;
        }
        this.mapTripId.put(tripId, shouldBeFiltered);
    }

    public void addRouteType(Integer routeType) {
        this.routeType.add(routeType);
        this.shouldKeepAlert = true;
    }

    public boolean checkAllEntity() {
        return (notAnyRouteOrTripAffected() && (CollectionUtils.isNotEmpty(affectedStops) || CollectionUtils.isNotEmpty(agencies) || CollectionUtils.isNotEmpty(routeType)))
                || notAnyRouteOrTripToFilter();
    }

    private boolean notAnyRouteOrTripAffected() {
        return mapTripId.isEmpty() && mapRouteId.isEmpty();
    }

    private boolean notAnyRouteOrTripToFilter() {
        return mapRouteId.values().stream().allMatch(Boolean.FALSE::equals)
                && mapTripId.values().stream().allMatch(Boolean.FALSE::equals);
    }
}
