package no.rutebanken.anshar.util;


import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import uk.org.siri.siri20.*;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

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
        StopPointRef stopPointRef = new StopPointRef();
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
