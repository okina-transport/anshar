package no.rutebanken.anshar.util;

import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.frGeneralMessageStructure.Content;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri21.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class GeneralMessageHelper {

    private static final Logger logger = LoggerFactory.getLogger(GeneralMessageHelper.class);

    public static void applyTransformationsInContent(Siri siri, List<ValueAdapter> valueAdapters, Map<ObjectType, Optional<IdProcessingParameters>> idMap) {

        if (siri.getServiceDelivery() == null || siri.getServiceDelivery().getGeneralMessageDeliveries() == null ||
                siri.getServiceDelivery().getGeneralMessageDeliveries().size() == 0) {
            return;
        }

        for (GeneralMessageDeliveryStructure generalMessageDelivery : siri.getServiceDelivery().getGeneralMessageDeliveries()) {
            applyTransformationToGeneralMessageDelivery(generalMessageDelivery, valueAdapters, idMap);
        }

    }

    public static Siri applyTransformationsInContent(Siri siri, List<ValueAdapter> valueAdapters, Map<ObjectType, Optional<IdProcessingParameters>> idMap, Boolean deepCopyBeforeTransform) {

        if (siri.getServiceDelivery() == null || siri.getServiceDelivery().getGeneralMessageDeliveries() == null ||
                siri.getServiceDelivery().getGeneralMessageDeliveries().isEmpty()) {
            return null;
        }

        Siri transformed;
        if (deepCopyBeforeTransform) {
            try {
                transformed = SiriObjectFactory.deepCopy(siri);
            } catch (Exception e) {
                logger.warn("Unable to transform SIRI-object", e);
                return siri;
            }

        } else {
            transformed = siri;
        }

        for (GeneralMessageDeliveryStructure generalMessageDelivery : transformed.getServiceDelivery().getGeneralMessageDeliveries()) {
            applyTransformationToGeneralMessageDelivery(generalMessageDelivery, valueAdapters, idMap);
        }

        return transformed;

    }


    private static void applyTransformationToGeneralMessageDelivery(GeneralMessageDeliveryStructure generalMessageDelivery, List<ValueAdapter> valueAdapters, Map<ObjectType, Optional<IdProcessingParameters>> idMap) {

        if (generalMessageDelivery.getGeneralMessages() == null || generalMessageDelivery.getGeneralMessages().size() == 0) {
            return;
        }

        for (GeneralMessage generalMessage : generalMessageDelivery.getGeneralMessages()) {
            applyTransformationsToGeneralMessage(generalMessage, valueAdapters, idMap);
        }
    }

    private static void applyTransformationsToGeneralMessage(GeneralMessage generalMessage, List<ValueAdapter> valueAdapters, Map<ObjectType, Optional<IdProcessingParameters>> idMap) {
        if (generalMessage.getContent() == null || !(generalMessage.getContent() instanceof Content)) {
            return;
        }

        Content content = (Content) generalMessage.getContent();
        Optional<IdProcessingParameters> idProcLineOpt = idMap.get(ObjectType.LINE);
        Optional<IdProcessingParameters> idProcStopOpt = idMap.get(ObjectType.STOP);

        Optional<OutboundIdAdapter> stopRefAdapterOpt = getStopRefAdapter(valueAdapters);
        Optional<OutboundIdAdapter> lineRefAdapterOpt = getLineRefAdapter(valueAdapters);

        if (content.getLineRefs() != null && content.getLineRefs().size() > 0 && idProcLineOpt.isPresent()) {
            if (lineRefAdapterOpt.isPresent()) {
                OutboundIdAdapter lineRefAdapter = lineRefAdapterOpt.get();
                List<String> processedIds = content.getLineRefs().stream()
                        .map(lineRefAdapter::apply)
                        .collect(Collectors.toList());
                content.setLineRefs(processedIds);
            }
        }

        if (content.getStopPointRefs() != null && content.getStopPointRefs().size() > 0 && idProcStopOpt.isPresent()) {
            if (stopRefAdapterOpt.isPresent()) {
                OutboundIdAdapter stopRefAdapter = stopRefAdapterOpt.get();
                List<String> processedIds = content.getStopPointRefs().stream()
                        .map(stopRefAdapter::apply)
                        .collect(Collectors.toList());
                content.setStopPointRefs(processedIds);
            }
        }
    }


    private static Optional<OutboundIdAdapter> getStopRefAdapter(List<ValueAdapter> valueAdapters) {

        for (ValueAdapter valueAdapter : valueAdapters) {
            if (!(valueAdapter instanceof OutboundIdAdapter)) {
                continue;
            }

            OutboundIdAdapter currAdapter = (OutboundIdAdapter) valueAdapter;
            if (currAdapter.getClassToApply().equals(StopPointRefStructure.class)) {
                return Optional.of(currAdapter);
            }
        }
        return Optional.empty();
    }

    private static Optional<OutboundIdAdapter> getLineRefAdapter(List<ValueAdapter> valueAdapters) {

        for (ValueAdapter valueAdapter : valueAdapters) {
            if (!(valueAdapter instanceof OutboundIdAdapter)) {
                continue;
            }

            OutboundIdAdapter currAdapter = (OutboundIdAdapter) valueAdapter;
            if (currAdapter.getClassToApply().equals(LineRef.class)) {
                return Optional.of(currAdapter);
            }
        }
        return Optional.empty();
    }
}
