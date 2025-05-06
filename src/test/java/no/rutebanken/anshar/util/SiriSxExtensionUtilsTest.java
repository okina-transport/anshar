package no.rutebanken.anshar.util;

import no.rutebanken.anshar.data.util.CustomSiriXml;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import uk.org.siri.siri21.PtSituationElement;
import uk.org.siri.siri21.Siri;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.TimeZone;

import static no.rutebanken.anshar.util.SiriSxExtensionUtils.DATE_NODES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

class SiriSxExtensionUtilsTest {

    private Siri siri;

    @BeforeEach
    void setUp() {
        try {
            siri = CustomSiriXml.parseXml(Files.readString(Path.of(
                    "src/test/resources/consistency/anshar_cache_siri_sx.xml"
            )));
        } catch (Exception e) {
            fail("Unable to parse input test file");
        }
    }

    @ParameterizedTest
    @CsvSource({
            "Europe/Paris,2025-03-18T18:10:40+01:00",
            "UTC,2025-03-18T17:10:40Z",
            "America/New_York,2025-03-18T13:10:40-04:00",
    })
    void transformSiriSxDateInExtensionsFormatEuropeParis(String timezone, String expectedDate) {
        TimeZone newTimeZone = TimeZone.getTimeZone(timezone);
        TimeZone.setDefault(newTimeZone);
        PtSituationElement ptSituationElement = siri.getServiceDelivery().getSituationExchangeDeliveries().get(0).getSituations().getPtSituationElements().get(0);

        SiriSxExtensionUtils.transformDateInExtensionsNode(ptSituationElement.getExtensions().getAnies());

        validateDateFormat(ptSituationElement, expectedDate);
    }

    private static void validateDateFormat(PtSituationElement ptSituationElement, String expectedDate) {
        for (Node currentNode : ptSituationElement.getExtensions().getAnies()) {
            Deque<Node> stack = new ArrayDeque<>();
            stack.push(currentNode);

            while (!stack.isEmpty()) {
                Node node = stack.pop();
                if (node.getNodeType() == Node.ELEMENT_NODE && DATE_NODES.contains(node.getNodeName())) {
                    assertThat(node.getTextContent()).isEqualTo(expectedDate);
                }

                NodeList childNodes = node.getChildNodes();
                for (int i = childNodes.getLength() - 1; i >= 0; i--) {
                    stack.push(childNodes.item(i));
                }
            }
        }
    }

}