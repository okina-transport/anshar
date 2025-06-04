package no.rutebanken.anshar.consistency;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.config.IncomingSiriParameters;
import no.rutebanken.anshar.consistency.exception.DatasetNotFoundException;
import no.rutebanken.anshar.consistency.model.ConsistencyReport;
import no.rutebanken.anshar.data.frGeneralMessageStructure.Content;
import no.rutebanken.anshar.routes.mapping.LineUpdaterService;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.mapping.VehicleJourneyService;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.subscription.DatasetService;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.w3c.dom.Element;
import uk.org.ifopt.siri21.StopPlaceRef;
import uk.org.siri.siri21.*;

import java.lang.reflect.InvocationTargetException;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ConsistencyService {

    public static final String REQUESTOR_REF = "ANSHAR - ConsistencyService";
    public static final String CLIENT_TRACKING_NAME = "ANSHAR - ConsistencyService";
    private final SiriHandler siriHandler;
    private final LineUpdaterService lineUpdaterService;
    private final StopPlaceUpdaterService stopPlaceUpdaterService;
    private final VehicleJourneyService vehicleJourneyService;
    private final DatasetService datasetService;

    public ConsistencyService(SiriHandler siriHandler, LineUpdaterService lineUpdaterService, StopPlaceUpdaterService stopPlaceUpdaterService, VehicleJourneyService vehicleJourneyService, DatasetService datasetService) {
        this.siriHandler = siriHandler;
        this.lineUpdaterService = lineUpdaterService;
        this.stopPlaceUpdaterService = stopPlaceUpdaterService;
        this.vehicleJourneyService = vehicleJourneyService;
        this.datasetService = datasetService;
    }

    /**
     * @param datasetId dataset id to build consistency report for
     * @return TH/TR consistency report for a dataset id
     * @throws InvocationTargetException when retrieving ids from Siri object fails
     * @throws IllegalAccessException    when retrieving ids from Siri object fails
     * @throws DatasetNotFoundException  when dataset does not belong to ANSHAR
     */
    public ConsistencyReport buildReportForDataset(String datasetId) throws InvocationTargetException, IllegalAccessException, DatasetNotFoundException {
        if (!datasetService.exists(datasetId)) {
            throw new DatasetNotFoundException(datasetId);
        }
        ConsistencyReport report = new ConsistencyReport();
        report.setStart(ZonedDateTime.now());
        report.setDataset(datasetId);
        Map<SiriDataType, ConsistencyReport.Consistency> consistencies = new EnumMap<>(SiriDataType.class);
        for (var type : SiriDataType.values()) {
            ConsistencyReport.Consistency consistencyByTypeAndDatasetId = getConsistencyByTypeAndDatasetId(type, datasetId);
            if (consistencyByTypeAndDatasetId != null) {
                consistencies.put(type, consistencyByTypeAndDatasetId);
            }
        }
        report.setConsistencies(consistencies);
        report.setEnd(ZonedDateTime.now());
        return report;
    }

    /**
     * @param type      type of siri message to get from cache
     * @param datasetId dataset id to get all siri message from cache
     * @return siri message from cache by type and dataset id
     */
    private Siri getCachedDataWithOriginalIdByTypeAndDatasetId(SiriDataType type, String datasetId) {
        IncomingSiriParameters params = new IncomingSiriParameters();
        params.setDatasetId(datasetId);
        params.setMaxSize(-1);
        params.setClientTrackingName(CLIENT_TRACKING_NAME);
        params.setSoapTransformation(false);
        params.setUseOriginalId(false);
        params.setExcludedDatasetIdList(List.of());
        params.setOutboundIdMappingPolicy(OutboundIdMappingPolicy.DEFAULT);
        params.setVersion("2.1");
        Siri request = SiriObjectFactory.createServiceRequest(type, "2.1", REQUESTOR_REF,
                null, null);
        return siriHandler.buildSiriResponse(params, request);
    }

    /**
     * @param type      type of siri message to get consistency from
     * @param datasetId dataset id to get consistency from
     * @return {@link no.rutebanken.anshar.consistency.model.ConsistencyReport.Consistency} or null if no id is found
     * in SIRI from cache
     * @throws InvocationTargetException when retrieving ids from Siri object fails
     * @throws IllegalAccessException    when retrieving ids from Siri object fails
     */
    private ConsistencyReport.Consistency getConsistencyByTypeAndDatasetId(SiriDataType type, String datasetId) throws InvocationTargetException, IllegalAccessException {
        log.info("Get all siri {} (dataset: {})", type, datasetId);
        Siri cachedData = getCachedDataWithOriginalIdByTypeAndDatasetId(type, datasetId);

        IdsByEntity ids = getIdsByEntityFromSiri(cachedData);
        log.info("Found: {} lineIds, {} stopIds, {} vehicleJourneyIds from siri {} (dataset: {})",
                ids.getLineIds().size(), ids.getStopIds().size(), ids.getVehicleJourneyIds().size(), type, datasetId);

        if (CollectionUtils.isEmpty(ids.getLineIds()) && CollectionUtils.isEmpty(ids.getStopIds()) && CollectionUtils.isEmpty(ids.getVehicleJourneyIds())) {
            return null;
        }

        ConsistencyReport.Consistency consistency = new ConsistencyReport.Consistency();

        if (CollectionUtils.isNotEmpty(ids.getLineIds())) {
            ConsistencyReport.MatchResult lines = new ConsistencyReport.MatchResult();
            List<String> unmatchedIds = ids.getLineIds().stream().filter(id -> !lineUpdaterService.exists(id)).collect(Collectors.toList());
            lines.setUnmatchedIds(unmatchedIds);
            lines.setNbMatch(ids.getLineIds().size() - unmatchedIds.size());
            consistency.setLines(lines);
            log.info("lines: {} match, {} unmatched (dataset: {})", consistency.getLines().getNbMatch(),
                    consistency.getLines().getUnmatchedIds().size(), datasetId);
        }

        if (!ids.getStopIds().isEmpty()) {
            ConsistencyReport.MatchResult stops = new ConsistencyReport.MatchResult();
            List<String> unmatchedIds = ids.getStopIds().stream().filter(id -> !stopPlaceUpdaterService.exists
                    (id)).collect(Collectors.toList());
            stops.setUnmatchedIds(unmatchedIds);
            stops.setNbMatch(ids.getStopIds().size() - unmatchedIds.size());
            consistency.setStops(stops);
            log.info("stops: {} match, {} unmatched (dataset: {})", consistency.getStops().getNbMatch(),
                    consistency.getStops().getUnmatchedIds().size(), datasetId);
        }

        if (!ids.getVehicleJourneyIds().isEmpty()) {
            ConsistencyReport.MatchResult vehicleJourneys = new ConsistencyReport.MatchResult();
            List<String> unmatchedIds = ids.getVehicleJourneyIds().stream().filter(id -> !vehicleJourneyService.exists(id)).collect(Collectors.toList());
            vehicleJourneys.setUnmatchedIds(unmatchedIds);
            vehicleJourneys.setNbMatch(ids.getVehicleJourneyIds().size() - unmatchedIds.size());
            consistency.setVehicleJourneys(vehicleJourneys);
            log.info("vehicleJourneys: {} match, {} unmatched (dataset: {})", consistency.getVehicleJourneys().getNbMatch(),
                    consistency.getVehicleJourneys().getUnmatchedIds().size(), datasetId);
        }

        return consistency;
    }

    /**
     * @param siri siri message to get all line, stop and vehicle journey ids from
     * @return all line, stop and vehicle journey ids (no duplicates)
     * @throws InvocationTargetException when retrieving ids from Siri object fails
     * @throws IllegalAccessException    when retrieving ids from Siri object fails
     */
    private IdsByEntity getIdsByEntityFromSiri(Siri siri) throws InvocationTargetException, IllegalAccessException {
        IdsByEntity ids = new IdsByEntity();
        getIdsByEntityFromSiriRecursive(siri, ids);
        return ids;
    }

    /**
     * @param o   an object from Siri message (= any attribute from Siri message and its children recursively)
     * @param ids stores ids by entity
     * @throws InvocationTargetException when retrieving ids from Siri object fails
     * @throws IllegalAccessException    when retrieving ids from Siri object fails
     */
    private void getIdsByEntityFromSiriRecursive(Object o, IdsByEntity ids) throws InvocationTargetException, IllegalAccessException {
        if (o == null) {
            return;
        }
        if (o instanceof List && CollectionUtils.isNotEmpty((List) o)) {
            for (var oo : (List) o) {
                getIdsByEntityFromSiriRecursive(oo, ids);
            }
        }
        if (o instanceof Element) {
            // GeneralMessage.Content unmarshalling does not work ATM, REMOVE this when this is fixed
            Element e = (Element) o;
            if (e.getTagName().equals("Content")) {
                var lineRefs = e.getElementsByTagName("LineRef");
                for (int i = 0; i < lineRefs.getLength(); i++) {
                    ids.getLineIds().add(lineRefs.item(i).getTextContent());
                }
                var stopRefs = e.getElementsByTagName("StopPointRef");
                for (int i = 0; i < stopRefs.getLength(); i++) {
                    ids.getStopIds().add(stopRefs.item(i).getTextContent());
                }
            }
        }
        if (o instanceof Content) {
            // GeneralMessage.Content unmarshalling does not work ATM, KEEP this when this is fixed
            Content c = (Content) o;
            if (CollectionUtils.isNotEmpty(c.getLineRefs())) ids.getLineIds().addAll(c.getLineRefs());
            if (CollectionUtils.isNotEmpty(c.getStopPointRefs())) ids.getStopIds().addAll(c.getStopPointRefs());
        }
        if (!o.getClass().getName().startsWith("uk.org.siri")) {
            return;
        }
        if (o instanceof LineRef) {
            ids.getLineIds().add(((LineRef) o).getValue());
        } else if (o instanceof StopPointRefStructure) {
            ids.getStopIds().add(((StopPointRefStructure) o).getValue());
        } else if (o instanceof StopPlaceRef) {
            ids.getStopIds().add(((StopPlaceRef) o).getValue());
        } else if (o instanceof MonitoringRefStructure) {
            ids.getStopIds().add(((MonitoringRefStructure) o).getValue());
        } else if (o instanceof DestinationRef) {
            ids.getStopIds().add(((DestinationRef) o).getValue());
        } else if (o instanceof JourneyPlaceRefStructure) {
            ids.getStopIds().add(((JourneyPlaceRefStructure) o).getValue());
        } else if (o instanceof DatedVehicleJourneyRef) {
            ids.getVehicleJourneyIds().add(((DatedVehicleJourneyRef) o).getValue());
        } else if (o instanceof FramedVehicleJourneyRefStructure) {
            ids.getVehicleJourneyIds().add(((FramedVehicleJourneyRefStructure) o).getDatedVehicleJourneyRef());
        } else if (o instanceof VehicleJourneyRef) {
            ids.getVehicleJourneyIds().add(((VehicleJourneyRef) o).getValue());
        }
        for (var method : o.getClass().getMethods()) {
            if (method.getParameterCount() == 0
                    && !("void".equals(method.getReturnType().getName()))) {
                getIdsByEntityFromSiriRecursive(method.invoke(o), ids);
            }
        }
    }

    @Getter
    public static class IdsByEntity {
        private final Set<String> lineIds = new HashSet<>();
        private final Set<String> vehicleJourneyIds = new HashSet<>();
        private final Set<String> stopIds = new HashSet<>();
    }

}
