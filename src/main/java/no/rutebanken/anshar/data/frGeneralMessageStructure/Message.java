package no.rutebanken.anshar.data.frGeneralMessageStructure;

import javax.xml.bind.annotation.XmlElement;
import java.io.Serializable;


public class Message implements Serializable {

    @XmlElement(name = "MessageType", namespace="http://www.siri.org.uk/siri")
    private String msgType;

    @XmlElement(name = "MessageText", namespace="http://www.siri.org.uk/siri")
    private String msgText;



    public void setMsgType(String msgType) {
        this.msgType = msgType;
    }



    public void setMsgText(String msgText) {
        this.msgText = msgText;
    }
}
