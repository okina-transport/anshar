package no.rutebanken.anshar.data.util;


import no.rutebanken.anshar.data.frGeneralMessageStructure.Content;
import no.rutebanken.anshar.data.frGeneralMessageStructure.Message;
import org.apache.commons.lang3.StringUtils;
import uk.org.siri.siri21.*;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Class that maps a Situation to a GeneralMessage
 */
public class GeneralMessageMapper {

    /**
     * Maps a situation to a general message
     *
     * @param situation situation to convert
     * @return the created GeneralMessage
     */
    public static GeneralMessage mapToGeneralMessage(PtSituationElement situation) {

        GeneralMessage generalMessage = new GeneralMessage();

        generalMessage.setFormatRef("France");
        generalMessage.setRecordedAtTime(situation.getCreationTime());

        mapInfoId(generalMessage, situation);
        mapInfoChannelRef(generalMessage);
        mapValidUntil(generalMessage, situation);
        mapContent(generalMessage, situation);


        return generalMessage;

    }

    private static void mapContent(GeneralMessage generalMessage, PtSituationElement situation) {
        Content content = new Content();
        Message msg = new Message();

        msg.setMsgText(getMsgText(situation));
        msg.setMsgType("textOnly");

        if (situation.getAffects() != null) {
            mapAffects(content, situation);
        }

        content.setMessage(msg);
        generalMessage.setContent(content);
    }

    private static void mapAffects(Content content, PtSituationElement situation) {

        if (situation.getAffects().getNetworks() != null) {



            List<String> lineList = new ArrayList<>();
            List<String> networkList = new ArrayList<>();

            for (AffectsScopeStructure.Networks.AffectedNetwork affectedNetwork : situation.getAffects().getNetworks().getAffectedNetworks()) {

                if (affectedNetwork.getNetworkRef() != null) {
                    networkList.add(affectedNetwork.getNetworkRef().getValue());

                }

                for (AffectedLineStructure affectedLine : affectedNetwork.getAffectedLines()) {
                    lineList.add(affectedLine.getLineRef().getValue());
                }

            }

            if (networkList.size() > 0) {
                content.setGroupOfLinesRefs(networkList);
            }

            if (lineList.size() > 0) {
                content.setLineRefs(lineList);
            }

        }


        if (situation.getAffects().getStopPoints() != null) {

            List<String> stopPointList = new ArrayList<>();

            for (AffectedStopPointStructure affectedStopPoint : situation.getAffects().getStopPoints().getAffectedStopPoints()) {
                stopPointList.add(affectedStopPoint.getStopPointRef().getValue());
            }

            if (stopPointList.size() > 0) {
                content.setStopPointRefs(stopPointList);
            }

        }
    }


    private static String getMsgText(PtSituationElement situation) {

        StringBuilder sb = new StringBuilder();

        for (DefaultedTextStructure summary : situation.getSummaries()) {
            sb.append(summary.getValue());
        }
        return sb.toString();
    }

    private static void mapValidUntil(GeneralMessage generalMessage, PtSituationElement situation) {
        ZonedDateTime currentMax = null;

        for (HalfOpenTimestampOutputRangeStructure validityPeriod : situation.getValidityPeriods()) {
            if (currentMax == null || currentMax.isBefore(validityPeriod.getEndTime())) {
                currentMax = validityPeriod.getEndTime();
            }
        }

        if (currentMax == null) {
            currentMax = ZonedDateTime.now();
            currentMax.plusYears(100);
        }

        generalMessage.setValidUntilTime(currentMax);
    }

    private static void mapInfoId(GeneralMessage generalMessage, PtSituationElement situation) {
        String msgId = situation.getSituationNumber().getValue();
        generalMessage.setItemIdentifier(msgId);

        InfoMessageRefStructure infoMess = new InfoMessageRefStructure();
        infoMess.setValue(msgId);
        generalMessage.setInfoMessageIdentifier(infoMess);

        SituationRef situationRef = new SituationRef();
        SituationSimpleRef simpleRef = new SituationSimpleRef();
        simpleRef.setValue(msgId);
        situationRef.setSituationSimpleRef(simpleRef);
        generalMessage.setSituationRef(situationRef);
    }

    private static void mapInfoChannelRef(GeneralMessage generalMessage) {
        InfoChannelRefStructure infoChannelRef = new InfoChannelRefStructure();
        infoChannelRef.setValue("Perturbation");
        generalMessage.setInfoChannelRef(infoChannelRef);
    }
}
