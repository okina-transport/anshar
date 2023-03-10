package no.rutebanken.anshar.util;

import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.frGeneralMessageStructure.Content;
import uk.org.siri.siri20.GeneralMessage;
import uk.org.siri.siri20.GeneralMessageDeliveryStructure;
import uk.org.siri.siri20.Siri;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class GeneralMessageHelper {


    public static void applyTransformationsInContent(Siri siri,  Map<ObjectType, Optional<IdProcessingParameters>> idMap){

        if (siri.getServiceDelivery() == null || siri.getServiceDelivery().getGeneralMessageDeliveries() == null ||
                siri.getServiceDelivery().getGeneralMessageDeliveries().size() == 0 ){
            return;
        }

        for (GeneralMessageDeliveryStructure generalMessageDelivery : siri.getServiceDelivery().getGeneralMessageDeliveries()) {
            applyTransformationToGeneralMessageDelivery(generalMessageDelivery,idMap);
        }

    }

    private static void applyTransformationToGeneralMessageDelivery(GeneralMessageDeliveryStructure generalMessageDelivery, Map<ObjectType, Optional<IdProcessingParameters>> idMap) {

        if (generalMessageDelivery.getGeneralMessages() == null || generalMessageDelivery.getGeneralMessages().size() == 0){
            return;
        }

        for (GeneralMessage generalMessage : generalMessageDelivery.getGeneralMessages()) {
            applyTransformationsToGeneralMessage(generalMessage,idMap);
        }
    }

    private static void applyTransformationsToGeneralMessage(GeneralMessage generalMessage, Map<ObjectType, Optional<IdProcessingParameters>> idMap) {
        if (generalMessage.getContent() == null || !(generalMessage.getContent() instanceof Content)){
            return;
        }

        Content content = (Content) generalMessage.getContent();
        Optional<IdProcessingParameters> idProcLineOpt = idMap.get(ObjectType.LINE);
        Optional<IdProcessingParameters> idProcStopOpt = idMap.get(ObjectType.STOP);

        if (content.getLineRefs() != null && content.getLineRefs().size() > 0 && idProcLineOpt.isPresent()){
            IdProcessingParameters idProcessingParams = idProcLineOpt.get();
            List<String> processedIds = content.getLineRefs().stream()
                                                        .map(idProcessingParams::applyTransformationToString)
                                                        .collect(Collectors.toList());

            content.setLineRefs(processedIds);
        }

        if (content.getStopPointRefs() != null && content.getStopPointRefs().size() > 0 && idProcStopOpt.isPresent()){
            IdProcessingParameters idProcessingParams = idProcStopOpt.get();
            List<String> processedIds = content.getStopPointRefs().stream()
                                    .map(idProcessingParams::applyTransformationToString)
                                    .collect(Collectors.toList());

            content.setStopPointRefs(processedIds);
        }
    }
}
