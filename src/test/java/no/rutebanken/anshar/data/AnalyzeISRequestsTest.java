package no.rutebanken.anshar.data;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.xml.bind.JAXBException;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import org.apache.commons.lang3.StringUtils;
import uk.org.siri.siri21.Siri;

import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.util.*;

public class AnalyzeISRequestsTest {


    public void readFileAndAnalyze() throws IOException, XMLStreamException, JAXBException, TransformerException {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> map = mapper.readValue(new File("src/test/resources/gravitee_logs.json"), new TypeReference<Map<String, Object>>() {
        });

        Map<String, List<String>> outgoingData = new HashMap<>();
        Map<String, List<String>> incomingData = new HashMap<>();
        Map<String, Integer> incomingStats = new HashMap<>();
        Map<String, Integer> outgoingStats = new HashMap<>();
        List<String> requestedButNotIncoming = new ArrayList<>();
        Integer totalRequestedButNotIncoming = 0;

        List<String> incomingButNeverRequested = new ArrayList<>();
        Integer totalIncomingButNeverRequested = 0;
        List<String> mostlyRequested = new ArrayList<>();
        Integer mostlyRequestedOutgoing = 0;
        Integer mostlyRequestedIncoming = 0;
        List<String> mostlyIncoming = new ArrayList<>();
        Integer mostlyIncomingOutgoing = 0;
        Integer mostlyIncomingIncoming = 0;


        Map<String, Object> hits = (Map<String, Object>) map.get("hits");

        List<Map<String, Object>> subHits = (List<Map<String, Object>>) hits.get("hits");

        for (Map<String, Object> subHit : subHits) {

            Map<String, Object> source = (Map<String, Object>) subHit.get("_source");
            String timeStamp = (String) source.get("@timestamp");
            Map<String, Object> clientReq = (Map<String, Object>) source.get("client-request");
            String uri = (String) clientReq.get("uri");
            String body = (String) clientReq.get("body");

            if (uri != null && uri.contains("incoming")) {
                // Record incoming data
                String monitoringRef = extractPointFromBody(body);
                if (StringUtils.isNotEmpty(monitoringRef) && monitoringRef.contains("SIRI_NVP_037")) {
                    recordIncomingData(incomingData, monitoringRef, timeStamp + "- monitoringRef:" + monitoringRef);
                }

            }


            if (body != null && body.contains("MonitoringRef") && !body.contains("VehicleMonitoringRef") && body.contains("InstantSystem")) {
                // Record outgoing SM data
                String monitoringRef = extractMonitoringRef(body);
                recordResult(outgoingData, monitoringRef, timeStamp + " - monitoringRef:" + monitoringRef);
            }
        }

        for (Map.Entry<String, List<String>> entry : outgoingData.entrySet()) {
            for (String log : entry.getValue()) {
                System.out.println(log);
            }
            outgoingStats.put(entry.getKey(), outgoingData.size());
        }
        System.out.println("================ INCOMING data =================");

        for (Map.Entry<String, List<String>> entry : incomingData.entrySet()) {
            for (String log : entry.getValue()) {
                System.out.println(log);
            }
            incomingStats.put(entry.getKey(), entry.getValue().size());
        }


        for (Map.Entry<String, List<String>> outgoingEntry : outgoingData.entrySet()) {
            String monitoringRef = outgoingEntry.getKey();
            if (!incomingData.containsKey(monitoringRef)) {
                requestedButNotIncoming.add(monitoringRef);
                totalRequestedButNotIncoming = totalRequestedButNotIncoming + outgoingEntry.getValue().size();
            } else {
                Integer nbOfIncoming = incomingData.get(monitoringRef).size();
                if (nbOfIncoming >= outgoingEntry.getValue().size()) {
                    mostlyIncoming.add(monitoringRef);
                    mostlyIncomingIncoming = mostlyIncomingIncoming + nbOfIncoming;
                    mostlyIncomingOutgoing = mostlyIncomingOutgoing + outgoingEntry.getValue().size();
                } else {
                    mostlyRequested.add(monitoringRef);
                    mostlyRequestedIncoming = mostlyRequestedIncoming + nbOfIncoming;
                    mostlyRequestedOutgoing = mostlyRequestedOutgoing + outgoingEntry.getValue().size();
                }
            }
        }

        for (Map.Entry<String, List<String>> stringListEntry : incomingData.entrySet()) {
            String monitoringRef = stringListEntry.getKey();
            if (!outgoingData.containsKey(monitoringRef)) {
                incomingButNeverRequested.add(monitoringRef);
                totalIncomingButNeverRequested = totalIncomingButNeverRequested + stringListEntry.getValue().size();
            }
        }

        System.out.println("================= Stats ================");
        System.out.println("Points requetés par IS, qui n'ont jamais reçu de données entrantes : " + requestedButNotIncoming.size() + ", total requetes : " + totalRequestedButNotIncoming);
        System.out.println("Points en entrée depuis navocap, jamais requétés en sortie par IS : " + incomingButNeverRequested.size() + ", total entrées : " + totalIncomingButNeverRequested);
        System.out.println("Points plus requetés que de données recues en entrée : " + mostlyRequested.size() + ", total Inc :" + mostlyRequestedIncoming + ", total out:" + mostlyRequestedOutgoing);
        System.out.println("Points plus entrants que de requêtes en sortie : " + mostlyIncoming.size() + ", total inc :" + mostlyIncomingIncoming + ", total out:" + mostlyIncomingOutgoing);
    }

    private void recordIncomingData(Map<String, List<String>> results, String monitoringRef, String log) {
        if (results.containsKey(monitoringRef)) {
            results.get(monitoringRef).add(log);
        } else {
            List<String> list = new LinkedList<>();
            list.add(log);
            results.put(monitoringRef, list);
        }
    }

    private String extractPointFromBody(String body) throws TransformerException, XMLStreamException, JAXBException {

        TransformerFactory tFactory = TransformerFactory.newInstance();
        Source xslDoc = new StreamSource("src/main/resources/xsl/siri_soap_raw.xsl");

        // Source XML depuis la String
        Source xmlInput = new StreamSource(new StringReader(body));

        try {


            // Résultat dans un flux mémoire
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Transformer transformer = tFactory.newTransformer(xslDoc);
            transformer.transform(xmlInput, new StreamResult(outputStream));
            byte[] transformedBytes = outputStream.toByteArray();
            Siri incoming = SiriValueTransformer.parseXml(new ByteArrayInputStream(transformedBytes));
            if (incoming != null && incoming.getServiceDelivery() != null && incoming.getServiceDelivery().getStopMonitoringDeliveries() != null && !incoming.getServiceDelivery().getStopMonitoringDeliveries().isEmpty() &&
                    !incoming.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().isEmpty()) {

                return incoming.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits().getFirst().getMonitoringRef().getValue();
            }
        } catch (Exception e) {
            return null;
        }


        return null;
    }

    private void recordResult(Map<String, List<String>> results, String monitoringRef, String log) {

        if (results.containsKey(monitoringRef)) {
            results.get(monitoringRef).add(log);
        } else {
            List<String> list = new LinkedList<>();
            list.add(log);
            results.put(monitoringRef, list);
        }
    }

    private String extractMonitoringRef(String siriXml) {
        if (!siriXml.contains("MonitoringRef")) {
            return "";
        }

        String tagName = siriXml.contains("ns4:") ? "ns4:MonitoringRef" : "MonitoringRef";
        try {
            String[] firstSplit = siriXml.split("<" + tagName + ">");
            String[] secondSplit = firstSplit[1].split("</" + tagName + ">");
            return secondSplit[0];
        } catch (Exception e) {
            System.out.println("====> " + siriXml);
            return "";
        }


    }
}
