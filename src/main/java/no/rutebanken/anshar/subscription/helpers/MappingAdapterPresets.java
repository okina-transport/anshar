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

import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.processor.CodespaceOutboundProcessor;
import no.rutebanken.anshar.routes.siri.processor.RemoveEmojiPostProcessor;
import no.rutebanken.anshar.routes.siri.processor.RuterOutboundDatedVehicleRefAdapter;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.ifopt.siri20.StopPlaceRef;
import uk.org.siri.siri20.*;

import java.util.*;


public class MappingAdapterPresets {

    public static List<ValueAdapter> getOutboundAdapters(SiriDataType dataType, OutboundIdMappingPolicy outboundIdMappingPolicy) {
        return getOutboundAdapters(dataType, outboundIdMappingPolicy, new HashMap<>());
    }

    public static List<ValueAdapter> getOutboundAdapters(SiriDataType dataType, OutboundIdMappingPolicy outboundIdMappingPolicy, Map<ObjectType, Optional<IdProcessingParameters>> idProcessingMap) {


        OutboundIdAdapter stopIdAdapter = new OutboundIdAdapter(StopPointRef.class, outboundIdMappingPolicy, true);
        OutboundIdAdapter monitoringRefAdapter = new OutboundIdAdapter(MonitoringRefStructure.class, outboundIdMappingPolicy, true);
        OutboundIdAdapter destinationRefAdapter = new OutboundIdAdapter(DestinationRef.class, outboundIdMappingPolicy, true);
        OutboundIdAdapter originRefAdapter = new OutboundIdAdapter(JourneyPlaceRefStructure.class, outboundIdMappingPolicy, true);
        OutboundIdAdapter lineRefAdapter = new  OutboundIdAdapter(LineRef .class, outboundIdMappingPolicy);
        RuterOutboundDatedVehicleRefAdapter datedVjRefAdapter = new  RuterOutboundDatedVehicleRefAdapter(MappingAdapterPresets .class, outboundIdMappingPolicy);
        OutboundIdAdapter operatorRefAdapter = new  OutboundIdAdapter(OperatorRefStructure .class, outboundIdMappingPolicy);


        if (idProcessingMap.containsKey(ObjectType.STOP) && idProcessingMap.get(ObjectType.STOP).isPresent()) {
            IdProcessingParameters idProcessingParameters = idProcessingMap.get(ObjectType.STOP).get();
            stopIdAdapter.setIdProcessingParameters(idProcessingParameters);
            monitoringRefAdapter.setIdProcessingParameters(idProcessingParameters);
            destinationRefAdapter.setIdProcessingParameters(idProcessingParameters);
            originRefAdapter.setIdProcessingParameters(idProcessingParameters);
        }

        if (idProcessingMap.containsKey(ObjectType.LINE) && idProcessingMap.get(ObjectType.LINE).isPresent()) {
            IdProcessingParameters lineIdProcessingParameters = idProcessingMap.get(ObjectType.LINE).get();
            lineRefAdapter.setIdProcessingParameters(lineIdProcessingParameters);
        }

        if (idProcessingMap.containsKey(ObjectType.VEHICLE_JOURNEY) && idProcessingMap.get(ObjectType.VEHICLE_JOURNEY).isPresent()) {
            IdProcessingParameters vehicleIdProcessingParameters = idProcessingMap.get(ObjectType.VEHICLE_JOURNEY).get();
            datedVjRefAdapter.setIdProcessingParameters(vehicleIdProcessingParameters);
        }

        if (idProcessingMap.containsKey(ObjectType.OPERATOR) && idProcessingMap.get(ObjectType.OPERATOR).isPresent()) {
            IdProcessingParameters operatorIdParams = idProcessingMap.get(ObjectType.OPERATOR).get();
            operatorRefAdapter.setIdProcessingParameters(operatorIdParams);
        }


    List<ValueAdapter> adapters = new ArrayList<>();
        adapters.add(stopIdAdapter);
        adapters.add(monitoringRefAdapter);
        adapters.add(destinationRefAdapter);
        adapters.add(originRefAdapter);
        adapters.add(lineRefAdapter);
        adapters.add(datedVjRefAdapter);
        adapters.add(operatorRefAdapter);
        adapters.add(new  CodespaceOutboundProcessor(outboundIdMappingPolicy));


        switch(dataType)

    {
        case ESTIMATED_TIMETABLE:
            adapters.add(new OutboundIdAdapter(JourneyPlaceRefStructure.class, outboundIdMappingPolicy));
            adapters.add(new OutboundIdAdapter(DestinationRef.class, outboundIdMappingPolicy));
            break;
        case VEHICLE_MONITORING:
          //  adapters.add(new OutboundIdAdapter(JourneyPlaceRefStructure.class, outboundIdMappingPolicy));
            //adapters.add(new OutboundIdAdapter(DestinationRef.class, outboundIdMappingPolicy));
            adapters.add(new OutboundIdAdapter(CourseOfJourneyRefStructure.class, outboundIdMappingPolicy));
       //     adapters.add(new RuterOutboundDatedVehicleRefAdapter(MappingAdapterPresets.class, outboundIdMappingPolicy));
            break;
        case SITUATION_EXCHANGE:
            adapters.add(new OutboundIdAdapter(RequestorRef.class, outboundIdMappingPolicy));
            adapters.add(new OutboundIdAdapter(StopPlaceRef.class, outboundIdMappingPolicy));
            adapters.add(new RemoveEmojiPostProcessor(outboundIdMappingPolicy));
            break;
        case STOP_MONITORING:
            // TODO MHI
            break;
        default:
            return getOutboundAdapters(outboundIdMappingPolicy);
    }
        return adapters;
}

    public static List<ValueAdapter> getOutboundAdapters(OutboundIdMappingPolicy outboundIdMappingPolicy) {
        List<ValueAdapter> adapters = new ArrayList<>();
        adapters.add(new OutboundIdAdapter(LineRef.class, outboundIdMappingPolicy));
        adapters.add(new OutboundIdAdapter(StopPointRef.class, outboundIdMappingPolicy));
        adapters.add(new OutboundIdAdapter(StopPlaceRef.class, outboundIdMappingPolicy));
        adapters.add(new OutboundIdAdapter(JourneyPlaceRefStructure.class, outboundIdMappingPolicy));
        adapters.add(new OutboundIdAdapter(DestinationRef.class, outboundIdMappingPolicy));
        adapters.add(new OutboundIdAdapter(CourseOfJourneyRefStructure.class, outboundIdMappingPolicy));

        //Adapter for SIRI-SX ParticipantRef
        adapters.add(new OutboundIdAdapter(RequestorRef.class, outboundIdMappingPolicy));

        //Adding postprocessor for Ruter DatedVehicleRef
        adapters.add(new RuterOutboundDatedVehicleRefAdapter(MappingAdapterPresets.class, outboundIdMappingPolicy));

        // Adding postprocessor for removing emojis etc. from SX-messages
        adapters.add(new RemoveEmojiPostProcessor(outboundIdMappingPolicy));

        // Postprocessor to set "correct" datasource/codespaceId
        adapters.add(new CodespaceOutboundProcessor(outboundIdMappingPolicy));
        return adapters;
    }
}
