package no.rutebanken.anshar.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // Applique les CORS à toutes les routes
                .allowedOrigins("*") // Autorise les requêtes venant de cette origine
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // Autorise ces méthodes HTTP
                .allowedHeaders("*") // Autorise tous les headers
                .allowCredentials(true); // Autorise l'envoi des cookies d'authentification
    }
}

