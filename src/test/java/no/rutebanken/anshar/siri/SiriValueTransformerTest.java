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

package no.rutebanken.anshar.siri;

import jakarta.xml.bind.JAXBException;
import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.helpers.MappingAdapterPresets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SiriValueTransformerTest extends SpringBootBaseTest {


    @BeforeEach
    public void clearCachedGetters() {
        SiriValueTransformer.clearCachedGettersForAdapter();
    }


    @Test
    public void testForNullAdapters() throws JAXBException {
        String lineRefValue = "99";
        String blockRefValue = "34";

        Siri siri = createSiriObject(lineRefValue, blockRefValue);

        siri = SiriValueTransformer.transform(siri, null);
        assertEquals(lineRefValue, getLineRefFromSiriObj(siri), "LineRef should not be altered");
        assertEquals(blockRefValue, getBlockRefFromSiriObj(siri), "BlockRef should not be altered");

        assertNotNull(siri);
    }


    @Test
    public void testOkinaMappingAdapters() throws JAXBException {
        String lineRefValue = "OLDLINEPREF::123:4:OLDLINESUFF";
        String blockRefValue = "";
        String mappedLineRefValue = "TEST:Line:012304";
        String stopRefValue = "OLDPREFIX:Stop:1234:SUFFIXTOREMOVE";


        Siri siri = createSiriObject(lineRefValue, blockRefValue, stopRefValue, stopRefValue);

        assertEquals(stopRefValue, getOriginFromSiriObj(siri));
        assertEquals(stopRefValue, getDestinationfFromSiriObj(siri));
        assertEquals(lineRefValue, getLineRefFromSiriObj(siri));


//
//        List<ValueAdapter> mappingAdapters = new ArrayList<>();
//        mappingAdapters.add(new RuterSubstringAdapter(LineRef.class, ':', '0', 2));
//        mappingAdapters.add(new LeftPaddingAdapter(LineRef.class, 6, '0'));
//        SubscriptionSetup subscriptionSetup = new SubscriptionSetup();
//        subscriptionSetup.setDatasetId("TEST");
//        subscriptionSetup.setSubscriptionType(SiriDataType.ESTIMATED_TIMETABLE);
//
//        mappingAdapters.addAll(new NsrValueAdapters().createIdPrefixAdapters(subscriptionSetup));
//
//        siri = SiriValueTransformer.transform(siri, mappingAdapters);


        IdProcessingParameters stopIddProcessingParameters = new IdProcessingParameters();
        stopIddProcessingParameters.setInputPrefixToRemove("OLDPREFIX:Stop:");
        stopIddProcessingParameters.setInputSuffixToRemove(":SUFFIXTOREMOVE");
        stopIddProcessingParameters.setOutputPrefixToAdd("NEWPREFFIX:");
        stopIddProcessingParameters.setOutputSuffixToAdd(":NEWSUFF");

        Optional<IdProcessingParameters> stopIdProcessingParametersOpt = Optional.of(stopIddProcessingParameters);


        IdProcessingParameters lineIddProcessingParameters = new IdProcessingParameters();
        lineIddProcessingParameters.setInputPrefixToRemove("OLDLINEPREF::");
        lineIddProcessingParameters.setInputSuffixToRemove(":OLDLINESUFF");
        lineIddProcessingParameters.setOutputPrefixToAdd("NEWLINEPREFFIX:");
        lineIddProcessingParameters.setOutputSuffixToAdd(":NEWLINESUFF");

        Optional<IdProcessingParameters> lineIdProcessingParametersOpt = Optional.of(lineIddProcessingParameters);

        Map<ObjectType, Optional<IdProcessingParameters>> idMap = new HashMap<>();
        idMap.put(ObjectType.STOP, stopIdProcessingParametersOpt);
        idMap.put(ObjectType.LINE, lineIdProcessingParametersOpt);


        List<ValueAdapter> adapters = MappingAdapterPresets.getOutboundAdapters(SiriDataType.STOP_MONITORING, OutboundIdMappingPolicy.DEFAULT, idMap);
        Siri transformedSiri = SiriValueTransformer.transform(siri, adapters);


        assertEquals("NEWPREFFIX:1234:NEWSUFF", getOriginFromSiriObj(transformedSiri));
        assertEquals("NEWPREFFIX:1234:NEWSUFF", getDestinationfFromSiriObj(transformedSiri));
        assertEquals("NEWLINEPREFFIX:123##3A##4:NEWLINESUFF", getLineRefFromSiriObj(transformedSiri));


    }


    /**
     * DATA-2114: some producers (e.g. Traffic Report / Chaos) send a bare {@code lang="xx"}
     * attribute on Summary/Description/Prompt instead of the standards-compliant
     * {@code xml:lang="xx"}. JAXB only binds the latter, so without normalization the language
     * is silently lost when parsing. Extensions content (raw/untyped) must stay untouched.
     */
    @Test
    public void testBareLangAttributeIsPreservedWhenParsingIncomingXml() throws Exception {
        String xml = """
                <Siri xmlns="http://www.siri.org.uk/siri" version="2.1">
                    <ServiceDelivery>
                        <ResponseTimestamp>2026-08-18T15:58:27+02:00</ResponseTimestamp>
                        <ProducerRef>TEST</ProducerRef>
                        <SituationExchangeDelivery>
                            <ResponseTimestamp>2026-08-18T15:58:27+02:00</ResponseTimestamp>
                            <Situations>
                                <PtSituationElement>
                                    <CreationTime>2026-08-18T15:58:27+02:00</CreationTime>
                                    <ParticipantRef>TEST</ParticipantRef>
                                    <SituationNumber>test-1</SituationNumber>
                                    <Source><SourceType>other</SourceType></Source>
                                    <Progress>open</Progress>
                                    <ValidityPeriod><StartTime>2026-08-18T15:56:00+02:00</StartTime></ValidityPeriod>
                                    <Severity>normal</Severity>
                                    <Summary lang="fr">test message de ligne</Summary>
                                    <Summary lang="en">Line message test</Summary>
                                    <Description lang="fr">&lt;p&gt;test de message&lt;/p&gt;</Description>
                                    <Description lang="es">&lt;p&gt;Prueba de mensajes&lt;/p&gt;</Description>
                                    <PublishingActions>
                                        <PublishToWebAction>
                                            <ActionData>
                                                <Name>SiteWeb</Name>
                                                <Prompt lang="fr">&lt;p&gt;test de message&lt;/p&gt;</Prompt>
                                                <Prompt lang="en">&lt;p&gt;Message test&lt;/p&gt;</Prompt>
                                            </ActionData>
                                        </PublishToWebAction>
                                    </PublishingActions>
                                    <Extensions>
                                        <Alerts>
                                            <AlertMessages>
                                                <AlertMessage>
                                                    <ChannelName>Site web</ChannelName>
                                                    <MessageText lang="fr">test</MessageText>
                                                </AlertMessage>
                                            </AlertMessages>
                                        </Alerts>
                                    </Extensions>
                                </PtSituationElement>
                            </Situations>
                        </SituationExchangeDelivery>
                    </ServiceDelivery>
                </Siri>
                """;

        Siri siri = SiriValueTransformer.parseXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        PtSituationElement situation = siri.getServiceDelivery().getSituationExchangeDeliveries().get(0)
                .getSituations().getPtSituationElements().get(0);

        List<DefaultedTextStructure> summaries = situation.getSummaries();
        assertEquals("fr", summaries.get(0).getLang());
        assertEquals("en", summaries.get(1).getLang());

        List<DefaultedTextStructure> descriptions = situation.getDescriptions();
        assertEquals("fr", descriptions.get(0).getLang());
        assertEquals("es", descriptions.get(1).getLang());

        List<NaturalLanguageStringStructure> prompts = situation.getPublishingActions()
                .getPublishToWebActions().get(0).getActionDatas().get(0).getPrompts();
        assertEquals("fr", prompts.get(0).getLang());
        assertEquals("en", prompts.get(1).getLang());
    }

    @Test
    public void testProperlyNamespacedLangAttributeIsStillParsedCorrectly() throws Exception {
        String xml = """
                <Siri xmlns="http://www.siri.org.uk/siri" xmlns:xml="http://www.w3.org/XML/1998/namespace" version="2.1">
                    <ServiceDelivery>
                        <ResponseTimestamp>2026-08-18T15:58:27+02:00</ResponseTimestamp>
                        <ProducerRef>TEST</ProducerRef>
                        <SituationExchangeDelivery>
                            <ResponseTimestamp>2026-08-18T15:58:27+02:00</ResponseTimestamp>
                            <Situations>
                                <PtSituationElement>
                                    <CreationTime>2026-08-18T15:58:27+02:00</CreationTime>
                                    <ParticipantRef>TEST</ParticipantRef>
                                    <SituationNumber>test-2</SituationNumber>
                                    <Source><SourceType>other</SourceType></Source>
                                    <Progress>open</Progress>
                                    <ValidityPeriod><StartTime>2026-08-18T15:56:00+02:00</StartTime></ValidityPeriod>
                                    <Severity>normal</Severity>
                                    <Summary xml:lang="fr">message correctement préfixé</Summary>
                                </PtSituationElement>
                            </Situations>
                        </SituationExchangeDelivery>
                    </ServiceDelivery>
                </Siri>
                """;

        Siri siri = SiriValueTransformer.parseXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        PtSituationElement situation = siri.getServiceDelivery().getSituationExchangeDeliveries().get(0)
                .getSituations().getPtSituationElements().get(0);

        assertEquals("fr", situation.getSummaries().get(0).getLang());
    }

    @Test
    public void testUnrelatedBareLangAttributeIsNotAffected() throws Exception {
        // "lang" on an element other than Summary/Description/Prompt must not be touched
        String xml = """
                <Siri xmlns="http://www.siri.org.uk/siri" version="2.1">
                    <ServiceDelivery>
                        <ResponseTimestamp>2026-08-18T15:58:27+02:00</ResponseTimestamp>
                        <ProducerRef>TEST</ProducerRef>
                        <SituationExchangeDelivery>
                            <ResponseTimestamp>2026-08-18T15:58:27+02:00</ResponseTimestamp>
                            <Situations>
                                <PtSituationElement>
                                    <CreationTime>2026-08-18T15:58:27+02:00</CreationTime>
                                    <ParticipantRef>TEST</ParticipantRef>
                                    <SituationNumber>test-3</SituationNumber>
                                    <Source><SourceType>other</SourceType></Source>
                                    <Progress>open</Progress>
                                    <ValidityPeriod><StartTime>2026-08-18T15:56:00+02:00</StartTime></ValidityPeriod>
                                    <Severity>normal</Severity>
                                    <Extensions>
                                        <Alerts>
                                            <AlertMessages>
                                                <AlertMessage>
                                                    <ChannelName>Site web</ChannelName>
                                                    <MessageText lang="fr">test</MessageText>
                                                </AlertMessage>
                                            </AlertMessages>
                                        </Alerts>
                                    </Extensions>
                                </PtSituationElement>
                            </Situations>
                        </SituationExchangeDelivery>
                    </ServiceDelivery>
                </Siri>
                """;

        Siri siri = SiriValueTransformer.parseXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        PtSituationElement situation = siri.getServiceDelivery().getSituationExchangeDeliveries().get(0)
                .getSituations().getPtSituationElements().get(0);

        assertNotNull(situation.getExtensions());
    }


    private Siri createSiriObject(String lineRefValue, String blockRefValue) {
        return createSiriObject(lineRefValue, blockRefValue, null, null);
    }


    private Siri createSiriObject(String lineRefValue, String blockRefValue, String originStopPointValue, String destinationStopPointValue) {
        Siri siri = new Siri();
        ServiceDelivery serviceDelivery = new ServiceDelivery();
        EstimatedTimetableDeliveryStructure estimatedTimetableDelivery = new EstimatedTimetableDeliveryStructure();
        EstimatedVersionFrameStructure estimatedJourneyVersionFrame = new EstimatedVersionFrameStructure();
        EstimatedVehicleJourney estimatedVehicleJourney = new EstimatedVehicleJourney();

        if (lineRefValue != null) {
            LineRef lineRef = new LineRef();
            lineRef.setValue(lineRefValue);
            estimatedVehicleJourney.setLineRef(lineRef);
        }

        if (blockRefValue != null) {
            BlockRefStructure blockRef = new BlockRefStructure();
            blockRef.setValue(blockRefValue);
            estimatedVehicleJourney.setBlockRef(blockRef);
        }

        if (originStopPointValue != null) {
            JourneyPlaceRefStructure origin = new JourneyPlaceRefStructure();
            origin.setValue(originStopPointValue);
            estimatedVehicleJourney.setOriginRef(origin);
        }

        if (destinationStopPointValue != null) {
            DestinationRef destination = new DestinationRef();
            destination.setValue(destinationStopPointValue);
            estimatedVehicleJourney.setDestinationRef(destination);
        }


        estimatedJourneyVersionFrame.getEstimatedVehicleJourneies().add(estimatedVehicleJourney);
        estimatedTimetableDelivery.getEstimatedJourneyVersionFrames().add(estimatedJourneyVersionFrame);
        serviceDelivery.getEstimatedTimetableDeliveries().add(estimatedTimetableDelivery);
        siri.setServiceDelivery(serviceDelivery);
        return siri;
    }


    private String getBlockRefFromSiriObj(Siri siri) {
        return siri.getServiceDelivery().getEstimatedTimetableDeliveries().get(0).getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().get(0).getBlockRef().getValue();
    }

    private String getLineRefFromSiriObj(Siri siri) {
        return siri.getServiceDelivery().getEstimatedTimetableDeliveries().get(0).getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().get(0).getLineRef().getValue();
    }

    private String getOriginFromSiriObj(Siri siri) {
        return siri.getServiceDelivery().getEstimatedTimetableDeliveries().get(0).getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().get(0).getOriginRef().getValue();
    }

    private String getDestinationfFromSiriObj(Siri siri) {
        return siri.getServiceDelivery().getEstimatedTimetableDeliveries().get(0).getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().get(0).getDestinationRef().getValue();
    }


    private static String readFile(String path) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(path, "rw");
        byte[] contents = new byte[(int) raf.length()];
        raf.readFully(contents);
        return new String(contents);
    }
}
