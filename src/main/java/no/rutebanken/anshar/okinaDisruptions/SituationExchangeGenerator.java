package no.rutebanken.anshar.okinaDisruptions;

import no.rutebanken.anshar.okinaDisruptions.model.*;
import uk.org.siri.siri20.*;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class SituationExchangeGenerator {


    public static PtSituationElement createFromDisruption(Disruption disruption) {

        PtSituationElement ptSituationElement = new PtSituationElement();
        mapDescription(ptSituationElement, disruption);
        mapPeriod(ptSituationElement, disruption);
        mapReasons(ptSituationElement, disruption);
        mapAffects(ptSituationElement, disruption);
        mapImage(ptSituationElement, disruption);
        return ptSituationElement;
    }

    private static void mapImage(PtSituationElement ptSituationElement, Disruption disruption) {

        if (disruption.getImgFileBinary() == null) {
            return;
        }

        PtSituationElement.Images imageList = new PtSituationElement.Images();
        PtSituationElement.Images.Image disruptionImg = new PtSituationElement.Images.Image();
        disruptionImg.setImageBinary(disruption.getImgFileBinary());
        imageList.getImages().add(disruptionImg);
        ptSituationElement.setImages(imageList);


    }

    private static void mapAffects(PtSituationElement ptSituationElement, Disruption disruption) {

        AffectsScopeStructure affectStruct = new AffectsScopeStructure();

        mapVehicleJourneys(disruption, affectStruct);
        mapStopPoints(disruption, affectStruct);
        mapNetworkAndLines(disruption, affectStruct);

        ptSituationElement.setAffects(affectStruct);
    }

    private static void mapNetworkAndLines(Disruption disruption, AffectsScopeStructure affectStruct) {
        AffectsScopeStructure.Networks networks = new AffectsScopeStructure.Networks();
        Map<String, AffectsScopeStructure.Networks.AffectedNetwork> affectedNetworksMap = new HashMap<>();

        if (disruption.getNetworks().size() > 0) {
            for (OkinaNetwork network : disruption.getNetworks()) {

                AffectsScopeStructure.Networks.AffectedNetwork affectedNetwork = new AffectsScopeStructure.Networks.AffectedNetwork();
                NetworkRefStructure networkRef = new NetworkRefStructure();
                String networkId = network.getObjectId();
                networkRef.setValue(networkId);
                affectedNetwork.setNetworkRef(networkRef);
                affectedNetworksMap.put(networkId, affectedNetwork);
                networks.getAffectedNetworks().add(affectedNetwork);
            }
        }


        if (disruption.getLines().size() > 0) {
            for (OkinaLine line : disruption.getLines()) {

                String networkId = line.getNetwork().getObjectId();
                AffectsScopeStructure.Networks.AffectedNetwork currentNetwork;

                if (affectedNetworksMap.containsKey(networkId)) {
                    currentNetwork = affectedNetworksMap.get(networkId);
                } else {
                    currentNetwork = new AffectsScopeStructure.Networks.AffectedNetwork();
                    NetworkRefStructure networkRef = new NetworkRefStructure();
                    networkRef.setValue(networkId);
                    currentNetwork.setNetworkRef(networkRef);
                    affectedNetworksMap.put(networkId, currentNetwork);
                    networks.getAffectedNetworks().add(currentNetwork);
                }

                AffectedLineStructure affectedLine = new AffectedLineStructure();
                LineRef lineRef = new LineRef();
                lineRef.setValue(line.getObjectId());
                affectedLine.setLineRef(lineRef);
                currentNetwork.getAffectedLines().add(affectedLine);
            }
        }

        if (networks.getAffectedNetworks().size() > 0) {
            affectStruct.setNetworks(networks);
        }

    }

    private static void mapStopPoints(Disruption disruption, AffectsScopeStructure affectStruct) {

        if (disruption.getStopAreas().size() == 0)
            return;


        AffectsScopeStructure.StopPoints stopPoints = new AffectsScopeStructure.StopPoints();

        for (OkinaStopArea stopArea : disruption.getStopAreas()) {

            AffectedStopPointStructure stopPoint = new AffectedStopPointStructure();
            StopPointRef stopPointRef = new StopPointRef();
            stopPointRef.setValue(disruption.getOrganization().toUpperCase(Locale.ROOT) + ":Quay:" + stopArea.getObjectId());
            stopPoint.setStopPointRef(stopPointRef);
            stopPoints.getAffectedStopPoints().add(stopPoint);
        }
        affectStruct.setStopPoints(stopPoints);

    }

    private static void mapVehicleJourneys(Disruption disruption, AffectsScopeStructure affectStruct) {

        AffectsScopeStructure.VehicleJourneys vehicleJourneys = new AffectsScopeStructure.VehicleJourneys();

        if (disruption.getCourses().size() > 0) {
            for (OkinaVehicleJourney course : disruption.getCourses()) {
                mapVehicleJourney(course, vehicleJourneys);
            }
        }


        if (disruption.getJourneys().size() > 0) {
            for (JourneyPattern journey : disruption.getJourneys()) {
                for (OkinaVehicleJourney okinaVehicleJourney : journey.getVehicleJourneys()) {
                    mapVehicleJourney(okinaVehicleJourney, vehicleJourneys);
                }
            }
        }

        if (vehicleJourneys.getAffectedVehicleJourneies().size() > 0) {
            affectStruct.setVehicleJourneys(vehicleJourneys);
        }
    }


    private static void mapVehicleJourney(OkinaVehicleJourney course, AffectsScopeStructure.VehicleJourneys vehicleJourneys) {

        AffectedVehicleJourneyStructure vehicleJourney = new AffectedVehicleJourneyStructure();

        if (course.getOkinaRoute() != null) {
            LineRef lineRef = new LineRef();
            lineRef.setValue(course.getOkinaRoute().getOkinaLine().getObjectId());
            vehicleJourney.setLineRef(lineRef);
        }

        NaturalLanguageStringStructure publishedName = new NaturalLanguageStringStructure();
        publishedName.setValue(course.getPublishedJourneyName());
        vehicleJourney.setPublishedLineName(publishedName);
        FramedVehicleJourneyRefStructure vehicleJourneyRef = new FramedVehicleJourneyRefStructure();
        vehicleJourneyRef.setDatedVehicleJourneyRef(course.getObjectId());
        vehicleJourney.setFramedVehicleJourneyRef(vehicleJourneyRef);
        vehicleJourneys.getAffectedVehicleJourneies().add(vehicleJourney);

    }

    private static void mapReasons(PtSituationElement ptSituationElement, Disruption disruption) {

        if (disruption.getCategory() == null) {
            ptSituationElement.setMiscellaneousReason(MiscellaneousReasonEnumeration.UNKNOWN);
            return;
        }

        switch (disruption.getCategory()) {
            case "MiscellaneousReason":
                ptSituationElement.setMiscellaneousReason(MiscellaneousReasonEnumeration.valueOf(disruption.getReason()));
                break;
            case "EquipmentReason":
                ptSituationElement.setEquipmentReason(EquipmentReasonEnumeration.valueOf(disruption.getReason()));
                break;
            case "PersonnelReason":
                ptSituationElement.setPersonnelReason(PersonnelReasonEnumeration.valueOf(disruption.getReason()));
                break;
            case "EnvironmentReason":
                ptSituationElement.setEnvironmentReason(EnvironmentReasonEnumeration.valueOf(disruption.getReason()));
                break;
            default:
                //Ignore
        }


    }

    private static void mapPeriod(PtSituationElement ptSituationElement, Disruption disruption) {
        ZoneId zoneId = ZoneId.systemDefault();


        if (disruption.getPeriods() == null) {
            addValidityPeriodToSituation(ptSituationElement, disruption.getStartDateTime(), disruption.getEndDateTime());

        } else {
            for (DisruptionPeriod period : disruption.getPeriods()) {
                addValidityPeriodToSituation(ptSituationElement, period.getStartDate(), period.getEndDate());
            }
        }

        ZonedDateTime creationTime = disruption.getCreationDateTime().atZone(zoneId);
        ptSituationElement.setCreationTime(creationTime);


        if (disruption.getPublicationStartDateTime() != null) {
            HalfOpenTimestampOutputRangeStructure publicationWindow = new HalfOpenTimestampOutputRangeStructure();

            ZonedDateTime startPublication = disruption.getPublicationStartDateTime().atZone(zoneId);
            publicationWindow.setStartTime(startPublication);

            if (disruption.getPublicationEndDateTime() != null) {
                ZonedDateTime endPublication = disruption.getPublicationEndDateTime().atZone(zoneId);
                publicationWindow.setEndTime(endPublication);
            }
            ptSituationElement.setPublicationWindow(publicationWindow);
        }
    }


    private static void addValidityPeriodToSituation(PtSituationElement ptSituationElement, LocalDateTime startDate, LocalDateTime endDate) {
        ZoneId zoneId = ZoneId.systemDefault();
        HalfOpenTimestampOutputRangeStructure validityPeriod = new HalfOpenTimestampOutputRangeStructure();

        if (startDate != null) {
            ZonedDateTime startDateTime = startDate.atZone(zoneId);
            validityPeriod.setStartTime(startDateTime);
        } else {
            ZonedDateTime timestamp = ZonedDateTime.ofInstant(Instant.ofEpochMilli(Long.MIN_VALUE), zoneId);
            validityPeriod.setStartTime(timestamp);
        }

        if (endDate != null) {
            ZonedDateTime endDateTime = endDate.atZone(zoneId);
            validityPeriod.setEndTime(endDateTime);
        }

        if (startDate != null || endDate != null) {
            ptSituationElement.getValidityPeriods().add(validityPeriod);
        }

    }

    private static void mapDescription(PtSituationElement ptSituationElement, Disruption disruption) {

        RequestorRef requestor = new RequestorRef();
        requestor.setValue("MOBIITI");
        ptSituationElement.setParticipantRef(requestor);
        SituationNumber situationNumber = new SituationNumber();
        situationNumber.setValue(disruption.getOrganization() + "-" + disruption.getId());
        ptSituationElement.setSituationNumber(situationNumber);
        SituationVersion situationVersion = new SituationVersion();
        situationVersion.setValue(BigInteger.valueOf(1));
        ptSituationElement.setVersion(situationVersion);
        SituationSourceStructure source = new SituationSourceStructure();
        NaturalLanguageStringStructure name = new NaturalLanguageStringStructure();
        name.setValue("Mobi-iti disruption service");
        source.setName(name);
        ptSituationElement.setSource(source);

        //Map of message summary
        DefaultedTextStructure summary = new DefaultedTextStructure();
        summary.setValue(disruption.getMessage());
        summary.setLang("FR");
        ptSituationElement.getSummaries().add(summary);

        //Map of message comment
        DefaultedTextStructure comment = new DefaultedTextStructure();
        comment.setValue(disruption.getComment());
        comment.setLang("FR");
        ptSituationElement.getDescriptions().add(comment);

    }


}
