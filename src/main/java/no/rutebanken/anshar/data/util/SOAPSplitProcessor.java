package no.rutebanken.anshar.data.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Processor that split an exchange that contains multiple SOAP messages.
 * First message will continue the route.
 * Other messages will be redirected to enqueue.message to be processed by another thread
 */
@Component
@Slf4j
public class SOAPSplitProcessor implements Processor {


    @Produce(value = "direct:enqueue.message")
    protected ProducerTemplate enqueueMessageProducer;


    @Override
    public void process(Exchange exchange) throws Exception {

        String body = exchange.getIn().getBody(String.class);
        List<String> splittedSoaps = splitSoapMessages(body);
        if (splittedSoaps.size() > 1) {
            log.warn("Found more than one soap message, spliting");
            // first SOAP msg is kept into this route
            exchange.getIn().setBody(splittedSoaps.get(0));

            // others are re-scheduled for later
            rescheduleOtherMsgs(splittedSoaps, exchange.getIn().getHeaders());

        }
    }

    private void rescheduleOtherMsgs(List<String> splittedSoaps, Map<String, Object> headers) {

        String firstMessage = splittedSoaps.get(0);

        // first msg is skipped because it will be processed in the current thread
        for (int i = 1; i < splittedSoaps.size(); i++) {
            String currentMsg = splittedSoaps.get(i);
            if (!firstMessage.equals(currentMsg)) {
                enqueueMessageProducer.asyncRequestBodyAndHeaders(enqueueMessageProducer.getDefaultEndpoint(), currentMsg, headers);
            }

        }
    }

    public static List<String> splitSoapMessages(String input) {
        List<String> messages = new ArrayList<>();

        String regex = "<\\w+:Envelope.*?</\\w+:Envelope>";
        Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(input);

        while (matcher.find()) {
            messages.add(matcher.group(0));
        }

        return messages;
    }
}
