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

import no.rutebanken.anshar.routes.mapping.LineUpdaterService;
import no.rutebanken.anshar.routes.mapping.VehicleJourney.VehicleJourney;
import no.rutebanken.anshar.routes.mapping.VehicleJourney.VehicleJourneyCache;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.util.MappingUtils;
import no.rutebanken.anshar.util.StopMonitoringUtils;
import org.apache.commons.collections4.CollectionUtils;
import uk.org.siri.siri21.*;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;


@Slf4j
public class UpdateVJIdProcessor extends ValueAdapter implements PostProcessor {

    private static final DateTimeFormatter DF_YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DF_HHMMSS = DateTimeFormatter.ofPattern("HHmmss");

    private final String datasetId;


    private transient VehicleJourneyCache vjCache;

    private transient SubscriptionConfig subscriptionConfig;

    private transient LineUpdaterService lineUpdaterService;


    public UpdateVJIdProcessor(String datasetId) {
        this.datasetId = datasetId;
        vjCache = ApplicationContextHolder.getContext().getBean(VehicleJourneyCache.class);
        subscriptionConfig = ApplicationContextHolder.getContext().getBean(SubscriptionConfig.class);
        lineUpdaterService = ApplicationContextHolder.getContext().getBean(LineUpdaterService.class);
    }

    public UpdateVJIdProcessor(String datasetId, VehicleJourneyCache vjCache, LineUpdaterService lineUpdaterService) {
        this.datasetId = datasetId;
        this.vjCache = vjCache;
        subscriptionConfig = ApplicationContextHolder.getContext().getBean(SubscriptionConfig.class);
        this.lineUpdaterService = lineUpdaterService;
    }


    @Override
    protected String apply(String text) {
        return null;
    }

    @Override
    public void process(Siri siri) {

        try {
            if (siri == null || siri.getServiceDelivery() == null || siri.getServiceDelivery().getStopMonitoringDeliveries() == null || siri.getServiceDelivery().getStopMonitoringDeliveries().isEmpty()) {
                return;
            }

            if (vjCache == null) {
                vjCache = ApplicationContextHolder.getContext().getBean(VehicleJourneyCache.class);
            }

            if (subscriptionConfig == null) {
                subscriptionConfig = ApplicationContextHolder.getContext().getBean(SubscriptionConfig.class);
            }

            if (lineUpdaterService == null) {
                lineUpdaterService = ApplicationContextHolder.getContext().getBean(LineUpdaterService.class);
            }

            for (StopMonitoringDeliveryStructure stopMonitoringDelivery : siri.getServiceDelivery().getStopMonitoringDeliveries()) {

                if (stopMonitoringDelivery.getMonitoredStopVisits().isEmpty()) {
                    continue;
                }

                for (MonitoredStopVisit monitoredStopVisit : stopMonitoringDelivery.getMonitoredStopVisits()) {

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
                    String lineNumber = "";
                    if (lineIdProcessingParameter.isPresent()) {
                        lineId = lineIdProcessingParameter.get().applyTransformationToString(lineId);
                        lineNumber = lineUpdaterService.getLineNumber(lineId).orElse(null);
                    }

                    String cacheKey = MappingUtils.buildIneoVJKey(
                            LocalDate.now().format(DF_YYYYMMDD),
                            aimedArrivalTime.format(DF_HHMMSS),
                            lineNumber,
                            directionId,
                            stop,
                            datasetId);

                    Optional<VehicleJourney> vehicleJourneyIdFromTH = vjCache.findVehicleJourney(cacheKey);
                    if (vehicleJourneyIdFromTH.isPresent()) {
                        getMetricsService().registerRecomputeVehicleJourneyIdFromTheoretical(true);
                        FramedVehicleJourneyRefStructure framedVehicleJourneyRef = new FramedVehicleJourneyRefStructure();
                        framedVehicleJourneyRef.setDatedVehicleJourneyRef(CustomStringUtils.applyChouetteIdTransformation(vehicleJourneyIdFromTH.get().getVehicleJourneyId()));
                        if (vehicleJourney.getFramedVehicleJourneyRef() != null && vehicleJourney.getFramedVehicleJourneyRef().getDataFrameRef() != null) {
                            framedVehicleJourneyRef.setDataFrameRef(vehicleJourney.getFramedVehicleJourneyRef().getDataFrameRef());
                        }
                        vehicleJourney.setFramedVehicleJourneyRef(framedVehicleJourneyRef);

                    } else {
                        getMetricsService().registerRecomputeVehicleJourneyIdFromTheoretical(false);
                        log.debug("Unable to find vehicleJouney id from theoretical data : datasetId:{} - lineId:{} - lineNumber:{} - monitoringRef:{} - directionId:{} - destinationRef:{} - aimedArrivalTime:{}", datasetId, lineId, lineNumber, stop, directionId, destinationRef, aimedArrivalTime);
                    }


                    monitoredStopVisit.setItemIdentifier(
                            monitoredStopVisit.getMonitoringRef().getValue() + "_" +
                                    StopMonitoringUtils.getVehicleJourneyName(monitoredStopVisit).orElse(null) + "_" +
                                    getArrivalDateOrTime(monitoredStopVisit, DF_YYYYMMDD).orElse(null) + "_" +
                                    getArrivalDateOrTime(monitoredStopVisit, DF_HHMMSS).orElse(null)
                    );
                }
            }
        } catch (Exception e) {
            log.debug("Error while applying UpdateVJIdProcessor, dataset:{}", datasetId, e);
        }

    }

    private Optional<String> getArrivalDateOrTime(MonitoredStopVisit monitoredStopVisit, DateTimeFormatter formatter) {
        if (monitoredStopVisit.getMonitoredVehicleJourney() == null || monitoredStopVisit.getMonitoredVehicleJourney().getMonitoredCall() == null ||
                monitoredStopVisit.getMonitoredVehicleJourney().getMonitoredCall().getAimedArrivalTime() == null) {
            return Optional.empty();
        }
        return Optional.of(monitoredStopVisit.getMonitoredVehicleJourney().getMonitoredCall().getAimedArrivalTime().format(formatter));
    }

}
