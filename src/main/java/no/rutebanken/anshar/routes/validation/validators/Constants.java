/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package no.rutebanken.anshar.routes.validation.validators;

public class Constants {

    /*
     * Defines xpaths used to resolve the correct elements when validating XML
     */

    public static final String SERVICE_DELIVERY = "Siri/ServiceDelivery";

    public static final String PT_SITUATION_ELEMENT = SERVICE_DELIVERY + "/SituationExchangeDelivery/Situations/PtSituationElement";
    private static final String AFFECTS = PT_SITUATION_ELEMENT + "/Affects";

    public static final String AFFECTED_NETWORK = AFFECTS + "/Networks/AffectedNetwork";
    public static final String AFFECTED_LINE = AFFECTED_NETWORK + "/AffectedLine";
    public static final String AFFECTED_ROUTE = AFFECTED_LINE + "/Routes/AffectedRoute";

    public static final String AFFECTED_STOP_POINT = AFFECTS + "/StopPoints/AffectedStopPoint";
    public static final String AFFECTED_STOP_PLACE = AFFECTS + "/StopPlaces/AffectedStopPlace";
    public static final String ACCESSIBILITY_ASSESSMENT = AFFECTED_STOP_PLACE + "/StopPlaces";
    public static final String AFFECTED_COMPONENTS = AFFECTED_STOP_PLACE + "/AffectedComponents";
    public static final String AFFECTED_VEHICLE_JOURNEY = AFFECTS + "/VehicleJourneys/AffectedVehicleJourney";


    public static final String ESTIMATED_VEHICLE_JOURNEY = SERVICE_DELIVERY + "/EstimatedTimetableDelivery/EstimatedJourneyVersionFrame/EstimatedVehicleJourney";
    public static final String ESTIMATED_CALLS = ESTIMATED_VEHICLE_JOURNEY + "/EstimatedCalls";
    public static final String ESTIMATED_CALL = ESTIMATED_VEHICLE_JOURNEY + "/EstimatedCalls/EstimatedCall";
    public static final String RECORDED_CALL = ESTIMATED_VEHICLE_JOURNEY + "/RecordedCalls/RecordedCall";


    public static final String VEHICLE_ACTIVITY = SERVICE_DELIVERY + "/VehicleMonitoringDelivery/VehicleActivity";
    public static final String MONITORED_VEHICLE_JOURNEY = VEHICLE_ACTIVITY + "/MonitoredVehicleJourney";
    public static final String MONITORED_CALL_STRUCTURE = MONITORED_VEHICLE_JOURNEY + "/MonitoredCall";

    public static final String DATASET_ID_HEADER_NAME = "datasetId";

    public static final String HEARTBEAT_HEADER = "HeartBeat";
    public static final String URL_HEADER_NAME = "URL";

    public final static String GTFSRT_ET_PREFIX = "GTFS-RT_ET_";
    public final static String GTFSRT_SM_PREFIX = "GTFS-RT_SM_";
    public final static String GTFSRT_VM_PREFIX = "GTFS-RT_VM_";
    public final static String GTFSRT_SX_PREFIX = "GTFS-RT_SX_";

    public final static String GTFSRT_ET_QUEUE = "gtfsrt.estimated.timetable";
    public final static String GTFSRT_SM_QUEUE = "gtfsrt.stop.monitoring";
    public final static String GTFSRT_SX_QUEUE = "gtfsrt.situation.exchange";
    public final static String GTFSRT_VM_QUEUE = "gtfsrt.vehicle.monitoring";

    public final static String SIRI_LITE_SERVICE_NAME = "SiriLiteServiceName";

    public final static String SIRI_ET_KAFKA_QUEUE = "siri.estimated.timetable.kafka";
    public final static String SIRI_SM_KAFKA_QUEUE = "siri.stop.monitoring.kafka";
    public final static String SIRI_SX_KAFKA_QUEUE = "siri.situation.exchange.kafka";
    public final static String SIRI_VM_KAFKA_QUEUE = "siri.vehicle.monitoring.kafka";
    public final static String SIRI_GM_KAFKA_QUEUE = "siri.general.message.kafka";
    public final static String SIRI_FM_KAFKA_QUEUE = "siri.facility.monitoring.kafka";


    // TODO MHI : implement validators for SM

    private Constants() {
        // should not be instantiated
    }
}
