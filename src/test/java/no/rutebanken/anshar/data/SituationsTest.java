/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package no.rutebanken.anshar.data;

import no.rutebanken.anshar.api.GtfsRTApi;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.config.IncomingSiriParameters;
import no.rutebanken.anshar.helpers.TestObjectFactory;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.mapping.LineUpdaterService;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri20.*;

import javax.xml.bind.UnmarshalException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.*;

import static no.rutebanken.anshar.helpers.SleepUtil.sleep;
import static org.junit.jupiter.api.Assertions.*;

public class SituationsTest extends SpringBootBaseTest {

    @Autowired
    private Situations situations;

    @Autowired
    private SubscriptionConfig subscriptionConfig;

    @Autowired
    private AnsharConfiguration configuration;

    @Autowired
    private LineUpdaterService lineupdaterService;

    @Autowired
    private SiriHandler handler;

    @BeforeEach
    public void init() {
        situations.clearAll();
    }


    @Test
    public void testAddSituation() {
        int previousSize = situations.getAll().size();
        PtSituationElement element = TestObjectFactory.createPtSituationElement("atb", "1234", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(4));

        situations.add("test", element);

        assertEquals(previousSize + 1, situations.getAll().size(), "Situation not added");
    }

    @Test
    public void testRemoveSituation() throws InterruptedException {

        configuration.setSxGraceperiodMinutes(0);
        int initialSize = situations.getAll().size();
        PtSituationElement element = TestObjectFactory.createPtSituationElement("atb", "1234", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusMinutes(1));

        situations.add("test", element);
        assertEquals(initialSize + 1, situations.getAll().size(), "Situation not added");

        // On attend une minute pour que la perturbation soit périmée .Normalement, elle doit être supprimée du cache
        Thread.sleep(70 * 1000);


        assertEquals(initialSize, situations.getAll().size(), "Situation not removed after expiration");

    }

    @Test
    public void testDraftSituationIgnored() {
        int previousSize = situations.getAll().size();
        PtSituationElement element = TestObjectFactory.createPtSituationElement("tst", "43123", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(4));

        element.setProgress(WorkflowStatusEnumeration.DRAFT);

        situations.add("test", element);

        assertEquals(previousSize, situations.getAll().size(), "Draft-situation added");
    }

    @Test
    public void testAddNullSituation() {
        int previousSize = situations.getAll().size();
        situations.add("test", null);

        assertEquals(previousSize, situations.getAll().size(), "Null-situation added");
    }

    @Test
    public void test_add__when_adding_situation_with_same_datasetid_and_participantref_and_situation_number__should_not_add_situations() {
        PtSituationElement original = TestObjectFactory.createPtSituationElement("ruter", "1234", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1));
        PtSituationElement update = TestObjectFactory.createPtSituationElement("ruter", "1234", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1));

        situations.add("test", original);
        situations.add("test", update);

        assertEquals(1, situations.getAll().size(), "should not have duplicate situation");
    }

    @Test
    public void test_add__when_adding_situation_with_different_datasetid__should_add_situations() {
        PtSituationElement original = TestObjectFactory.createPtSituationElement("ruter", "1234", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1));
        PtSituationElement originalCopy = TestObjectFactory.createPtSituationElement("ruter", "1234", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1));

        situations.add("test", original);
        situations.add("test2", originalCopy);

        assertEquals(2, situations.getAll().size(), "should have 2 situations");
    }

    @Test
    public void test_add__when_adding_situation_with_same_datasetid_and_different_participantref_and_same_situation_number__should_not_add_situations() {
        PtSituationElement original = TestObjectFactory.createPtSituationElement("ruter", "1234", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1));
        PtSituationElement originalWithDifferentParticipantRef = TestObjectFactory.createPtSituationElement("mobiiti", "1234", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1));

        situations.add("test", original);
        situations.add("test", originalWithDifferentParticipantRef);

        assertEquals(1, situations.getAll().size(), "should not have duplicate situation");
    }

    @Test
    public void testGetUpdatesOnly() {

        int previousSize = situations.getAll().size();

        String prefix = "updates-";
        situations.add("test", TestObjectFactory.createPtSituationElement("ruter", prefix + "1234", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1)));
        situations.add("test", TestObjectFactory.createPtSituationElement("ruter", prefix + "2345", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1)));
        situations.add("test", TestObjectFactory.createPtSituationElement("ruter", prefix + "3456", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1)));

        sleep(50);

        // Added 3
        assertEquals(previousSize + 3, situations.getAllUpdates("1234-1234", null).size());

        situations.add("test", TestObjectFactory.createPtSituationElement("ruter", prefix + "4567", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1)));

        sleep(50);

        //Added one
        assertEquals(1, situations.getAllUpdates("1234-1234", null).size());
        sleep(50);

        //None added
        assertEquals(0, situations.getAllUpdates("1234-1234", null).size());
        sleep(50);
        //Verify that all elements still exist
        assertEquals(previousSize + 4, situations.getAll().size());
    }

    // @Test
//    public void testGetUpdatesOnlyFromCache() {
//
//        int previousSize = situations.getAll().size();
//
//        String prefix = "cache-updates-sx-";
//        String datasetId = "cache-sx-datasetid";
//
//        situations.add(datasetId, TestObjectFactory.createPtSituationElement("ruter", prefix + "1234", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1)));
//        situations.add(datasetId, TestObjectFactory.createPtSituationElement("ruter", prefix + "2345", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1)));
//        situations.add(datasetId, TestObjectFactory.createPtSituationElement("ruter", prefix + "3456", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1)));
//
//        sleep(50);
//        // Added 3
//        assertEquals(previousSize + 3, situations.getAllCachedUpdates("1234-1234-cache", datasetId,
//                null
//        ).size());
//
//        situations.add(datasetId, TestObjectFactory.createPtSituationElement("ruter", prefix + "4567", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1)));
//
//        sleep(50);
//
//        //Added one
//        assertEquals(1, situations.getAllCachedUpdates("1234-1234-cache", datasetId, null).size());
//        sleep(50);
//
//        //None added
//        assertEquals(0, situations.getAllCachedUpdates("1234-1234-cache", datasetId, null).size());
//        sleep(50);
//        //Verify that all elements still exist
//        assertEquals(previousSize + 4, situations.getAll().size());
//    }

    @Test
    public void testFlexibleLineConversion() throws UnmarshalException {
        String flexibleLineId = "PROV1:Line:35";
        String standardlineId = "PROV2:Line:AAA";

        List<GtfsRTApi> gtfsApis = new ArrayList<>();
        GtfsRTApi api1 = new GtfsRTApi();
        api1.setDatasetId("PROV1");
        GtfsRTApi api2 = new GtfsRTApi();
        api2.setDatasetId("PROV2");
        gtfsApis.add(api1);
        gtfsApis.add(api2);

        subscriptionConfig.setGtfsRTApis(gtfsApis);

        Map<String, Boolean> flexibleLineMap = new HashMap<>();
        flexibleLineMap.put(flexibleLineId, true);
        flexibleLineMap.put(standardlineId, false);
        lineupdaterService.addFlexibleLines(flexibleLineMap);

        String datasetId = "DATASET1";
        String prefix = "cache-updates-sx-";
        PtSituationElement sx1 = TestObjectFactory.createPtSituationElement("ruter", prefix + "1234", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1));
        addLineRef(sx1, flexibleLineId);

        PtSituationElement sx2 = TestObjectFactory.createPtSituationElement("ruter", prefix + "2345", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1));
        addLineRef(sx2, standardlineId);

        situations.add(datasetId, sx1);
        situations.add(datasetId, sx2);

        Collection<PtSituationElement> sampleSit = situations.getAll();
        assertFalse(sampleSit.isEmpty());

        String stringXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Siri xmlns=\"http://www.siri.org.uk/siri\" xmlns:ns2=\"http://www.ifopt.org.uk/acsb\" xmlns:ns3=\"http://www.ifopt.org.uk/ifopt\" xmlns:ns4=\"http://datex2.eu/schema/2_0RC1/2_0\" version=\"2.0\">\n" +
                "    <ServiceRequest>\n" +
                "        <RequestorRef>#RequestorREF#12EFS1aaa-2</RequestorRef>\n" +
                "        <SituationExchangeRequest version=\"2.0\">\n" +
                "        </SituationExchangeRequest>\n" +
                "    </ServiceRequest>\n" +
                "</Siri>";

        InputStream xml = IOUtils.toInputStream(stringXml, StandardCharsets.UTF_8);

        IncomingSiriParameters params = new IncomingSiriParameters();
        params.setIncomingSiriStream(xml);
        params.setDatasetId("DATASET1");
        params.setOutboundIdMappingPolicy(SiriHandler.getIdMappingPolicy("true", "false"));
        params.setMaxSize(-1);

        Siri response = handler.handleIncomingSiri(params);


        assertNotNull(response.getServiceDelivery());
        assertNotNull(response.getServiceDelivery().getSituationExchangeDeliveries());
        assertFalse(response.getServiceDelivery().getSituationExchangeDeliveries().isEmpty());
        SituationExchangeDeliveryStructure del = response.getServiceDelivery().getSituationExchangeDeliveries().get(0);

        assertNotNull(del.getSituations());
        assertFalse(del.getSituations().getPtSituationElements().isEmpty());

        for (PtSituationElement ptSituationElement : del.getSituations().getPtSituationElements()) {

            assertNotNull(ptSituationElement.getAffects());
            assertNotNull(ptSituationElement.getAffects().getNetworks());
            assertNotNull(ptSituationElement.getAffects().getNetworks().getAffectedNetworks());
            assertFalse(ptSituationElement.getAffects().getNetworks().getAffectedNetworks().isEmpty());

            AffectsScopeStructure.Networks.AffectedNetwork affectNet = ptSituationElement.getAffects().getNetworks().getAffectedNetworks().get(0);

            assertNotNull(affectNet.getAffectedLines());
            assertFalse(affectNet.getAffectedLines().isEmpty());

            AffectedLineStructure affectedLine = affectNet.getAffectedLines().get(0);

            assertNotNull(affectedLine.getLineRef());
            String lineId = affectedLine.getLineRef().getValue();
            if (lineId.startsWith("PROV1")) {
                assertEquals("PROV1:FlexibleLine:35", lineId);
            } else {
                assertEquals(standardlineId, lineId);
            }
        }
    }

    private void addLineRef(PtSituationElement sx, String lineId) {
        AffectsScopeStructure affectedScope = new AffectsScopeStructure();
        AffectsScopeStructure.Networks networks = new AffectsScopeStructure.Networks();
        AffectsScopeStructure.Networks.AffectedNetwork affectNet = new AffectsScopeStructure.Networks.AffectedNetwork();

        AffectedLineStructure affectedLineStructure = new AffectedLineStructure();
        LineRef lineRef = new LineRef();
        lineRef.setValue(lineId);
        affectedLineStructure.setLineRef(lineRef);
        affectNet.getAffectedLines().add(affectedLineStructure);
        networks.getAffectedNetworks().add(affectNet);
        affectedScope.setNetworks(networks);
        sx.setAffects(affectedScope);

    }


}
