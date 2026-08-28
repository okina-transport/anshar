package no.rutebanken.anshar.integration;

import no.rutebanken.anshar.App;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.mockserver.integration.ClientAndServer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.socket.PortFactory.findFreePort;

@CamelSpringBootTest
@SpringBootTest(webEnvironment= SpringBootTest.WebEnvironment.NONE, classes = App.class)
public abstract class SpringBootBaseTest {

    static final GenericContainer<?> artemis =
            new GenericContainer<>("apache/activemq-artemis:latest")
                    .withExposedPorts(61616)
                    .withEnv("ANONYMOUS_LOGIN", "true")
                    .withEnv("ARTEMIS_USERNAME", "admin")
                    .withEnv("ARTEMIS_PASSWORD", "admin");

    static final ClientAndServer mockServerIshtarKeycloakOkina
            = startClientAndServer(findFreePort());

    static {
        artemis.start();

        for (String path : new String[]{
                "/siri-apis/all",
                "/id-processing-parameters/all",
                "/subscriptions/all",
                "/gtfs-rt-apis"
        }) {
            mockServerIshtarKeycloakOkina.when(request().withPath(path))
                    .respond(response()
                            .withStatusCode(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("[]"));
        }

        mockServerIshtarKeycloakOkina.when(request().withPath("/realms/fakeRealm/protocol/openid-connect/token"))
                .respond(response()
                        .withStatusCode(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"test-token\",\"expires_in\":300,\"refresh_expires_in\":0,\"token_type\":\"Bearer\"}"));

        mockServerIshtarKeycloakOkina.when(request().withPath("/disruptions"))
                .respond(response().withStatusCode(200));

        Runtime.getRuntime().addShutdownHook(new Thread(mockServerIshtarKeycloakOkina::stop));
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.activemq.broker-url",
                () -> "tcp://" + artemis.getHost() + ":" + artemis.getMappedPort(61616));
        registry.add("ishtar.server.url",
                () -> "http://localhost:" + mockServerIshtarKeycloakOkina.getLocalPort());
        registry.add("keycloak.auth-server-url",
                () -> "http://localhost:" + mockServerIshtarKeycloakOkina.getLocalPort());
        registry.add("mobi.iti.disruption.api.url",
                () -> "http://localhost:" + mockServerIshtarKeycloakOkina.getLocalPort() + "/disruptions");
    }
}
