package no.rutebanken.anshar.idTests.situationExchange;

import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.*;

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

    public static String getStopRef(PtSituationElement situation) {
        if (situation.getAffects() == null || situation.getAffects().getStopPoints() == null || situation.getAffects().getStopPoints().getAffectedStopPoints() == null
                || situation.getAffects().getStopPoints().getAffectedStopPoints().get(0).getStopPointRef() == null) {
            return null;
        }

        return situation.getAffects().getStopPoints().getAffectedStopPoints().get(0).getStopPointRef().getValue();
    }
}