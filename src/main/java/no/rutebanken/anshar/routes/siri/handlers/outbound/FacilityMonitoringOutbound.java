package no.rutebanken.anshar.routes.siri.handlers.outbound;

import jdk.jshell.execution.Util;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.handlers.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.FacilityMonitoringRequestStructure;
import uk.org.siri.siri20.FacilityRef;
import uk.org.siri.siri20.LineRef;
import uk.org.siri.siri20.ServiceRequest;
import uk.org.siri.siri20.StopPointRef;
import uk.org.siri.siri20.VehicleRef;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FacilityMonitoringOutbound {

    @Autowired
    private Utils utils;

    public Set<String> getFacilityRevesList(ServiceRequest serviceRequest) {
        Map<Class, Set<String>> filterMap = new HashMap<>();
        for (FacilityMonitoringRequestStructure req : serviceRequest.getFacilityMonitoringRequests()) {
            List<FacilityRef> facilityReves = req.getFacilityReves();
            if (facilityReves != null && !facilityReves.isEmpty()) {
                Set<String> facilityRevesList = filterMap.get(FacilityRef.class) != null ? filterMap.get(FacilityRef.class) : new HashSet<>();
                facilityRevesList.addAll(facilityReves.stream().map(FacilityRef::getValue).collect(Collectors.toSet()));
                filterMap.put(FacilityRef.class, facilityRevesList);
            }

        }

        return filterMap.get(FacilityRef.class) != null ? filterMap.get(FacilityRef.class) : new HashSet<>();
    }
    public Set<String> getLineRefOriginalList(ServiceRequest serviceRequest, OutboundIdMappingPolicy outboundIdMappingPolicy, String datasetId) {
        Map<Class, Set<String>> filterMap = new HashMap<>();
        for (FacilityMonitoringRequestStructure req : serviceRequest.getFacilityMonitoringRequests()) {
            LineRef lineRef = req.getLineRef();
            if (lineRef != null) {
                Set<String> linerefList = filterMap.get(LineRef.class) != null ? filterMap.get(LineRef.class) : new HashSet<>();
                linerefList.add(lineRef.getValue());
                filterMap.put(LineRef.class, linerefList);
            }

        }
        Set<String> lineRefList = filterMap.get(LineRef.class) != null ? filterMap.get(LineRef.class) : new HashSet<>();
        Set<String> lineRefOriginalList;
        if (OutboundIdMappingPolicy.ALT_ID.equals(outboundIdMappingPolicy)) {
            lineRefOriginalList = utils.convertFromAltIdsToImportedIdsLine(lineRefList, datasetId);
        } else {
            lineRefOriginalList = lineRefList;
        }

        return lineRefOriginalList;
    }

    public Set<String> getStopPointRefList(ServiceRequest serviceRequest) {
        Map<Class, Set<String>> filterMap = new HashMap<>();
        for (FacilityMonitoringRequestStructure req : serviceRequest.getFacilityMonitoringRequests()) {
            StopPointRef stopPointRef = req.getStopPointRef();
            if (stopPointRef != null) {
                Set<String> stopPointRefList = filterMap.get(StopPointRef.class) != null ? filterMap.get(StopPointRef.class) : new HashSet<>();
                stopPointRefList.add(stopPointRef.getValue());
                filterMap.put(StopPointRef.class, stopPointRefList);
            }
        }

        return filterMap.get(StopPointRef.class) != null ? filterMap.get(StopPointRef.class) : new HashSet<>();

    }

    public Set<String> getVehicleRefList(ServiceRequest serviceRequest) {
        Map<Class, Set<String>> filterMap = new HashMap<>();
        for (FacilityMonitoringRequestStructure req : serviceRequest.getFacilityMonitoringRequests()) {
            VehicleRef vehicleRef = req.getVehicleRef();
            if (vehicleRef != null) {
                Set<String> vehicleRefList = filterMap.get(VehicleRef.class) != null ? filterMap.get(VehicleRef.class) : new HashSet<>();
                vehicleRefList.add(vehicleRef.getValue());
                filterMap.put(VehicleRef.class, vehicleRefList);
            }

        }

        return  filterMap.get(VehicleRef.class) != null ? filterMap.get(VehicleRef.class) : new HashSet<>();
    }

}
