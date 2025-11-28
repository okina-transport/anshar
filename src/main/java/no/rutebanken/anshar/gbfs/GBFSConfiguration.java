package no.rutebanken.anshar.gbfs;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "anshar.gbfs.configuration")
@Getter
@Setter
public class GBFSConfiguration {
    private String defaultDataset = "gbfs";
}
