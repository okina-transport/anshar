package no.rutebanken.anshar.idTests.situationExchange;

import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.*;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.StringBody.subString;
import static org.mockserver.verify.VerificationTimes.exactly;

public class TestUtils {

    private static final Logger logger = LoggerFactory.getLogger(TestUtils.class);

    public static PtSituationElement createSituationForLine(String situationNumber, String lineCode) {
        PtSituationElement situation = new PtSituationElement();
        SituationNumber sitNumber = new SituationNumber();
        sitNumber.setValue(situationNumber);
        situation.setSituationNumber(sitNumber);
        AffectsScopeStructure affectedStruct = new AffectsScopeStructure();
        AffectsScopeStructure.Networks networks = new AffectsScopeStructure.Networks();
        AffectsScopeStructure.Networks.AffectedNetwork affNet = new AffectsScopeStructure.Networks.AffectedNetwork();
        AffectedLineStructure affLine = new AffectedLineStructure();
        LineRef lineRef = new LineRef();
        lineRef.setValue(lineCode);
        affLine.setLineRef(lineRef);
        affNet.getAffectedLines().add(affLine);
        networks.getAffectedNetworks().add(affNet);
        affectedStruct.setNetworks(networks);
        situation.setAffects(affectedStruct);
        return situation;
    }

    public static void verifyStringInResponse(ClientAndServer mockServer, String stringToCheck) {
        mockServer.verify(
                request()
                        .withMethod("POST")
                        .withPath("/incomingSiri")
                        .withBody(subString(stringToCheck)),
                exactly(1));

    }

    public static void printReceivedRequests(ClientAndServer mockServer) {
        HttpRequest[] recordedRequests = mockServer.retrieveRecordedRequests(
                request()
                        .withMethod("POST")
                        .withPath("/incomingSiri")
        );

        for (HttpRequest recordedRequest : recordedRequests) {
            logger.info("Requête reçue: " + recordedRequest);
        }

    }


    public static void addAffectedStop(PtSituationElement situation, String stopCode) {
        AffectedStopPointStructure affectedStopStruct = new AffectedStopPointStructure();
        StopPointRef stopRef = new StopPointRef();
        stopRef.setValue(stopCode);
        affectedStopStruct.setStopPointRef(stopRef);


        AffectsScopeStructure.StopPoints stopPoints = new AffectsScopeStructure.StopPoints();
        stopPoints.getAffectedStopPoints().add(affectedStopStruct);
        situation.getAffects().setStopPoints(stopPoints);

    }

    public static void addAffectedLine(PtSituationElement situation, String lineRef) {
        AffectsScopeStructure.Networks networks = new AffectsScopeStructure.Networks();
        AffectsScopeStructure.Networks.AffectedNetwork affectedNetwork = new AffectsScopeStructure.Networks.AffectedNetwork();
        AffectedLineStructure affectedLine = new AffectedLineStructure();

        LineRef lineRefStruct = new LineRef();
        lineRefStruct.setValue(lineRef);
        affectedLine.setLineRef(lineRefStruct);

        affectedNetwork.getAffectedLines().add(affectedLine);
        networks.getAffectedNetworks().add(affectedNetwork);
        AffectsScopeStructure.Networks networkList = situation.getAffects().getNetworks();
        if (networkList != null) {
            networkList.getAffectedNetworks().add(affectedNetwork);
        } else {
            situation.getAffects().setNetworks(networks);
        }

    }

    public static void addAffectedNetwork(PtSituationElement situation, String networkRef) {
        AffectsScopeStructure.Networks networks = new AffectsScopeStructure.Networks();
        AffectsScopeStructure.Networks.AffectedNetwork affectedNetwork = new AffectsScopeStructure.Networks.AffectedNetwork();
        NetworkRefStructure networkRefStructure = new NetworkRefStructure();
        networkRefStructure.setValue(networkRef);
        affectedNetwork.setNetworkRef(networkRefStructure);

        networks.getAffectedNetworks().add(affectedNetwork);
        AffectsScopeStructure.Networks networkList = situation.getAffects().getNetworks();
        if (networkList != null) {
            networkList.getAffectedNetworks().add(affectedNetwork);
        } else {
            situation.getAffects().setNetworks(networks);
        }
    }

    public static void addAffectedStopInRoute(PtSituationElement situation, String stopCode) {
        AffectedLineStructure line = situation.getAffects().getNetworks().getAffectedNetworks().get(0).getAffectedLines().get(0);
        AffectedStopPointStructure affectedStopStruct = new AffectedStopPointStructure();
        StopPointRef stopRef = new StopPointRef();
        stopRef.setValue(stopCode);
        affectedStopStruct.setStopPointRef(stopRef);
        AffectedLineStructure.Routes routes = new AffectedLineStructure.Routes();
        AffectedRouteStructure affectedRoute = new AffectedRouteStructure();
        AffectedRouteStructure.StopPoints stopPoints = new AffectedRouteStructure.StopPoints();
        stopPoints.getAffectedStopPointsAndLinkProjectionToNextStopPoints().add(affectedStopStruct);
        affectedRoute.setStopPoints(stopPoints);
        routes.getAffectedRoutes().add(affectedRoute);
        line.setRoutes(routes);
    }

    public static List<PtSituationElement> extractSituationsFromSiri(Siri siri) {
        List<PtSituationElement> results = new ArrayList<>();
        if (siri.getServiceDelivery() == null || siri.getServiceDelivery().getSituationExchangeDeliveries() == null || siri.getServiceDelivery().getSituationExchangeDeliveries().isEmpty()) {
            return new ArrayList<>();
        }

        for (SituationExchangeDeliveryStructure situationExchangeDelivery : siri.getServiceDelivery().getSituationExchangeDeliveries()) {
            if (situationExchangeDelivery.getSituations() != null) {
                results.addAll(situationExchangeDelivery.getSituations().getPtSituationElements());
            }
        }
        return results;
    }

    public static PtSituationElement getSituationFromSiri(Siri siri, String situationNumber) {

        for (SituationExchangeDeliveryStructure situationExchangeDelivery : siri.getServiceDelivery().getSituationExchangeDeliveries()) {

            for (PtSituationElement ptSituationElement : situationExchangeDelivery.getSituations().getPtSituationElements()) {
                if (ptSituationElement.getSituationNumber() != null && ptSituationElement.getSituationNumber().getValue().equals(situationNumber)) {
                    return ptSituationElement;
                }
            }
        }
        throw new IllegalArgumentException("Unable to find situation in siri:" + situationNumber);
    }


    public static String getLineRef(PtSituationElement situation) {
        if (situation.getAffects() == null || situation.getAffects().getNetworks() == null || situation.getAffects().getNetworks().getAffectedNetworks() == null
                || situation.getAffects().getNetworks().getAffectedNetworks().get(0).getAffectedLines() == null) {
            return null;
        }

        return situation.getAffects().getNetworks().getAffectedNetworks().get(0).getAffectedLines().get(0).getLineRef().getValue();
    }


    public static String getNetworkRef(PtSituationElement situation) {
        if (situation.getAffects() == null || situation.getAffects().getNetworks() == null || situation.getAffects().getNetworks().getAffectedNetworks() == null
                || situation.getAffects().getNetworks().getAffectedNetworks().get(1).getAffectedLines() == null) {
            return null;
        }

        return situation.getAffects().getNetworks().getAffectedNetworks().get(1).getNetworkRef().getValue();
    }

    public static String getStopRef(PtSituationElement situation) {
        if (situation.getAffects() == null || situation.getAffects().getStopPoints() == null || situation.getAffects().getStopPoints().getAffectedStopPoints() == null
                || situation.getAffects().getStopPoints().getAffectedStopPoints().get(0).getStopPointRef() == null) {
            return null;
        }

        return situation.getAffects().getStopPoints().getAffectedStopPoints().get(0).getStopPointRef().getValue();
    }

    public static String getStopRefInRoute(PtSituationElement situation) {
        if (situation.getAffects() == null || situation.getAffects().getNetworks() == null || situation.getAffects().getNetworks().getAffectedNetworks() == null
                || situation.getAffects().getNetworks().getAffectedNetworks().get(0).getAffectedLines() == null) {
            return null;
        }

        AffectedLineStructure line = situation.getAffects().getNetworks().getAffectedNetworks().get(0).getAffectedLines().get(0);
        if (line.getRoutes() == null || line.getRoutes().getAffectedRoutes() == null) {
            return null;
        }

        Serializable stop = line.getRoutes().getAffectedRoutes().get(0).getStopPoints().getAffectedStopPointsAndLinkProjectionToNextStopPoints().get(0);
        if (stop instanceof AffectedStopPointStructure) {
            AffectedStopPointStructure affectedStop = (AffectedStopPointStructure) stop;
            return affectedStop.getStopPointRef().getValue();
        }

        return null;
    }
}
