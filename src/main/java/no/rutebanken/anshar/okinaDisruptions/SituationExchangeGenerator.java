package no.rutebanken.anshar.okinaDisruptions;

import no.rutebanken.anshar.okinaDisruptions.model.Disruption;
import no.rutebanken.anshar.okinaDisruptions.model.JourneyPattern;
import no.rutebanken.anshar.okinaDisruptions.model.OkinaLine;
import no.rutebanken.anshar.okinaDisruptions.model.OkinaNetwork;
import no.rutebanken.anshar.okinaDisruptions.model.OkinaStopArea;
import no.rutebanken.anshar.okinaDisruptions.model.OkinaVehicleJourney;
import uk.org.siri.siri20.AffectedLineStructure;
import uk.org.siri.siri20.AffectedStopPointStructure;
import uk.org.siri.siri20.AffectedVehicleJourneyStructure;
import uk.org.siri.siri20.AffectsScopeStructure;
import uk.org.siri.siri20.DefaultedTextStructure;
import uk.org.siri.siri20.FramedVehicleJourneyRefStructure;
import uk.org.siri.siri20.HalfOpenTimestampOutputRangeStructure;
import uk.org.siri.siri20.LineRef;
import uk.org.siri.siri20.MiscellaneousReasonEnumeration;
import uk.org.siri.siri20.NaturalLanguageStringStructure;
import uk.org.siri.siri20.NetworkRefStructure;
import uk.org.siri.siri20.PtSituationElement;
import uk.org.siri.siri20.RequestorRef;
import uk.org.siri.siri20.SituationNumber;
import uk.org.siri.siri20.SituationSourceStructure;
import uk.org.siri.siri20.SituationVersion;
import uk.org.siri.siri20.StopPointRef;

import java.math.BigInteger;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

public class SituationExchangeGenerator {


    public static PtSituationElement createFromDisruption(Disruption disruption) {

        PtSituationElement ptSituationElement = new PtSituationElement();
        mapDescription(ptSituationElement, disruption);
        mapPeriod(ptSituationElement, disruption);
        mapReasons(ptSituationElement, disruption);
        mapAffects(ptSituationElement, disruption);

        return ptSituationElement;

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

        if (networks.getAffectedNetworks().size() > 0){
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
            stopPointRef.setValue(stopArea.getObjectId());
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
        ptSituationElement.setMiscellaneousReason(MiscellaneousReasonEnumeration.UNKNOWN);
    }

    private static void mapPeriod(PtSituationElement ptSituationElement, Disruption disruption) {
        HalfOpenTimestampOutputRangeStructure validityPeriod = new HalfOpenTimestampOutputRangeStructure();
        ZoneId zoneId = ZoneId.systemDefault();

        if (disruption.getStartDateTime() != null) {
            ZonedDateTime startDateTime = disruption.getStartDateTime().atZone(zoneId);
            validityPeriod.setStartTime(startDateTime);
        }

        if (disruption.getEndDateTime() != null) {
            ZonedDateTime endDateTime = disruption.getEndDateTime().atZone(zoneId);
            validityPeriod.setEndTime(endDateTime);
        }

        if (disruption.getStartDateTime() != null || disruption.getEndDateTime() != null) {
            ptSituationElement.getValidityPeriods().add(validityPeriod);
        }

        ZonedDateTime creationTime = disruption.getCreationDateTime().atZone(zoneId);
        ptSituationElement.setCreationTime(creationTime);


        if (disruption.getPublicationStartDateTime() != null){
            HalfOpenTimestampOutputRangeStructure publicationWindow = new HalfOpenTimestampOutputRangeStructure();

            ZonedDateTime startPublication = disruption.getPublicationStartDateTime().atZone(zoneId);
            publicationWindow.setStartTime(startPublication);

            if (disruption.getPublicationEndDateTime() != null){
                ZonedDateTime endPublication = disruption.getPublicationEndDateTime().atZone(zoneId);
                publicationWindow.setEndTime(endPublication);
            }
            ptSituationElement.setPublicationWindow(publicationWindow);
        }
    }

    private static void mapDescription(PtSituationElement ptSituationElement, Disruption disruption) {

        RequestorRef requestor = new RequestorRef();
        requestor.setValue("Okina");
        ptSituationElement.setParticipantRef(requestor);
        SituationNumber situationNumber = new SituationNumber();
        situationNumber.setValue(getOrganisationName(disruption) + "-" + disruption.getId());
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
        summary.setLang("LANG_TYPE_FR");
        ptSituationElement.getSummaries().add(summary);

        //Map of message comment
        DefaultedTextStructure comment = new DefaultedTextStructure();
        comment.setValue(disruption.getComment());
        comment.setLang("LANG_TYPE_FR");
        ptSituationElement.getDescriptions().add(comment);

    }

    private static String getOrganisationName(Disruption disruption) {
        if (disruption.getLines().size() > 0) {
            for (OkinaLine line : disruption.getLines()) {
                String[] splittedObjectId = line.getObjectId().split(":");
                if (splittedObjectId.length == 3) {
                    return splittedObjectId[0];
                }
            }
        }

        if (disruption.getCourses().size() > 0) {
            for (OkinaVehicleJourney course : disruption.getCourses()) {
                String[] splittedObjectId = course.getObjectId().split(":");
                if (splittedObjectId.length == 3) {
                    return splittedObjectId[0];
                }
            }
        }

        if (disruption.getStopAreas().size() > 0) {
            for (OkinaStopArea stopArea : disruption.getStopAreas()) {
                String[] splittedObjectId = stopArea.getObjectId().split(":");
                if (splittedObjectId.length == 3) {
                    return splittedObjectId[0];
                }
            }
        }

        if (disruption.getJourneys().size() > 0) {
            for (JourneyPattern journeyPattern: disruption.getJourneys()) {
                String[] splittedObjectId = journeyPattern.getObjectId().split(":");
                if (splittedObjectId.length == 3) {
                    return splittedObjectId[0];
                }
            }
        }

        return "";
    }


}
