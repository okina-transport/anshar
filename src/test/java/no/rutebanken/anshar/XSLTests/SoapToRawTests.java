package no.rutebanken.anshar.XSLTests;

import jakarta.xml.bind.JAXBException;
import no.rutebanken.anshar.data.util.CustomSiriXml;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.Siri;

import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.nio.charset.StandardCharsets;

import static junit.framework.TestCase.assertNotNull;
import static org.junit.Assert.assertEquals;

public class SoapToRawTests extends SpringBootBaseTest {


    private Source xslDoc = new StreamSource("src/main/resources/xsl/siri_soap_raw.xsl");

    @Test
    public void stopPointsDiscoveryTest() throws IOException, TransformerException, JAXBException, XMLStreamException {

        TransformerFactory tFactory = TransformerFactory.newInstance();

        Source xmlDoc = new StreamSource("src/test/resources/discoveryTest/stop_points_soap_to_xml_test.xml");
        String outputFileName = "src/test/resources/discoveryTest/results/resultSP.xml";

        try {

            OutputStream htmlFile = new FileOutputStream(outputFileName);
            Transformer transform = tFactory.newTransformer(xslDoc);
            transform.transform(xmlDoc, new StreamResult(htmlFile));

            // XML file generated. Now trying to read it
            File file = new File(outputFileName);
            Siri incoming = SiriValueTransformer.parseXml(new ByteArrayInputStream(FileUtils.readFileToByteArray(file)));

            assertNotNull(incoming.getStopPointsRequest());
            file.delete();


        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    @Test
    public void cgeckStatusTest() throws JAXBException, FileNotFoundException, TransformerException, XMLStreamException {
        String receivedCheckstatus = """
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                    <soap:Header/>
                    <soap:Body>
                        <ns1:CheckStatusResponse xmlns:ns1="http://wsdl.siri.org.uk">
                            <CheckStatusAnswerInfo xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt"
                                                   xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0"
                                                   xmlns:ns5="http://www.siri.org.uk/siri" xmlns:ns6="http://wsdl.siri.org.uk/siri"
                                                   xmlns:ns7="http://www.divia.fr/mcos" xmlns:ns8="http://qommute.siri.org.uk/siri"
                                                   xmlns:ns9="http://www.rtm.fr/hub">
                                <ns5:ResponseTimestamp>2025-09-25T10:26:28.243+02:00</ns5:ResponseTimestamp>
                                <ns5:ProducerRef>TCAR</ns5:ProducerRef>
                                <ns5:ResponseMessageIdentifier>TCAR:ResponseMessage::32e1cdfc-06dd-453c-9ac5-120ffd3481a1:LOC
                                </ns5:ResponseMessageIdentifier>
                                <ns5:RequestMessageRef>0691bac4-e34c-4f2d-b9a4-f614b1ddbcb6</ns5:RequestMessageRef>
                            </CheckStatusAnswerInfo>
                            <Answer xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt"
                                    xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0" xmlns:ns5="http://www.siri.org.uk/siri"
                                    xmlns:ns6="http://wsdl.siri.org.uk/siri" xmlns:ns7="http://www.divia.fr/mcos"
                                    xmlns:ns8="http://qommute.siri.org.uk/siri" xmlns:ns9="http://www.rtm.fr/hub">
                                <ns5:Status>true</ns5:Status>
                                <ns5:ServiceStartedTime>2025-09-25T03:22:07.574+02:00</ns5:ServiceStartedTime>
                            </Answer>
                            <AnswerExtension xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt"
                                             xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0" xmlns:ns5="http://www.siri.org.uk/siri"
                                             xmlns:ns6="http://wsdl.siri.org.uk/siri" xmlns:ns7="http://www.divia.fr/mcos"
                                             xmlns:ns8="http://qommute.siri.org.uk/siri" xmlns:ns9="http://www.rtm.fr/hub"/>
                        </ns1:CheckStatusResponse>
                    </soap:Body>
                </soap:Envelope>
                """;


        String raw = CustomSiriXml.soapToRaw(receivedCheckstatus);
        InputStream inputStream = new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8));

        Siri siriResponse = SiriValueTransformer.parseXml(inputStream);
        assertNotNull(siriResponse.getCheckStatusResponse());
        assertNotNull(siriResponse.getCheckStatusResponse().getServiceStartedTime());
        assertNotNull(siriResponse.getCheckStatusResponse().isStatus());
        assertEquals(true, siriResponse.getCheckStatusResponse().isStatus());
    }


    @Test
    public void linesDiscoveryTest() throws IOException, TransformerException, JAXBException, XMLStreamException {

        TransformerFactory tFactory = TransformerFactory.newInstance();
        Source xmlDoc = new StreamSource("src/test/resources/discoveryTest/lines_soap_to_xml_test.xml");

        String outputFileName = "src/test/resources/discoveryTest/results/resultLines.xml";

        try {

            OutputStream htmlFile = new FileOutputStream(outputFileName);
            Transformer transform = tFactory.newTransformer(xslDoc);
            transform.transform(xmlDoc, new StreamResult(htmlFile));

            // XML file generated. Now trying to read it
            File file = new File(outputFileName);
            Siri incoming = SiriValueTransformer.parseXml(new ByteArrayInputStream(FileUtils.readFileToByteArray(file)));

            assertNotNull(incoming.getLinesRequest());
            file.delete();

        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }
}
