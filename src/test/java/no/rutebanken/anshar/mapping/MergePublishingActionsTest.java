package no.rutebanken.anshar.mapping;

import jakarta.xml.bind.JAXBException;

import no.rutebanken.anshar.data.util.CustomSiriXml;
import no.rutebanken.anshar.routes.siri.processor.MergePublishingActionsProcessor;


import org.apache.commons.collections4.CollectionUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.*;

import javax.xml.transform.TransformerException;
import java.io.FileNotFoundException;
import java.util.List;

public class MergePublishingActionsTest {

    @Test
    public void test_no_error_on_empty_siri() {
        MergePublishingActionsProcessor processor = new MergePublishingActionsProcessor();
        Siri siri = new Siri();
        processor.process(siri);
    }


    @Test
    public void test_merge_publishing_actions() throws JAXBException, FileNotFoundException, TransformerException {
        MergePublishingActionsProcessor processor = new MergePublishingActionsProcessor();
        Siri siri = new Siri();
        ServiceDelivery serviceDelivery = new ServiceDelivery();
        SituationExchangeDeliveryStructure structure = new SituationExchangeDeliveryStructure();
        SituationExchangeDeliveryStructure.Situations situations = new SituationExchangeDeliveryStructure.Situations();
        PtSituationElement sit1 = new PtSituationElement();


        ActionsStructure actionStruct = new ActionsStructure();


        // EMB M
        PublishToDisplayAction ptdaEMBM = new PublishToDisplayAction();
        ActionDataStructure adEMBM = new ActionDataStructure();
        adEMBM.setName("LUM_TEST_EMBM");
        adEMBM.setType("Text");
        adEMBM.getValues().add("Message");
        NaturalLanguageStringStructure promptEMBM = new NaturalLanguageStringStructure();
        promptEMBM.setValue("Perturbation à prévoir");
        adEMBM.getPrompts().add(promptEMBM);
        ptdaEMBM.getActionDatas().add(adEMBM);
        actionStruct.getPublishToDisplayActions().add(ptdaEMBM);


        // EMB T
        PublishToDisplayAction ptdaEMBT = new PublishToDisplayAction();
        ActionDataStructure adEMBT = new ActionDataStructure();
        adEMBT.setName("LUM_TEST_EMBT");
        adEMBT.setType("Text");
        adEMBT.getValues().add("Message");
        NaturalLanguageStringStructure promptEMBT = new NaturalLanguageStringStructure();
        promptEMBT.setValue("F2 INCIDENT");
        adEMBT.getPrompts().add(promptEMBT);
        ptdaEMBT.getActionDatas().add(adEMBT);
        actionStruct.getPublishToDisplayActions().add(ptdaEMBT);


        // SOL M
        PublishToDisplayAction ptdaSOLM = new PublishToDisplayAction();
        ActionDataStructure adSOLM = new ActionDataStructure();
        adSOLM.setName("LUM_TEST_SOLM");
        adSOLM.setType("Text");
        adSOLM.getValues().add("Message");
        NaturalLanguageStringStructure promptSOLM = new NaturalLanguageStringStructure();
        promptSOLM.setValue("Ceci est le message d'une perturbation");
        adSOLM.getPrompts().add(promptSOLM);
        ptdaSOLM.getActionDatas().add(adSOLM);
        actionStruct.getPublishToDisplayActions().add(ptdaSOLM);

        // SOL T
        PublishToDisplayAction ptdaSOLT = new PublishToDisplayAction();
        ActionDataStructure adSOLT = new ActionDataStructure();
        adSOLT.setName("LUM_TEST_SOLT");
        adSOLT.setType("Text");
        adSOLT.getValues().add("Message");
        NaturalLanguageStringStructure promptSOLT = new NaturalLanguageStringStructure();
        promptSOLT.setValue("Ceci est le titre d'une perturbation");
        adSOLT.getPrompts().add(promptSOLT);
        ptdaSOLT.getActionDatas().add(adSOLT);
        actionStruct.getPublishToDisplayActions().add(ptdaSOLT);


        sit1.setPublishingActions(actionStruct);
        sit1.getKeywords().add("test");
        situations.getPtSituationElements().add(sit1);
        structure.setSituations(situations);
        serviceDelivery.getSituationExchangeDeliveries().add(structure);
        siri.setServiceDelivery(serviceDelivery);
        processor.process(siri);
        Assertions.assertTrue(siri != null);
        Assertions.assertTrue(siri.getServiceDelivery() != null);
        Assertions.assertTrue(CollectionUtils.isNotEmpty(siri.getServiceDelivery().getSituationExchangeDeliveries()));
        List<SituationExchangeDeliveryStructure> firstDel = siri.getServiceDelivery().getSituationExchangeDeliveries();
        Assertions.assertTrue(firstDel.getFirst().getSituations() != null);
        Assertions.assertTrue(CollectionUtils.isNotEmpty(firstDel.getFirst().getSituations().getPtSituationElements()));
        PtSituationElement situation = firstDel.getFirst().getSituations().getPtSituationElements().getFirst();
        Assertions.assertTrue(situation.getPublishingActions() != null);

        ActionsStructure publishingActions = situation.getPublishingActions();

        // after merge, 4 incoming actions must became 2 results actions
        Assertions.assertEquals(2, publishingActions.getPublishToDisplayActions().size());
        publishingActions.getPublishToDisplayActions().forEach(this::checkPublishToDisplayAction);

        String res = CustomSiriXml.toXml(siri);
        String soapMsg = CustomSiriXml.subscriptionRawToSoap(res);


    }

    private void checkPublishToDisplayAction(PublishToDisplayAction publishToDisplayAction) {


        String name = publishToDisplayAction.getActionDatas().getFirst().getName();
        if ("F2 INCIDENT".equals(name)) {
            Assertions.assertEquals("Perturbation à prévoir", publishToDisplayAction.getActionDatas().getFirst().getPrompts().getFirst().getValue());
            Assertions.assertFalse(publishToDisplayAction.isOnPlace());
            Assertions.assertTrue(publishToDisplayAction.isOnBoard());

        } else if ("Ceci est le titre d'une perturbation".equals(name)) {
            Assertions.assertEquals("Ceci est le message d'une perturbation", publishToDisplayAction.getActionDatas().getFirst().getPrompts().getFirst().getValue());
            Assertions.assertFalse(publishToDisplayAction.isOnBoard());
            Assertions.assertTrue(publishToDisplayAction.isOnPlace());
        } else {
            throw new IllegalArgumentException("Unknown action:" + name);
        }
    }


}
