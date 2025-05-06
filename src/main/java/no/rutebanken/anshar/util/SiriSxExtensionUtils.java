package no.rutebanken.anshar.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;

@Slf4j
public class SiriSxExtensionUtils {

    protected static final Set<String> DATE_NODES = Set.of("NotificationsDate", "NotificationDate");

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private SiriSxExtensionUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static void transformDateInExtensionsNode(List<Element> nodeList) {
        if (CollectionUtils.isNotEmpty(nodeList)) {
            for (Node currentNode : nodeList) {
                log.debug("Starting sx extensions transformation");
                Deque<Node> stack = new ArrayDeque<>();
                stack.push(currentNode);

                while (!stack.isEmpty()) {
                    Node node = stack.pop();
                    log.debug("Node name: {}", node.getNodeName());
                    if (node.getNodeType() == Node.ELEMENT_NODE && DATE_NODES.contains(node.getNodeName())) {
                        log.debug("Node value before transformation: {}", node.getTextContent().trim());
                        Instant instant = Instant.parse(node.getTextContent());
                        ZonedDateTime zonedDateTime = instant.atZone(ZoneId.systemDefault());
                        node.setTextContent(FORMATTER.format(zonedDateTime));
                        log.debug("Node value after transformation: {}", node.getTextContent().trim());
                    }

                    NodeList childNodes = node.getChildNodes();
                    for (int i = childNodes.getLength() - 1; i >= 0; i--) {
                        stack.push(childNodes.item(i));
                    }
                }
            }

        }
    }
}
