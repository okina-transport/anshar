package no.rutebanken.anshar.gtfsrt.mappers.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;

public class ElementUtils {
    private static final Logger logger = LoggerFactory.getLogger(ElementUtils.class);

    private ElementUtils() {
        //should not be instantiated
    }

    public static Element createSimpleExtensionElement(String name, String value) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();
            Element element = doc.createElement(name);
            element.setTextContent(value);
            return element;
        } catch (Exception e) {
            logger.error("Erreur lors de la création de l'élément XML pour l'extension : {}", name, e);
            return null;
        }
    }
}
