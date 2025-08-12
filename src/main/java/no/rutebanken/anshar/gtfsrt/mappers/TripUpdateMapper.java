package no.rutebanken.anshar.gtfsrt.mappers;

import com.google.transit.realtime.GtfsRealtime;
import no.rutebanken.anshar.api.PublishedLineNameMapping;
import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.util.CustomStringUtils;
import no.rutebanken.anshar.routes.mapping.LineUpdaterService;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.mapping.StopTimesService;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.util.DateUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.org.siri.siri21.*;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;


/***
 * Utility class to convert Trip Update (GTFS RT)  to estimated time table (SIRI)
 */
@Component
public class TripUpdateMapper {

    private static final Logger logger = LoggerFactory.getLogger(TripUpdateMapper.class);

    private final StopTimesService stopTimesService;
    private final StopPlaceUpdaterService stopPlaceService;
    private final LineUpdaterService lineUpdaterService;
    private final SubscriptionConfig subscriptionConfig;

    public TripUpdateMapper(StopTimesService stopTimesService, StopPlaceUpdaterService stopPlaceService, LineUpdaterService lineUpdaterService, SubscriptionConfig subscriptionConfig) {
        this.stopTimesService = stopTimesService;
        this.stopPlaceService = stopPlaceService;
        this.lineUpdaterService = lineUpdaterService;
        this.subscriptionConfig = subscriptionConfig;
    }

    /**
     * Read the tripUpdate and map arrival times (aimed and expected) to siri object
     *
     * @param stopTimeUpdate   source object from GTFS-RT file that contains data to read
     * @param aimedArrivalTime theoretical arrival time
     * @param tripDelay        whole trip delay
     */
    private static Optional<ZonedDateTime> buildExpectedArrivalTime(GtfsRealtime.TripUpdate.StopTimeUpdate stopTimeUpdate,
                                                                    @Nullable ZonedDateTime aimedArrivalTime,
                                                                    @Nullable Integer tripDelay) {
        if (stopTimeUpdate.hasArrival() && stopTimeUpdate.getArrival().hasTime()) {
            return Optional.of(ZonedDateTime.ofInstant(Instant.ofEpochSecond(stopTimeUpdate.getArrival().getTime()),
                    ZoneId.systemDefault()));
        } else if (stopTimeUpdate.hasArrival() && stopTimeUpdate.getArrival().hasDelay() && aimedArrivalTime != null) {
            return Optional.of(aimedArrivalTime.plusSeconds(stopTimeUpdate.getArrival().getDelay()));
        } else if (tripDelay != null && aimedArrivalTime != null) {
            return Optional.of(aimedArrivalTime.plusSeconds(tripDelay));
        }
        return Optional.empty();
    }

    /**
     * Read the tripUpdate and map departure times (aimed and expected) to siri object
     *
     * @param stopTimeUpdate     source object from GTFS-RT file that contains data to read
     * @param aimedDepartureTime theoretical departure time
     * @param tripDelay          whole trip delay
     */
    private static Optional<ZonedDateTime> buildExpectedDepartureTime(GtfsRealtime.TripUpdate.StopTimeUpdate stopTimeUpdate,
                                                                      @Nullable ZonedDateTime aimedDepartureTime,
                                                                      @Nullable Integer tripDelay) {
        if (stopTimeUpdate.hasDeparture() && stopTimeUpdate.getDeparture().hasTime()) {
            return Optional.of(ZonedDateTime.ofInstant(Instant.ofEpochSecond(stopTimeUpdate.getDeparture().getTime()),
                    ZoneId.systemDefault()));
        } else if (stopTimeUpdate.hasDeparture() && stopTimeUpdate.getDeparture().hasDelay() && aimedDepartureTime != null) {
            return Optional.of(aimedDepartureTime.plusSeconds(stopTimeUpdate.getDeparture().getDelay()));
        } else if (tripDelay != null && aimedDepartureTime != null) {
            return Optional.of(aimedDepartureTime.plusSeconds(tripDelay));
        }
        return Optional.empty();
    }

    /**
     * Read a tripUpdate and create a vehicleJourneyRef
     *
     * @param tripUpdate the tripUpdate from which the vehicleJourney must be read
     * @return The vehicleJourneyRef
     */
    private static FramedVehicleJourneyRefStructure createVehicleJourneyRef(GtfsRealtime.TripUpdate tripUpdate) {
        String tripId = tripUpdate.getTrip() != null ? tripUpdate.getTrip().getTripId() : "";
        FramedVehicleJourneyRefStructure vehicleJourneyRef = new FramedVehicleJourneyRefStructure();
        vehicleJourneyRef.setDatedVehicleJourneyRef(tripId);

        DataFrameRefStructure dataFrameRef = new DataFrameRefStructure();
        dataFrameRef.setValue(tripId);
        vehicleJourneyRef.setDataFrameRef(dataFrameRef);
        return vehicleJourneyRef;
    }

    private static EstimatedCall mapEstimatedCallFromTripUpdate(GtfsRealtime.TripUpdate.StopTimeUpdate stopTimeUpdate,
                                                                @Nullable ZonedDateTime aimedArrivalTime,
                                                                @Nullable ZonedDateTime aimedDepartureTime,
                                                                @Nullable Integer tripDelay) {

        EstimatedCall estimatedCall = new EstimatedCall();
        StopPointRefStructure stopPointRefStructure = new StopPointRefStructure();
        stopPointRefStructure.setValue(stopTimeUpdate.getStopId());
        estimatedCall.setStopPointRef(stopPointRefStructure);
        estimatedCall.setOrder(BigInteger.valueOf(stopTimeUpdate.getStopSequence()));
        estimatedCall.setAimedArrivalTime(aimedArrivalTime);
        estimatedCall.setAimedDepartureTime(aimedDepartureTime);
        buildExpectedArrivalTime(stopTimeUpdate, aimedArrivalTime, tripDelay).ifPresent(estimatedCall::setExpectedArrivalTime);
        buildExpectedDepartureTime(stopTimeUpdate, aimedDepartureTime, tripDelay).ifPresent(estimatedCall::setExpectedDepartureTime);

        return estimatedCall;

    }

    private static boolean shouldFilterStop(List<String> routeIdList, String stopId, String routeIdInCache) {
        return stopId != null && StringUtils.isNotBlank(routeIdInCache) && !routeIdList.isEmpty() && !routeIdList.contains(routeIdInCache);
    }

    /**
     * Maps a GTFS-Realtime {@link GtfsRealtime.TripUpdate} into a list of {@link MonitoredStopVisit} instances.
     * This method extracts relevant stop visit information and structures it for monitoring purposes.
     *
     * @param tripUpdate               The GTFS-Realtime {@link GtfsRealtime.TripUpdate} containing trip update data.
     * @param datasetId                The identifier of the dataset associated with the trip update.
     * @param routeIdList              A list of route IDs used to filter relevant stop visits.
     * @param publishedLineNameMapping The way to map PublishedLineName from GTFS-RT TripUpdate to Siri StopMonitoring
     * @return A list of {@link MonitoredStopVisit} objects representing structured stop visit data.
     */
    public List<MonitoredStopVisit> mapStopVisitFromTripUpdate(GtfsRealtime.TripUpdate tripUpdate, String datasetId, List<String> routeIdList, PublishedLineNameMapping publishedLineNameMapping) {
        List<MonitoredStopVisit> stopVisitList = new ArrayList<>();
        FramedVehicleJourneyRefStructure vehicleJourneyRef = createVehicleJourneyRef(tripUpdate);

        String tripId = tripUpdate.getTrip().getTripId();
        if (tripUpdate.getTrip().getScheduleRelationship() != null && GtfsRealtime.TripDescriptor.ScheduleRelationship.CANCELED.equals(
                tripUpdate.getTrip().getScheduleRelationship())) {
            return Collections.emptyList();
        }
        LineRef lineRef = createLineRef(tripUpdate, datasetId, tripId);
        DestinationRef destinationRef = createDestinationRef(datasetId, tripId);
        NaturalLanguageStringStructure destinationName = createDestinationName(destinationRef, datasetId);

        for (GtfsRealtime.TripUpdate.StopTimeUpdate stopTimeUpdate : tripUpdate.getStopTimeUpdateList()) {
            MonitoredStopVisit stopVisit = new MonitoredStopVisit();

            String stopId = getStopId(stopTimeUpdate, datasetId, tripId);
            String routeIdInCache = stopTimesService.getRouteId(datasetId, tripId).orElse("");
            Optional<StopTimesService.StopTimeCacheEntry> cacheEntry = stopTimesService.findStopTimeCacheEntryByDatasetIdAndTripIdAndStopId(datasetId, tripId, stopId);
            Optional<ZonedDateTime> aimedArrivalTime = cacheEntry.isPresent() ? DateUtils.convertGtfsTimeToZonedDateTime(cacheEntry.get().getArrivalTime()) : Optional.empty();
            Optional<ZonedDateTime> aimedDepartureTime = cacheEntry.isPresent() ? DateUtils.convertGtfsTimeToZonedDateTime(cacheEntry.get().getDepartureTime()) : aimedArrivalTime;
            if (shouldFilterStop(routeIdList, stopId, routeIdInCache)) {
                continue;
            }
            if (StringUtils.isEmpty(stopId)) {
                logger.error("Unable to determine stopId for dataset:{}, tripId:{}, stopSequence:{}, stopId:{}", datasetId, tripId, stopTimeUpdate.getStopSequence(), stopTimeUpdate.getStopId());
                continue;
            }
            mapMonitoringRef(stopVisit, stopId);

            MonitoredVehicleJourneyStructure monitoredVehicleStruct = new MonitoredVehicleJourneyStructure();
            monitoredVehicleStruct.setLineRef(lineRef);
            monitoredVehicleStruct.setDestinationRef(destinationRef);
            monitoredVehicleStruct.getDestinationNames().add(destinationName);
            monitoredVehicleStruct.setFramedVehicleJourneyRef(vehicleJourneyRef);
            // publishedLineNames is filled by line name in MonitoredStopVisits
            // we only fill it with line number when mapping is by line number
            if (PublishedLineNameMapping.LINE_NUMBER.equals(publishedLineNameMapping)) {
                String lineId = lineRef.getValue();
                Optional<IdProcessingParameters> ipp = subscriptionConfig.getIdParametersForDataset(datasetId, ObjectType.LINE);
                if (ipp.isPresent()) {
                    lineId = ipp.get().applyTransformationToString(lineId);
                }
                lineUpdaterService.getLineNumber(lineId).ifPresent(lineNumber -> {
                    NaturalLanguageStringStructure nlss = new NaturalLanguageStringStructure();
                    nlss.setValue(lineNumber);
                    monitoredVehicleStruct.getPublishedLineNames().add(nlss);
                });
            }
            monitoredVehicleStruct.setMonitored(true);
            if (tripUpdate.getTrip() != null && tripUpdate.getTrip().hasDirectionId()) {
                NaturalLanguageStringStructure directionName = new NaturalLanguageStringStructure();
                directionName.setValue(tripUpdate.getTrip().getDirectionId() == 0 ? "A" : "R");
                monitoredVehicleStruct.getDirectionNames().add(directionName);
            } else {
                stopTimesService.getDirectionId(datasetId, tripId).ifPresent(directionId -> {
                    NaturalLanguageStringStructure directionName = new NaturalLanguageStringStructure();
                    directionName.setValue(directionId);
                    monitoredVehicleStruct.getDirectionNames().add(directionName);
                });
            }

            MonitoredCallStructure monitoredCallStructure = new MonitoredCallStructure();
            aimedArrivalTime.ifPresent(monitoredCallStructure::setAimedArrivalTime);
            aimedDepartureTime.ifPresent(monitoredCallStructure::setAimedDepartureTime);
            StopPointRefStructure stopPointRef = new StopPointRefStructure();
            stopPointRef.setValue(stopId);
            monitoredCallStructure.setStopPointRef(stopPointRef);
            monitoredCallStructure.setOrder(BigInteger.valueOf(stopTimeUpdate.getStopSequence()));
            Integer tripDelay = tripUpdate.hasDelay() ? tripUpdate.getDelay() : null;
            buildExpectedArrivalTime(stopTimeUpdate, aimedArrivalTime.orElse(null), tripDelay).ifPresent(monitoredCallStructure::setExpectedArrivalTime);
            buildExpectedDepartureTime(stopTimeUpdate, aimedDepartureTime.orElse(null), tripDelay).ifPresent(monitoredCallStructure::setExpectedDepartureTime);

            monitoredVehicleStruct.setMonitoredCall(monitoredCallStructure);
            stopVisit.setMonitoredVehicleJourney(monitoredVehicleStruct);
            feedItemIdentifier(stopVisit, stopId);
            stopVisitList.add(stopVisit);
        }

        return stopVisitList;
    }

    /**
     * Feeed itemIdentifier field with a concatenation between vehicleJourney and stop
     *
     * @param stopVisit the stopVisit on which itemIdentifier must be updated
     * @param stopId    the stopId on which the visit occurs
     */
    private void feedItemIdentifier(MonitoredStopVisit stopVisit, String stopId) {
        String vehicleJourneyRef = stopVisit.getMonitoredVehicleJourney().getFramedVehicleJourneyRef().getDatedVehicleJourneyRef();
        stopVisit.setItemIdentifier(vehicleJourneyRef + "-" + stopId);
    }

    /**
     * Read the tripUpdate and create a lineRef with routeId included in tripUpdate
     *
     * @param tripUpdate The trip update from which the routeId must be read
     * @return The lineRef containing the routeId
     */
    private LineRef createLineRef(GtfsRealtime.TripUpdate tripUpdate, String datasetId, String tripId) {
        String routeId = tripUpdate.getTrip() != null && StringUtils.isNotEmpty(tripUpdate.getTrip().getRouteId()) ?
                tripUpdate.getTrip().getRouteId() : stopTimesService.getRouteId(datasetId, tripId).orElse("");
        LineRef lineRef = new LineRef();

        lineRef.setValue(routeId);
        return lineRef;
    }

    private DestinationRef createDestinationRef(String datasetId, String tripId) {

        DestinationRef destinationRef = new DestinationRef();
        destinationRef.setValue(stopTimesService.getDestinationId(datasetId, tripId).orElse(""));
        return destinationRef;
    }

    private NaturalLanguageStringStructure createDestinationName(DestinationRef destinationRef, String datasetId) {

        NaturalLanguageStringStructure naturalLanguageStringStructure = new NaturalLanguageStringStructure();
        naturalLanguageStringStructure.setValue(stopPlaceService.getStopName(destinationRef.getValue(), datasetId));
        return naturalLanguageStringStructure;
    }

    private String getStopId(GtfsRealtime.TripUpdate.StopTimeUpdate stopTimeUpdate, String datasetId, String tripId) {
        if (stopTimeUpdate.hasStopId() && StringUtils.isNotEmpty(stopTimeUpdate.getStopId())) {
            return stopTimeUpdate.getStopId();
        }
        return stopTimesService.findStopIdByDatasetIdAndTripIdAndStopSequence(datasetId, tripId, stopTimeUpdate.getStopSequence()).orElse(null);
    }

    private void mapMonitoringRef(MonitoredStopVisit stopVisit, String stopId) {

        MonitoringRefStructure monitoringRefStruct = new MonitoringRefStructure();
        monitoringRefStruct.setValue(stopId);
        stopVisit.setMonitoringRef(monitoringRefStruct);
    }

    /**
     * Maps a GTFS-Realtime {@link GtfsRealtime.TripUpdate} into an {@link EstimatedVehicleJourney}.
     * This method extracts relevant trip update details, including vehicle and stop information,
     * and structures it for estimated journey tracking.
     *
     * @param tripUpdate  The GTFS-Realtime {@link GtfsRealtime.TripUpdate} containing trip update data.
     * @param routeIdList A list of route IDs used to filter relevant vehicle journeys.
     * @return An {@link EstimatedVehicleJourney} object representing the structured journey data,
     * or {@code null} if the route ID is not in the provided list.
     */
    public EstimatedVehicleJourney mapVehicleJourneyFromTripUpdate(GtfsRealtime.TripUpdate tripUpdate, String datasetId, List<String> routeIdList) {
        GtfsRealtime.TripDescriptor tripDescriptor = tripUpdate.getTrip();
        if (!routeIdList.isEmpty()) {
            String routeIdInCache = "";
            if (tripDescriptor.hasTripId()) {
                routeIdInCache = stopTimesService.getRouteId(datasetId, tripDescriptor.getTripId()).orElse("");
            } else if (tripDescriptor.hasRouteId()) {
                routeIdInCache = stopTimesService.checkIfKnownRouteId(datasetId, tripDescriptor.getRouteId()) ? tripDescriptor.getRouteId() : "";
            }
            if (StringUtils.isNotBlank(routeIdInCache) && !routeIdList.contains(routeIdInCache)) {
                return null;
            }
        }

        EstimatedVehicleJourney journey = new EstimatedVehicleJourney();
        DatedVehicleJourneyRef datedVehicleJourneyRef = new DatedVehicleJourneyRef();
        datedVehicleJourneyRef.setValue(CustomStringUtils.removeSpecialCharacters(tripDescriptor.getTripId()));
        journey.setDatedVehicleJourneyRef(datedVehicleJourneyRef);

        FramedVehicleJourneyRefStructure vehicleJourneyRef = createVehicleJourneyRef(tripUpdate);
        journey.setFramedVehicleJourneyRef(vehicleJourneyRef);
        journey.setDataSource("MOBIITI");


        if (tripDescriptor.getRouteId() != null) {
            LineRef lineRef = new LineRef();
            lineRef.setValue(tripDescriptor.getRouteId());
            journey.setLineRef(lineRef);
        }

        GtfsRealtime.VehicleDescriptor vehicleDescriptor = tripUpdate.getVehicle();

        if (vehicleDescriptor.getId() != null) {
            VehicleRef vehicleRef = new VehicleRef();
            vehicleRef.setValue(vehicleDescriptor.getId());
            journey.setVehicleRef(vehicleRef);
        }

        EstimatedVehicleJourney.EstimatedCalls estimatedCalls = new EstimatedVehicleJourney.EstimatedCalls();

        for (GtfsRealtime.TripUpdate.StopTimeUpdate stopTimeUpdate : tripUpdate.getStopTimeUpdateList()) {
            String stopId = getStopId(stopTimeUpdate, datasetId, tripDescriptor.getTripId());
            Optional<StopTimesService.StopTimeCacheEntry> cacheEntry =
                    stopTimesService.findStopTimeCacheEntryByDatasetIdAndTripIdAndStopId(datasetId,
                            tripDescriptor.getTripId(),
                            stopId);
            Optional<ZonedDateTime> aimedArrivalTime = cacheEntry.isPresent() ? DateUtils.convertGtfsTimeToZonedDateTime(cacheEntry.get().getArrivalTime()) : Optional.empty();
            Optional<ZonedDateTime> aimedDepartureTime = cacheEntry.isPresent() ? DateUtils.convertGtfsTimeToZonedDateTime(cacheEntry.get().getDepartureTime()) : aimedArrivalTime;
            EstimatedCall estimatedCall = mapEstimatedCallFromTripUpdate(stopTimeUpdate, aimedArrivalTime.orElse(null),
                    aimedDepartureTime.orElse(null), tripUpdate.hasDelay() ? tripUpdate.getDelay() : null);
            estimatedCalls.getEstimatedCalls().add(estimatedCall);
        }

        journey.setEstimatedCalls(estimatedCalls);
        return journey;
    }

    /**
     * Maps a GTFS-Realtime {@link GtfsRealtime.TripUpdate} into a list of {@link MonitoredStopVisitCancellation} instances.
     * This method processes trip updates that indicate trip cancellations and structures the affected stop visits.
     *
     * @param tripUpdate  The GTFS-Realtime {@link GtfsRealtime.TripUpdate} containing trip update data.
     * @param datasetId   The identifier of the dataset associated with the trip update.
     * @param routeIdList A list of route IDs used to filter relevant stop visit cancellations.
     * @return A list of {@link MonitoredStopVisitCancellation} objects representing structured stop visit cancellation data.
     * If the trip is not canceled, returns an empty list.
     */
    public List<MonitoredStopVisitCancellation> mapStopCancellationFromTripUpdate(GtfsRealtime.TripUpdate tripUpdate, String datasetId, List<String> routeIdList) {
        if (tripUpdate.getTrip().getScheduleRelationship() != null && !GtfsRealtime.TripDescriptor.ScheduleRelationship.CANCELED.equals(
                tripUpdate.getTrip().getScheduleRelationship())) {
            return Collections.emptyList();
        }
        List<MonitoredStopVisitCancellation> stopVisitCancellations = new ArrayList<>();

        FramedVehicleJourneyRefStructure vehicleJourneyRef = createVehicleJourneyRef(tripUpdate);
        String tripId = tripUpdate.getTrip().getTripId();

        LineRef lineRef = createLineRef(tripUpdate, datasetId, tripId);

        for (GtfsRealtime.TripUpdate.StopTimeUpdate stopTimeUpdate : tripUpdate.getStopTimeUpdateList()) {
            MonitoredStopVisitCancellation monitoredStopVisitCancellation = new MonitoredStopVisitCancellation();

            String stopId = getStopId(stopTimeUpdate, datasetId, tripId);
            String routeIdInCache = stopTimesService.getRouteId(datasetId, tripId).orElse("");
            if (shouldFilterStop(routeIdList, stopId, routeIdInCache)) {
                continue;
            }
            if (StringUtils.isEmpty(stopId)) {
                logger.error("Unable to determine stopId for dataset:{}, tripId:{}, stopSequence:{}, stopId:{}", datasetId, tripId, stopTimeUpdate.getStopSequence(), stopTimeUpdate.getStopId());
            }
            MonitoringRefStructure monitoringRefStruct = new MonitoringRefStructure();
            monitoringRefStruct.setValue(stopId);
            monitoredStopVisitCancellation.setMonitoringRef(monitoringRefStruct);
            monitoredStopVisitCancellation.setVehicleJourneyRef(vehicleJourneyRef);
            monitoredStopVisitCancellation.setLineRef(lineRef);

            ItemRefStructure itemRefStructure = new ItemRefStructure();

            //This id has to permit to recognize the SM "datasetId-tripId-stopId-lineId"
            itemRefStructure.setValue(datasetId + "-" + tripId + "-" + stopId + "-" + lineRef.getValue());

            monitoredStopVisitCancellation.setItemRef(itemRefStructure);

            stopVisitCancellations.add(monitoredStopVisitCancellation);
        }

        return stopVisitCancellations;
    }
}
