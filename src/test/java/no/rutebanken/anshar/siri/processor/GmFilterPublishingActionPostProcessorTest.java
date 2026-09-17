package no.rutebanken.anshar.siri.processor;

import no.rutebanken.anshar.data.frGeneralMessageStructure.Content;
import no.rutebanken.anshar.data.frGeneralMessageStructure.Message;
import no.rutebanken.anshar.data.frGeneralMessageStructure.MessageType;
import no.rutebanken.anshar.routes.siri.processor.GmFilterPublishingActionPostProcessor;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.GeneralMessage;
import uk.org.siri.siri21.GeneralMessageDeliveryStructure;
import uk.org.siri.siri21.ServiceDelivery;
import uk.org.siri.siri21.Siri;

import static org.assertj.core.api.Assertions.assertThat;

class GmFilterPublishingActionPostProcessorTest {

    private static final String PUBLISHING_ACTION_NAME = "publishToMobile";

    private final GmFilterPublishingActionPostProcessor tested = new GmFilterPublishingActionPostProcessor(PUBLISHING_ACTION_NAME);

    @Test
    void test_whenContentHasMatchingPublishingAction_shouldKeepGMAndReplaceMessageText() {
        // Arrange
        Content content = createContent("MESSAGE TEXT CONTENT");
        content.getPublishingActions().put(PUBLISHING_ACTION_NAME, "MESSAGE TEXT PUBLISHING ACTION");
        GeneralMessageDeliveryStructure gmds = createDelivery(createGeneralMessage(content));
        Siri siri = createSiri(gmds);

        // Act
        tested.process(siri);

        // Assert
        assertThat(gmds.getGeneralMessages()).hasSize(1);
        Content result = (Content) gmds.getGeneralMessages().getFirst().getContent();
        assertThat(result.getMessages()).hasSize(1);
        assertThat(result.getMessages().getFirst().getMsgText()).isEqualTo("MESSAGE TEXT PUBLISHING ACTION");
    }

    @Test
    void test_whenContentHasNoMatchingPublishingAction_shouldRemoveGM() {
        // Arrange
        Content content = createContent("MESSAGE TEXT CONTENT");
        content.getPublishingActions().put("someOtherAction", "OTHER MESSAGE TEXT");
        GeneralMessageDeliveryStructure gmds = createDelivery(createGeneralMessage(content));
        Siri siri = createSiri(gmds);

        // Act
        tested.process(siri);

        // Assert
        assertThat(gmds.getGeneralMessages()).isEmpty();
    }

    @Test
    void test_whenContentHasNoPublishingActionsAtAll_shouldRemoveGM() {
        // Arrange
        Content content = createContent("MESSAGE TEXT CONTENT");
        GeneralMessageDeliveryStructure gmds = createDelivery(createGeneralMessage(content));
        Siri siri = createSiri(gmds);

        // Act
        tested.process(siri);

        // Assert
        assertThat(gmds.getGeneralMessages()).isEmpty();
    }

    @Test
    void test_whenGMContentIsNotFrGeneralMessageStructure_shouldRemoveGM() {
        // Arrange
        GeneralMessage gm = new GeneralMessage();
        gm.setContent(new Object());
        GeneralMessageDeliveryStructure gmds = createDelivery(gm);
        Siri siri = createSiri(gmds);

        // Act
        tested.process(siri);

        // Assert
        assertThat(gmds.getGeneralMessages()).isEmpty();
    }

    @Test
    void test_whenGMContentIsNull_shouldRemoveGM() {
        // Arrange
        GeneralMessage gm = new GeneralMessage();
        gm.setContent(null);
        GeneralMessageDeliveryStructure gmds = createDelivery(gm);
        Siri siri = createSiri(gmds);

        // Act
        tested.process(siri);

        // Assert
        assertThat(gmds.getGeneralMessages()).isEmpty();
    }

    @Test
    void test_whenContentHasMultipleMessages_shouldReplaceTextOnAllMessages() {
        // Arrange
        Content content = createContent("FIRST MESSAGE");
        content.getMessages().add(createMessage("SECOND MESSAGE"));
        content.getPublishingActions().put(PUBLISHING_ACTION_NAME, "REPLACED MESSAGE TEXT");
        GeneralMessageDeliveryStructure gmds = createDelivery(createGeneralMessage(content));
        Siri siri = createSiri(gmds);

        // Act
        tested.process(siri);

        // Assert
        Content result = (Content) gmds.getGeneralMessages().getFirst().getContent();
        assertThat(result.getMessages()).hasSize(2);
        assertThat(result.getMessages())
                .extracting(Message::getMsgText)
                .containsExactly("REPLACED MESSAGE TEXT", "REPLACED MESSAGE TEXT");
    }

    @Test
    void test_whenDeliveryHasMultipleGMs_shouldKeepOnlyMatchingOnes() {
        // Arrange
        Content matchingContent = createContent("MATCHING MESSAGE");
        matchingContent.getPublishingActions().put(PUBLISHING_ACTION_NAME, "MATCHING REPLACED TEXT");
        GeneralMessage matchingGm = createGeneralMessage(matchingContent);

        Content nonMatchingContent = createContent("NON MATCHING MESSAGE");
        nonMatchingContent.getPublishingActions().put("otherAction", "OTHER TEXT");
        GeneralMessage nonMatchingGm = createGeneralMessage(nonMatchingContent);

        GeneralMessageDeliveryStructure gmds = createDelivery(matchingGm, nonMatchingGm);
        Siri siri = createSiri(gmds);

        // Act
        tested.process(siri);

        // Assert
        assertThat(gmds.getGeneralMessages()).hasSize(1);
        Content result = (Content) gmds.getGeneralMessages().getFirst().getContent();
        assertThat(result.getMessages().getFirst().getMsgText()).isEqualTo("MATCHING REPLACED TEXT");
    }

    @Test
    void test_whenSiriHasMultipleDeliveries_shouldFilterEachIndependently() {
        // Arrange
        Content matchingContent = createContent("MESSAGE 1");
        matchingContent.getPublishingActions().put(PUBLISHING_ACTION_NAME, "REPLACED TEXT");
        GeneralMessageDeliveryStructure matchingDelivery = createDelivery(createGeneralMessage(matchingContent));

        Content nonMatchingContent = createContent("MESSAGE 2");
        GeneralMessageDeliveryStructure nonMatchingDelivery = createDelivery(createGeneralMessage(nonMatchingContent));

        Siri siri = new Siri();
        ServiceDelivery serviceDelivery = new ServiceDelivery();
        serviceDelivery.getGeneralMessageDeliveries().add(matchingDelivery);
        serviceDelivery.getGeneralMessageDeliveries().add(nonMatchingDelivery);
        siri.setServiceDelivery(serviceDelivery);

        // Act
        tested.process(siri);

        // Assert
        assertThat(matchingDelivery.getGeneralMessages()).hasSize(1);
        assertThat(nonMatchingDelivery.getGeneralMessages()).isEmpty();
    }

    private Content createContent(String messageText) {
        Content content = new Content();
        content.getMessages().add(createMessage(messageText));
        return content;
    }

    private Message createMessage(String messageText) {
        Message message = new Message();
        message.setMsgType(MessageType.TEXT_ONLY);
        message.setMsgText(messageText);
        return message;
    }

    private GeneralMessage createGeneralMessage(Content content) {
        GeneralMessage gm = new GeneralMessage();
        gm.setContent(content);
        return gm;
    }

    private GeneralMessageDeliveryStructure createDelivery(GeneralMessage... gms) {
        GeneralMessageDeliveryStructure gmds = new GeneralMessageDeliveryStructure();
        gmds.getGeneralMessages().addAll(java.util.List.of(gms));
        return gmds;
    }

    private Siri createSiri(GeneralMessageDeliveryStructure... deliveries) {
        Siri siri = new Siri();
        ServiceDelivery serviceDelivery = new ServiceDelivery();
        serviceDelivery.getGeneralMessageDeliveries().addAll(java.util.List.of(deliveries));
        siri.setServiceDelivery(serviceDelivery);
        return siri;
    }
}
