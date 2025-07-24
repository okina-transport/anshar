package no.rutebanken.anshar.siri.processor;

import jakarta.xml.bind.JAXBException;
import no.rutebanken.anshar.data.frGeneralMessageStructure.Content;
import no.rutebanken.anshar.data.util.CustomSiriXml;
import no.rutebanken.anshar.routes.siri.processor.GmSIVSicAQuayPostProcessor;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.Siri;

import javax.xml.stream.XMLStreamException;

import static org.assertj.core.api.Assertions.assertThat;

class GmSIVSicAQuayPostProcessorTest {

    private static final String SIRI_GM_XML_WITH_ALERT_MESSAGE_SIC_A_QUAY = """
            <?xml version="1.0" ?>
            <Siri xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt" xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0" xmlns:ns5="http://www.opengis.net/gml/3.2" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" version="2.1">
              <ServiceDelivery>
                <ResponseTimestamp>2025-07-23T15:54:48.929353135+02:00</ResponseTimestamp>
                <ProducerRef>OKI</ProducerRef>
                <Status>true</Status>
                <GeneralMessageDelivery version="2.1">
                  <ResponseTimestamp>2025-07-23T15:54:48.929363309+02:00</ResponseTimestamp>
                  <GeneralMessage formatRef="France">
                    <RecordedAtTime>2025-07-23T15:46:02+02:00</RecordedAtTime>
                    <ItemIdentifier>5f4c6a24-67cb-11f0-89f0-0a58a9feac02</ItemIdentifier>
                    <InfoMessageIdentifier>5f4c6a24-67cb-11f0-89f0-0a58a9feac02</InfoMessageIdentifier>
                    <InfoChannelRef>Perturbation</InfoChannelRef>
                    <ValidUntilTime>2025-07-24T15:46:01+02:00</ValidUntilTime>
                    <SituationRef>
                      <SituationSimpleRef>5f4c6a24-67cb-11f0-89f0-0a58a9feac02</SituationSimpleRef>
                    </SituationRef>
                    <Content xmlns:ns7="http://www.siri.org.uk/siri" xsi:type="FrGeneralMessageStructure">
                      <ns7:StopPointRef>LONG2</ns7:StopPointRef>
                      <ns7:Message>
                        <ns7:MessageType>textOnly</ns7:MessageType>
                        <ns7:MessageText>MESSAGE TEXT CONTENT</ns7:MessageText>
                      </ns7:Message>
                    </Content>
                    <Extensions>
                      <Alerts>
                        <SendNotifications>true</SendNotifications>
                        <NotificationsDate>2025-07-23T13:46:02Z</NotificationsDate>
                        <AlertMessages>
                          <AlertMessage>
                            <ChannelName>SIC à quai</ChannelName>
                            <ChannelType>pids</ChannelType>
                            <MessageUsage>start</MessageUsage>
                            <NotificationDate>2025-07-23T13:46:02Z</NotificationDate>
                            <MessageText>MESSAGE TEXT SIC A QUAY</MessageText>
                          </AlertMessage>
                        </AlertMessages>
                      </Alerts>
                    </Extensions>
                  </GeneralMessage>
                </GeneralMessageDelivery>
              </ServiceDelivery>
            </Siri>""";

    private static final String SIRI_GM_XML_WITHOUT_ALERT_MESSAGE_SIC_A_QUAY = """
            <?xml version="1.0" ?>
            <Siri xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt" xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0" xmlns:ns5="http://www.opengis.net/gml/3.2" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" version="2.1">
              <ServiceDelivery>
                <ResponseTimestamp>2025-07-23T15:54:48.929353135+02:00</ResponseTimestamp>
                <ProducerRef>OKI</ProducerRef>
                <Status>true</Status>
                <GeneralMessageDelivery version="2.1">
                  <ResponseTimestamp>2025-07-23T15:54:48.929363309+02:00</ResponseTimestamp>
                  <GeneralMessage formatRef="France">
                    <RecordedAtTime>2025-07-23T15:46:02+02:00</RecordedAtTime>
                    <ItemIdentifier>5f4c6a24-67cb-11f0-89f0-0a58a9feac02</ItemIdentifier>
                    <InfoMessageIdentifier>5f4c6a24-67cb-11f0-89f0-0a58a9feac02</InfoMessageIdentifier>
                    <InfoChannelRef>Perturbation</InfoChannelRef>
                    <ValidUntilTime>2025-07-24T15:46:01+02:00</ValidUntilTime>
                    <SituationRef>
                      <SituationSimpleRef>5f4c6a24-67cb-11f0-89f0-0a58a9feac02</SituationSimpleRef>
                    </SituationRef>
                    <Content xmlns:ns7="http://www.siri.org.uk/siri" xsi:type="FrGeneralMessageStructure">
                      <ns7:StopPointRef>LONG2</ns7:StopPointRef>
                      <ns7:Message>
                        <ns7:MessageType>textOnly</ns7:MessageType>
                        <ns7:MessageText>MESSAGE TEXT CONTENT</ns7:MessageText>
                      </ns7:Message>
                    </Content>
                    <Extensions>
                      <Alerts>
                        <SendNotifications>true</SendNotifications>
                        <NotificationsDate>2025-07-23T13:46:02Z</NotificationsDate>
                        <AlertMessages>
                          <AlertMessage>
                            <ChannelName>SIC embarqué</ChannelName>
                            <ChannelType>pids</ChannelType>
                            <MessageUsage>start</MessageUsage>
                            <NotificationDate>2025-07-23T13:46:02Z</NotificationDate>
                            <MessageText>MESSAGE TEXT SIC EMBARQUE</MessageText>
                          </AlertMessage>
                        </AlertMessages>
                      </Alerts>
                    </Extensions>
                  </GeneralMessage>
                </GeneralMessageDelivery>
              </ServiceDelivery>
            </Siri>""";

    private static final String SIRI_GM_XML_WITHOUT_EXTENSIONS = """
            <?xml version="1.0" ?>
            <Siri xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt" xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0" xmlns:ns5="http://www.opengis.net/gml/3.2" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" version="2.1">
              <ServiceDelivery>
                <ResponseTimestamp>2025-07-23T15:54:48.929353135+02:00</ResponseTimestamp>
                <ProducerRef>OKI</ProducerRef>
                <Status>true</Status>
                <GeneralMessageDelivery version="2.1">
                  <ResponseTimestamp>2025-07-23T15:54:48.929363309+02:00</ResponseTimestamp>
                  <GeneralMessage formatRef="France">
                    <RecordedAtTime>2025-07-23T15:46:02+02:00</RecordedAtTime>
                    <ItemIdentifier>5f4c6a24-67cb-11f0-89f0-0a58a9feac02</ItemIdentifier>
                    <InfoMessageIdentifier>5f4c6a24-67cb-11f0-89f0-0a58a9feac02</InfoMessageIdentifier>
                    <InfoChannelRef>Perturbation</InfoChannelRef>
                    <ValidUntilTime>2025-07-24T15:46:01+02:00</ValidUntilTime>
                    <SituationRef>
                      <SituationSimpleRef>5f4c6a24-67cb-11f0-89f0-0a58a9feac02</SituationSimpleRef>
                    </SituationRef>
                    <Content xmlns:ns7="http://www.siri.org.uk/siri" xsi:type="FrGeneralMessageStructure">
                      <ns7:StopPointRef>LONG2</ns7:StopPointRef>
                      <ns7:Message>
                        <ns7:MessageType>textOnly</ns7:MessageType>
                        <ns7:MessageText>MESSAGE TEXT CONTENT</ns7:MessageText>
                      </ns7:Message>
                    </Content>
                  </GeneralMessage>
                </GeneralMessageDelivery>
              </ServiceDelivery>
            </Siri>""";


    GmSIVSicAQuayPostProcessor tested = new GmSIVSicAQuayPostProcessor();

    @Test
    void test_whenGMHasSicAQuayAlertMessageMessageText_shouldReplaceContentMessageText() throws XMLStreamException, JAXBException {
        // Arrange
        Siri siri = CustomSiriXml.parseXml(SIRI_GM_XML_WITH_ALERT_MESSAGE_SIC_A_QUAY);

        // Act
        tested.process(siri);

        // Assert
        Content content = CustomSiriXml.getGeneralMessageContent(siri.getServiceDelivery().getGeneralMessageDeliveries().getFirst().getGeneralMessages().getFirst());
        assertThat(content.getMessage().getMsgText()).isEqualTo("MESSAGE TEXT SIC A QUAY");
    }

    @Test
    void test_whenGMHasNoSicAQuayAlertMessageMessageText_shouldNotReplaceContentMessageText() throws XMLStreamException, JAXBException {
        // Arrange
        Siri siri = CustomSiriXml.parseXml(SIRI_GM_XML_WITHOUT_ALERT_MESSAGE_SIC_A_QUAY);

        // Act
        tested.process(siri);

        // Assert
        Content content = CustomSiriXml.getGeneralMessageContent(siri.getServiceDelivery().getGeneralMessageDeliveries().getFirst().getGeneralMessages().getFirst());
        assertThat(content.getMessage().getMsgText()).isEqualTo("MESSAGE TEXT CONTENT");
    }

    @Test
    void test_whenGMHasNoExtensions_shouldNotReplaceContentMessageText() throws XMLStreamException, JAXBException {
        // Arrange
        Siri siri = CustomSiriXml.parseXml(SIRI_GM_XML_WITHOUT_EXTENSIONS);

        // Act
        tested.process(siri);

        // Assert
        Content content = CustomSiriXml.getGeneralMessageContent(siri.getServiceDelivery().getGeneralMessageDeliveries().getFirst().getGeneralMessages().getFirst());
        assertThat(content.getMessage().getMsgText()).isEqualTo("MESSAGE TEXT CONTENT");
    }

}
