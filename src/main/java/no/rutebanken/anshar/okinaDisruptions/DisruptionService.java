package no.rutebanken.anshar.okinaDisruptions;

import no.rutebanken.anshar.config.TokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@Service
public class DisruptionService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${mobi.iti.disruption.api.url}")
    private String okinaDisruptionAPIUrl;

    @Autowired
    private TokenService tokenService;

    public String getAllDisrutionsFromOkinaDB(String ansharUserId){
        try {
            URL url = new URL(okinaDisruptionAPIUrl+"?requestorId="+ansharUserId);
            HttpURLConnection connection;
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Content-type", "application/json");
            connection.setDoOutput(true);
            connection.setRequestProperty("Authorization", "Bearer " + tokenService.getToken());
            InputStream inputStream = connection.getInputStream();
            return new BufferedReader( new InputStreamReader(inputStream, StandardCharsets.UTF_8)).lines() .collect(Collectors.joining("\n"));

        } catch (IOException e) {
            logger.error("Error while retrieving disruptions");
            logger.error(e.getMessage());
        }
        return "";
    }
}
