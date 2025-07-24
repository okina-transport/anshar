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
import org.w3c.dom.NodeList;
import uk.org.siri.siri21.GeneralMessage;
import uk.org.siri.siri21.GeneralMessageDeliveryStructure;
import uk.org.siri.siri21.Siri;

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

    private Optional<String> getAlertMessageSicAQuay(GeneralMessage gm) {
        if (gm.getExtensions() == null || CollectionUtils.isEmpty(gm.getExtensions().getAnies())) {
            return Optional.empty();
        }
        Optional<NodeList> optAlertMessages = gm.getExtensions().getAnies().stream()
                .map(e -> e.getElementsByTagName(ALERT_MESSAGE_TAG_NAME))
                .findFirst();
        if (optAlertMessages.isEmpty()) {
            return Optional.empty();
        }
        // Convert NodeList to List<Element> because it is easier to query with
        List<Element> alertMessages = IntStream.range(0, optAlertMessages.get().getLength())
                .mapToObj(optAlertMessages.get()::item)
                .map(n -> (Element) n)
                .toList();
        for (Element alertMessage : alertMessages) {
            NodeList channelNameNodes = alertMessage.getElementsByTagName(CHANNEL_NAME_TAG_NAME);
            if (channelNameNodes.getLength() == 0) {
                log.warn("No <ChannelName> in <AlertMessage> tag");
                continue;
            }
            Element channelNameTag = (Element) channelNameNodes.item(0);
            NodeList messageTextNodes = alertMessage.getElementsByTagName(MESSAGE_TEXT_TAG_NAME);
            String content = messageTextNodes.getLength() == 0 ? "" :
                    StringUtils.trimToEmpty(messageTextNodes.item(0).getTextContent());
            if (channelNameTag.getTextContent().equals(CHANNEL_NAME_VALUE_SIC_A_QUAI)) {
                return Optional.of(content);

            }
        }
        return Optional.empty();
    }

    @Override
    protected String apply(String value) {
        return null;
    }
}

