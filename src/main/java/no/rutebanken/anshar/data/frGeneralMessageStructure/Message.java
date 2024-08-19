package no.rutebanken.anshar.data.frGeneralMessageStructure;

import lombok.Getter;
import lombok.Setter;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import java.io.Serializable;


@Getter
@Setter
@XmlAccessorType(XmlAccessType.FIELD)
public class Message implements Serializable {

    @XmlElement(name = "MessageType", namespace="http://www.siri.org.uk/siri")
    private String msgType;

    @XmlElement(name = "MessageText", namespace="http://www.siri.org.uk/siri")
    private String msgText;
}
