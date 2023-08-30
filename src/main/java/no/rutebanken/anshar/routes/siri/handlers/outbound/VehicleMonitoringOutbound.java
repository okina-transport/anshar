package no.rutebanken.anshar.routes.siri.handlers.outbound;

import no.rutebanken.anshar.data.VehicleActivities;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.handlers.Utils;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.LineRef;
import uk.org.siri.siri20.ServiceRequest;
import uk.org.siri.siri20.VehicleMonitoringRequestStructure;
import uk.org.siri.siri20.VehicleRef;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
public class VehicleMonitoringOutbound {

    private static final Logger logger = LoggerFactory.getLogger(VehicleMonitoringOutbound.class);

    @Autowired
    SubscriptionManager subscriptionManager;

    @Autowired
    SubscriptionConfig subscriptionConfig;

    @Autowired
    VehicleActivities vehicleActivities;

    @Autowired
    Utils utils;


    public Set<String> getLineRefOriginalList(ServiceRequest serviceRequest, OutboundIdMappingPolicy outboundIdMappingPolicy, String datasetId) {
        Map<Class, Set<String>> filterMap = new HashMap<>();
        for (VehicleMonitoringRequestStructure req : serviceRequest.getVehicleMonitoringRequests()) {
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

    public Set<String> getVehicleRefList(ServiceRequest serviceRequest) {
        Map<Class, Set<String>> filterMap = new HashMap<>();
        for (VehicleMonitoringRequestStructure req : serviceRequest.getVehicleMonitoringRequests()) {
            VehicleRef vehicleRef = req.getVehicleRef();
            if (vehicleRef != null) {
                Set<String> vehicleRefList = filterMap.get(VehicleRef.class) != null ? filterMap.get(VehicleRef.class) : new HashSet<>();
                vehicleRefList.add(vehicleRef.getValue());
                filterMap.put(VehicleRef.class, vehicleRefList);
            }
        }
        return filterMap.get(VehicleRef.class) != null ? filterMap.get(VehicleRef.class) : new HashSet<>();
    }

}
