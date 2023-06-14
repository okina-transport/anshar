package no.rutebanken.anshar.util;


import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import uk.org.siri.siri21.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;


public class SytralJSONMapper {


    private final static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static List<MonitoredStopVisit> convertToSiriSM(JSONArray values){
        List<MonitoredStopVisit> resultList = new ArrayList<>();



        for (int i = 0; i < values.size(); i++) {
            JSONObject value = (JSONObject) values.get(i);
            MonitoredStopVisit sm = convertToSiriSm(value);
            resultList.add(sm);
        }
            return resultList;
    }


    private static MonitoredStopVisit convertToSiriSm(JSONObject value) {
        MonitoredStopVisit sm = new MonitoredStopVisit();
        MonitoringRefStructure refStruct = new MonitoringRefStructure();
        String stopPointId = String.valueOf(value.get("idtarretdestination"));


        refStruct.setValue(stopPointId);
        sm.setMonitoringRef(refStruct);

        sm.setItemIdentifier(String.valueOf(value.get("gid")));

        MonitoredVehicleJourneyStructure monitoredVehicleStruct = new MonitoredVehicleJourneyStructure();
        LineRef lineRef = new LineRef();
        lineRef.setValue((String)value.get("ligne"));
        monitoredVehicleStruct.setLineRef(lineRef);


        String tripId = (String)value.get("coursetheorique");
        FramedVehicleJourneyRefStructure vehicleJourneyRef = new FramedVehicleJourneyRefStructure();
        vehicleJourneyRef.setDatedVehicleJourneyRef(tripId);

        DataFrameRefStructure dataFrameRef = new DataFrameRefStructure();
        dataFrameRef.setValue(tripId);
        vehicleJourneyRef.setDataFrameRef(dataFrameRef);

        monitoredVehicleStruct.setFramedVehicleJourneyRef(vehicleJourneyRef);
        monitoredVehicleStruct.setMonitored(true);


        MonitoredCallStructure monitoredCallStructure = new MonitoredCallStructure();
        StopPointRefStructure stopPointRef = new StopPointRefStructure();
        stopPointRef.setValue(stopPointId);
        monitoredCallStructure.setStopPointRef(stopPointRef);


        ZonedDateTime aimedArrival = convertToZonedDateTime((String)value.get("heurepassage"));
        monitoredCallStructure.setAimedArrivalTime(aimedArrival);

        monitoredVehicleStruct.setMonitoredCall(monitoredCallStructure);
        sm.setMonitoredVehicleJourney(monitoredVehicleStruct);
        sm.setRecordedAtTime(convertToZonedDateTime((String)value.get("last_update_fme")));


        return sm;
    }

    private static ZonedDateTime convertToZonedDateTime(String value){
        LocalDate date = LocalDate.parse(value, formatter);
        return  date.atStartOfDay(ZoneId.systemDefault());
    }




}
