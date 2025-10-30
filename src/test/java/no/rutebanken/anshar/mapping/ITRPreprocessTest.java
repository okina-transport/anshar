package no.rutebanken.anshar.mapping;

import no.rutebanken.anshar.routes.siri.processor.SXPlannedFeedingProcessor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.PtSituationElement;
import uk.org.siri.siri21.ServiceDelivery;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.SituationExchangeDeliveryStructure;

public class ITRPreprocessTest {

    @Test
    public void test_no_error_on_empty_siri() {
        SXPlannedFeedingProcessor processor = new SXPlannedFeedingProcessor();
        Siri siri = new Siri();
        processor.process(siri);
    }

    @Test
    public void test_process_no_keyword() {
        SXPlannedFeedingProcessor processor = new SXPlannedFeedingProcessor();
        Siri siri = new Siri();
        ServiceDelivery serviceDelivery = new ServiceDelivery();
        SituationExchangeDeliveryStructure structure = new SituationExchangeDeliveryStructure();
        SituationExchangeDeliveryStructure.Situations situations = new SituationExchangeDeliveryStructure.Situations();
        PtSituationElement sit1 = new PtSituationElement();
        sit1.getKeywords().add("test");
        situations.getPtSituationElements().add(sit1);
        structure.setSituations(situations);
        serviceDelivery.getSituationExchangeDeliveries().add(structure);
        siri.setServiceDelivery(serviceDelivery);
        processor.process(siri);
        Assertions.assertEquals(true, siri.getServiceDelivery().getSituationExchangeDeliveries().get(0).getSituations().getPtSituationElements().get(0).isPlanned());
    }

    @Test
    public void test_process_planned() {
        SXPlannedFeedingProcessor processor = new SXPlannedFeedingProcessor();
        Siri siri = new Siri();
        ServiceDelivery serviceDelivery = new ServiceDelivery();
        SituationExchangeDeliveryStructure structure = new SituationExchangeDeliveryStructure();
        SituationExchangeDeliveryStructure.Situations situations = new SituationExchangeDeliveryStructure.Situations();
        PtSituationElement sit1 = new PtSituationElement();
        sit1.getKeywords().add("Prévue");
        situations.getPtSituationElements().add(sit1);
        structure.setSituations(situations);
        serviceDelivery.getSituationExchangeDeliveries().add(structure);
        siri.setServiceDelivery(serviceDelivery);
        processor.process(siri);
        Assertions.assertEquals(true, siri.getServiceDelivery().getSituationExchangeDeliveries().get(0).getSituations().getPtSituationElements().get(0).isPlanned());
    }

    @Test
    public void test_process_unPlanned() {
        SXPlannedFeedingProcessor processor = new SXPlannedFeedingProcessor();
        Siri siri = new Siri();
        ServiceDelivery serviceDelivery = new ServiceDelivery();
        SituationExchangeDeliveryStructure structure = new SituationExchangeDeliveryStructure();
        SituationExchangeDeliveryStructure.Situations situations = new SituationExchangeDeliveryStructure.Situations();
        PtSituationElement sit1 = new PtSituationElement();
        sit1.getKeywords().add("Inopinée");
        situations.getPtSituationElements().add(sit1);
        structure.setSituations(situations);
        serviceDelivery.getSituationExchangeDeliveries().add(structure);
        siri.setServiceDelivery(serviceDelivery);
        processor.process(siri);
        Assertions.assertEquals(false, siri.getServiceDelivery().getSituationExchangeDeliveries().get(0).getSituations().getPtSituationElements().get(0).isPlanned());
    }


}
