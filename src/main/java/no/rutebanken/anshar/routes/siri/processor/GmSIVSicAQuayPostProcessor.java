package no.rutebanken.anshar.routes.siri.processor;

import jakarta.xml.bind.JAXBException;
import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.data.frGeneralMessageStructure.Content;
import no.rutebanken.anshar.data.frGeneralMessageStructure.Message;
import no.rutebanken.anshar.data.util.CustomSiriXml;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import uk.org.siri.siri21.GeneralMessage;
import uk.org.siri.siri21.GeneralMessageDeliveryStructure;
import uk.org.siri.siri21.Siri;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Specific SEM - SIV when GeneralMessage has an Extensions/Alerts/AlertMessages/AlertMessage/ChannelName tag with
 * value "SIC à quay" then copy Extensions/Alerts/AlertMessages/AlertMessage/Channel/MessageText into
 * Content/Message/MessageText
 */
@Slf4j
public class GmSIVSicAQuayPostProcessor extends ValueAdapter implements PostProcessor {

    private static final String ALERT_MESSAGE_TAG_NAME = "AlertMessage";
    private static final String CHANNEL_NAME_TAG_NAME = "ChannelName";
    private static final String MESSAGE_TEXT_TAG_NAME = "MessageText";
    private static final String CHANNEL_NAME_VALUE_SIC_A_QUAI = "SIC à quai";

    @Override
    public void process(Siri siri) {
        List<GeneralMessageDeliveryStructure> gmdss = siri.getServiceDelivery().getGeneralMessageDeliveries();
        for (GeneralMessageDeliveryStructure gmds : gmdss) {
            List<GeneralMessage> gms = gmds.getGeneralMessages();
            for (GeneralMessage gm : gms) {
                Optional<String> alertMessageSicAQuay = getAlertMessageSicAQuay(gm);
                if (alertMessageSicAQuay.isPresent()) {
                    try {
                        Content content = CustomSiriXml.getGeneralMessageContent(gm);
                        if (content == null) {
                            log.error("Null content for GM with situationRef {}, ignore this processing", gm.getSituationRef());
                            continue;
                        }
                        Message message = new Message();
                        message.setMsgText(alertMessageSicAQuay.get());
                        message.setMsgType("textOnly");
                        content.setMessage(message);
                        gm.setContent(content);
                    } catch (JAXBException e) {
                        log.error("Error updating Content.Message.MessageText value", e);
                    }
                }
            }
        }
    }

    private static Optional<String> getAlertMessageSicAQuay(GeneralMessage gm) {
        if (gm.getExtensions() == null || CollectionUtils.isEmpty(gm.getExtensions().getAnies())) {
            return Optional.empty();
        }

        Optional<Element> optAlertsElement = gm.getExtensions().getAnies().stream()
                .filter(e -> e instanceof Element)
                .map(e -> (Element) e)
                .filter(e -> "Alerts".equals(e.getTagName()))
                .findFirst();

        if (optAlertsElement.isEmpty()) {
            return Optional.empty();
        }

        NodeList alertMessageNodes = optAlertsElement.get().getElementsByTagName(ALERT_MESSAGE_TAG_NAME);
        if (alertMessageNodes.getLength() == 0) {
            return Optional.empty();
        }

        List<Element> alertMessages = IntStream.range(0, alertMessageNodes.getLength())
                .mapToObj(alertMessageNodes::item)
                .map(n -> (Element) n)
                .toList();

        for (Element alertMessage : alertMessages) {
            if (isAlertMessageSicAQuai(alertMessage)) {
                NodeList messageTextNodes = alertMessage.getElementsByTagName(MESSAGE_TEXT_TAG_NAME);
                String content = messageTextNodes.getLength() == 0 ? "" :
                        StringUtils.trimToEmpty(messageTextNodes.item(0).getTextContent());
                return Optional.of(content);
            }
        }

        return Optional.empty();
    }

    @Override
    protected String apply(String value) {
        return null;
    }

    public static void filteringSiriGMToKeepSicAQuayAlertMessages(Siri siri) {
        if (CollectionUtils.isNotEmpty(siri.getServiceDelivery().getGeneralMessageDeliveries()) &&
                CollectionUtils.isNotEmpty(siri.getServiceDelivery().getGeneralMessageDeliveries().getFirst().getGeneralMessages())) {

            siri.getServiceDelivery().getGeneralMessageDeliveries().getFirst().getGeneralMessages().removeIf(
                    gm -> getAlertMessageSicAQuay(gm).isEmpty()
            );
        }
    }

    private static boolean isAlertMessageSicAQuai(Element alertMessageElement) {
        NodeList channelNameNodes = alertMessageElement.getElementsByTagName(CHANNEL_NAME_TAG_NAME);
        if (channelNameNodes.getLength() == 0) {
            return false;
        }
        Element channelNameTag = (Element) channelNameNodes.item(0);
        String channelName = channelNameTag.getTextContent();

        return CHANNEL_NAME_VALUE_SIC_A_QUAI.equals(channelName);
    }
}

