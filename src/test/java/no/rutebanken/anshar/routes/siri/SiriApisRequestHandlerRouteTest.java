package no.rutebanken.anshar.routes.siri;

import no.rutebanken.anshar.data.EstimatedTimetables;
import no.rutebanken.anshar.data.MonitoredStopVisits;
import no.rutebanken.anshar.data.Situations;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;

class SiriApisRequestHandlerRouteTest extends SpringBootBaseTest {

    @Autowired
    private Situations situations;

    @Autowired
    private MonitoredStopVisits stopVisits;

    @Autowired
    private EstimatedTimetables estimatedTimetables;

    @Autowired
    private SiriApisRequestHandlerRoute siriApisRequestHandlerRoute;

    @Test
    /**
     * Test SIRI Stop Monitoring
     */
    public void testMultitudSmCompliance() {
        File file = new File("src/test/resources/PT_RT_STOPTIME_SYTRAL_siri-sm_dynamic.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sm", file, file.getPath(), "SYTRAL");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }
        //assertFalse(stopVisits.getAll().isEmpty());
    }

    @Test
    /**
     * Test SIRI Situation Exchange
     */
    public void testMultitudSxCompliance() {
        File file = new File("src/test/resources/PT_EVENT_TCL_siri-sx_dynamic.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-sx", file, file.getPath(), "SYTRAL");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }
        //assertFalse(situations.getAll().isEmpty());
    }

    @Test
    /**
     * Test Siri Estimated Timetable
     */
    public void testMultitudEtCompliance() {
        File file = new File("src/test/resources/PT_RT_STOPTIME_SYTRAL_siri-et_dynamic.zip");
        try {
            siriApisRequestHandlerRoute.createSubscriptionsFromFile("siri-et", file, file.getPath(), "SYTRAL");
        } catch (IOException | SAXException | ParserConfigurationException | XMLStreamException e) {
            e.printStackTrace();
        }
       // assertFalse(estimatedTimetables.getAll().isEmpty());
    }
}