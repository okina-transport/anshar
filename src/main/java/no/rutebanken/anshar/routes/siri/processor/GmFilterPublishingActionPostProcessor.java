package no.rutebanken.anshar.routes.siri.processor;

import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.data.frGeneralMessageStructure.Content;
import no.rutebanken.anshar.data.frGeneralMessageStructure.Message;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import uk.org.siri.siri21.GeneralMessage;
import uk.org.siri.siri21.GeneralMessageDeliveryStructure;
import uk.org.siri.siri21.Siri;

import java.util.ArrayList;
import java.util.List;


@Slf4j
public class GmFilterPublishingActionPostProcessor extends ValueAdapter implements PostProcessor {


    private final String publishingActionName;

    public GmFilterPublishingActionPostProcessor(String publishingActionName) {
        this.publishingActionName = publishingActionName;
    }


    @Override
    public void process(Siri siri) {
        List<GeneralMessageDeliveryStructure> gmdss = siri.getServiceDelivery().getGeneralMessageDeliveries();
        for (GeneralMessageDeliveryStructure gmds : gmdss) {
            List<GeneralMessage> gms = gmds.getGeneralMessages();
            List<GeneralMessage> filteredGM = new ArrayList<>();
            for (GeneralMessage gm : gms) {

                if (gm.getContent() instanceof Content content && content.getPublishingActions().containsKey(publishingActionName)) {
                    for (Message message : content.getMessages()) {
                        message.setMsgText(content.getPublishingActions().get(publishingActionName));
                    }
                    filteredGM.add(gm);
                }
            }
            gmds.getGeneralMessages().clear();
            gmds.getGeneralMessages().addAll(filteredGM);
        }
    }


    @Override
    protected String apply(String value) {
        return "";
    }
}

