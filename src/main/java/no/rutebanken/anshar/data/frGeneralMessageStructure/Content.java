package no.rutebanken.anshar.data.frGeneralMessageStructure;

import lombok.Getter;
import lombok.Setter;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@XmlType(name = "FrGeneralMessageStructure")
@XmlAccessorType(XmlAccessType.FIELD)
public class Content implements Serializable {

    @XmlElement(name = "LineRef", namespace = "http://www.siri.org.uk/siri")
    private List<String> lineRefs = new ArrayList<>();

    @XmlElement(name = "StopPointRef", namespace = "http://www.siri.org.uk/siri")
    private List<String> stopPointRefs = new ArrayList<>();

    @XmlElement(name = "StopPlaceRef", namespace = "http://www.siri.org.uk/siri")
    private List<String> stopPlaceRefs = new ArrayList<>();

    @XmlElement(name = "JourneyPatternRef", namespace = "http://www.siri.org.uk/siri")
    private List<String> journeyPatternRefs = new ArrayList<>();

    @XmlElement(name = "RouteRef", namespace = "http://www.siri.org.uk/siri")
    private List<String> routeRefs = new ArrayList<>();

    @XmlElement(name = "GroupOfLinesRef", namespace = "http://www.siri.org.uk/siri")
    private List<String> groupOfLinesRefs = new ArrayList<>();

    @XmlElement(name = "Message", namespace = "http://www.siri.org.uk/siri")
    private List<Message> messages = new ArrayList<>();
}
