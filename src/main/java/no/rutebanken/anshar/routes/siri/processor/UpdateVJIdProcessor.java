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

package no.rutebanken.anshar.routes.siri.processor;

import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.util.CustomStringUtils;
import no.rutebanken.anshar.routes.mapping.StopTimesService;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import org.apache.commons.collections4.CollectionUtils;
import uk.org.siri.siri21.*;

import java.time.ZonedDateTime;
import java.util.Optional;


@Slf4j
public class UpdateVJIdProcessor extends ValueAdapter implements PostProcessor {

    private final String datasetId;
    private final StopTimesService stopTimesService;
    private final SubscriptionConfig subscriptionConfig;


    public UpdateVJIdProcessor(String datasetId) {
        this.datasetId = datasetId;
        stopTimesService = ApplicationContextHolder.getContext().getBean(StopTimesService.class);
        subscriptionConfig = ApplicationContextHolder.getContext().getBean(SubscriptionConfig.class);
    }

    public UpdateVJIdProcessor(String datasetId, StopTimesService stopTimesService) {
        this.datasetId = datasetId;
        this.stopTimesService = stopTimesService;
        subscriptionConfig = ApplicationContextHolder.getContext().getBean(SubscriptionConfig.class);
    }


    @Override
    protected String apply(String text) {
        return null;
    }

    @Override
    public void process(Siri siri) {

        if (siri == null || siri.getServiceDelivery() == null || siri.getServiceDelivery().getStopMonitoringDeliveries() == null || siri.getServiceDelivery().getStopMonitoringDeliveries().isEmpty()) {
            return;
        }

        for (StopMonitoringDeliveryStructure stopMonitoringDelivery : siri.getServiceDelivery().getStopMonitoringDeliveries()) {

            if (stopMonitoringDelivery.getMonitoredStopVisits().isEmpty()) {
                continue;
            }

            for (MonitoredStopVisit monitoredStopVisit : stopMonitoringDelivery.getMonitoredStopVisits()) {
                if (!monitoredStopVisit.getMonitoredVehicleJourney().isMonitored()) {
                    // only real time data is processed
                    continue;
                }


                MonitoringRefStructure monitoringRef = monitoredStopVisit.getMonitoringRef();
                MonitoredVehicleJourneyStructure vehicleJourney = monitoredStopVisit.getMonitoredVehicleJourney();
                LineRef lineRef = vehicleJourney.getLineRef();
                String destinationRef = null;
                if (vehicleJourney.getDestinationRef() != null) {
                    destinationRef = monitoredStopVisit.getMonitoredVehicleJourney().getDestinationRef().getValue();
                }

                ZonedDateTime aimedArrivalTime = vehicleJourney.getMonitoredCall().getAimedArrivalTime();
                String directionId = CollectionUtils.isNotEmpty(vehicleJourney.getDirectionNames()) ? vehicleJourney.getDirectionNames().getFirst().getValue() : "";


                Optional<IdProcessingParameters> stopIdProcessingParameter = subscriptionConfig.getIdParametersForDataset(datasetId, ObjectType.STOP);
                String stop = monitoringRef.getValue();

                if (stopIdProcessingParameter.isPresent()) {
                    stop = stopIdProcessingParameter.get().removeInputPrefixAndSuffix(stop);
                    destinationRef = stopIdProcessingParameter.get().removeInputPrefixAndSuffix(destinationRef);
                }

                String lineId = lineRef.getValue();
                Optional<IdProcessingParameters> lineIdProcessingParameter = subscriptionConfig.getIdParametersForDataset(datasetId, ObjectType.LINE);
                if (lineIdProcessingParameter.isPresent()) {
                    lineId = lineIdProcessingParameter.get().removeInputPrefixAndSuffix(lineId);
                }


                Optional<String> vehicleJourneyIdFromTH = stopTimesService.findTripIdByStopAndTime(datasetId, "1-" + lineId, stop, directionId, destinationRef, aimedArrivalTime);
                if (vehicleJourneyIdFromTH.isPresent()) {
                    getMetricsService().registerRecomputeVehicleJourneyIdFromTheoretical(true);
                    FramedVehicleJourneyRefStructure framedVehicleJourneyRef = new FramedVehicleJourneyRefStructure();
                    framedVehicleJourneyRef.setDatedVehicleJourneyRef(CustomStringUtils.applyChouetteIdTransformation(vehicleJourneyIdFromTH.get()));
                    if (vehicleJourney.getFramedVehicleJourneyRef() != null && vehicleJourney.getFramedVehicleJourneyRef().getDataFrameRef() != null) {
                        framedVehicleJourneyRef.setDataFrameRef(vehicleJourney.getFramedVehicleJourneyRef().getDataFrameRef());
                    }
                    vehicleJourney.setFramedVehicleJourneyRef(framedVehicleJourneyRef);

                } else {
                    getMetricsService().registerRecomputeVehicleJourneyIdFromTheoretical(false);
                    log.debug("Unable to find vehicleJouney id from theoretical data : datasetId:{} - line:{} - monitoringRef:{} - directionId:{} - destinationRef:{} - aimedArrivalTime:{}", datasetId, "1-" + lineId, stop, directionId, destinationRef, aimedArrivalTime);
                }
            }
        }
    }

}
