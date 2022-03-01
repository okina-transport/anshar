package no.rutebanken.anshar.okinaDisruptions.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import lombok.Getter;
import lombok.Setter;
import no.rutebanken.anshar.okinaDisruptions.model.enums.OrganisationTypeEnum;

import javax.xml.bind.annotation.XmlTransient;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class OkinaCompany extends NeptuneIdentifiedObject{

    private String name;
    private String shortName;
    private String organisationalUnit;
    private String operatingDepartmentName;
    private String code;
    private String phone;
    private String fax;
    private String email;
    private String registrationNumber;
    private String url;
    private String timeZone;

    private String passwordCompany;

    @XmlTransient
    private Date dateCountingCampaign;

    /**
     * lines
     *
     * @param lines
     * New value
     * @return The actual value
     */
    @JsonManagedReference
    @JsonIgnore
    private List<OkinaLine> okinaLines = new ArrayList<>(0);


    /**
     * La compagnie a-t-elle été "supprimée" par l'utilisateur ?
     */
    private Boolean active = Boolean.TRUE;

    private String agencyId;

    private String lang;

    private String fareUrl;

    /**
     * Organisation type
     *
     * @param organisationType
     *            New value
     * @return The actual value
     */
    @Getter
    @Setter
    private OrganisationTypeEnum organisationType;


    public Date getDateCountingCampeign() {
        if (this.dateCountingCampaign == null) {
            return new Date(0);
        } else {
            return this.dateCountingCampaign;
        }
    }

}
