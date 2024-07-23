package no.rutebanken.anshar.XSLTests;

import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.Siri;

import javax.xml.bind.JAXBException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;

import static junit.framework.TestCase.assertNotNull;

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
