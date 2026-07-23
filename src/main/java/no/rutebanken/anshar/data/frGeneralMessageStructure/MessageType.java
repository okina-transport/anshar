package no.rutebanken.anshar.data.frGeneralMessageStructure;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlEnumValue;
import lombok.Getter;

@Getter
@XmlEnum
public enum MessageType {
    @XmlEnumValue("shortMessage") SHORT_MESSAGE("shortMessage"),
    @XmlEnumValue("longMessage") LONG_MESSAGE("longMessage"),
    @XmlEnumValue("textOnly") TEXT_ONLY("textOnly"),
    @XmlEnumValue("formattedText") FORMATTED_TEXT("formattedText"),
    @XmlEnumValue("HTML") HTML("HTML"),
    @XmlEnumValue("RTF") RTF("RTF"),
    @XmlEnumValue("codedMessage") CODED_MESSAGE("codedMessage");

    private final String code;

    MessageType(String code) {
        this.code = code;
    }
}
