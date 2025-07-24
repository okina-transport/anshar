package no.rutebanken.anshar.idTests.situationExchange;

import jakarta.xml.bind.JAXBException;
import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.helpers.MappingAdapterPresets;
import org.entur.siri21.util.SiriXml;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.AffectedVehicleJourneyStructure;
import uk.org.siri.siri21.Siri;

import javax.xml.stream.XMLStreamException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static no.rutebanken.anshar.config.ObjectType.VEHICLE_JOURNEY;
import static org.assertj.core.api.Assertions.assertThat;

class SX_id_mapping_tests {

    private static final String SX_WITH_AFFECTED_VEHICLE_JOURNEY = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Siri xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt" xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0" xmlns:ns5="http://www.opengis.net/gml/3.2" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" version="2.1">
                <ServiceDelivery>
                    <ResponseTimestamp>2025-07-24T14:00:58.447317442+02:00</ResponseTimestamp>
                    <ProducerRef>OKI</ProducerRef>
                    <Status>true</Status>
                    <SituationExchangeDelivery version="2.1">
                        <ResponseTimestamp>2025-07-24T14:00:58.447323071+02:00</ResponseTimestamp>
                        <Situations>
                            <PtSituationElement>
                                <CreationTime>2025-07-24T13:59:05.245+02:00</CreationTime>
                                <ParticipantRef>MOBIITI</ParticipantRef>
                                <SituationNumber>data1096_perrine-1</SituationNumber>
                                <Version>1</Version>
                                <Source>
                                    <Name>Mobi-iti disruption service</Name>
                                </Source>
                                <ValidityPeriod>
                                    <StartTime>2025-07-24T13:58:19.346+02:00</StartTime>
                                    <EndTime>2025-07-25T00:00:00+02:00</EndTime>
                                </ValidityPeriod>
                                <MiscellaneousReason>demonstration</MiscellaneousReason>
                                <Severity>verySevere</Severity>
                                <Keywords/>
                                <Summary xml:lang="FR">TEST AF DATA-1120</Summary>
                                <Description xml:lang="FR">TEST AF DATA-1120</Description>
                                <Affects>
                                    <VehicleJourneys>
                                        <AffectedVehicleJourney>
                                            <VehicleJourneyRef>ID:vjref:TEST</VehicleJourneyRef>
                                            <DatedVehicleJourneyRef>ID:dvjref:TEST</DatedVehicleJourneyRef>
                                            <FramedVehicleJourneyRef>
                                                <DatedVehicleJourneyRef>ID:dvjref2:TEST</DatedVehicleJourneyRef>
                                            </FramedVehicleJourneyRef>
                                        </AffectedVehicleJourney>
                                    </VehicleJourneys>
                                </Affects>
                                <Consequences>
                                    <Consequence>
                                        <Condition>noService</Condition>
                                    </Consequence>
                                </Consequences>
                            </PtSituationElement>
                        </Situations>
                    </SituationExchangeDelivery>
                </ServiceDelivery>
            </Siri>
            """;

    @Test
    void test_whenSXMessageHasAffectedVehicleJourney_thenApplyIdProcessingParameters() throws XMLStreamException, JAXBException {
        // Arrange
        IdProcessingParameters ipp = new IdProcessingParameters();
        ipp.setDatasetId("dataset");
        ipp.setObjectType(VEHICLE_JOURNEY);
        ipp.setInputPrefixToRemove("ID:");
        ipp.setInputSuffixToRemove(":TEST");
        ipp.setOutputPrefixToAdd("DATASET:");
        ipp.setOutputSuffixToAdd(":LOC");

        List<ValueAdapter> adapters = MappingAdapterPresets.getOutboundAdapters(SiriDataType.SITUATION_EXCHANGE, OutboundIdMappingPolicy.DEFAULT, Map.of(VEHICLE_JOURNEY, Optional.of(ipp)));

        Siri siri = SiriXml.parseXml(SX_WITH_AFFECTED_VEHICLE_JOURNEY);

        // Act
        Siri output = SiriValueTransformer.transform(siri, adapters);

        // Assert
        AffectedVehicleJourneyStructure affectedVj = output.getServiceDelivery().getSituationExchangeDeliveries().getFirst().getSituations().getPtSituationElements().getFirst().getAffects().getVehicleJourneys().getAffectedVehicleJourneies().getFirst();
        assertThat(affectedVj.getDatedVehicleJourneyReves().getFirst().getValue()).isEqualTo("DATASET:dvjref:LOC");
        assertThat(affectedVj.getFramedVehicleJourneyRef().getDatedVehicleJourneyRef()).isEqualTo("DATASET:dvjref2:LOC");
        assertThat(affectedVj.getVehicleJourneyReves().getFirst().getValue()).isEqualTo("DATASET:vjref:LOC");
    }

}
