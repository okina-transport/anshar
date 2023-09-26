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

package no.rutebanken.anshar.subscription.helpers;

public enum RequestType {
    SUBSCRIBE,
    DELETE_SUBSCRIPTION,
    CHECK_STATUS,
    GET_VEHICLE_MONITORING,
    GET_SITUATION_EXCHANGE,
    GET_ESTIMATED_TIMETABLE,
    GET_STOP_MONITORING,
    GET_FACILITY_MONITORING,
    GET_GENERAL_MESSAGE
}
