package edu.iitp.landstack.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI landStackOpenApi() {
        return new OpenAPI().info(new Info()
                .title("LandStack API")
                .version("v1")
                .description("GIS-Based Land Information Integration & Governance Dashboard. "
                        + "Authentication uses the LANDSTACK_SESSION cookie; mutating requests need the X-XSRF-TOKEN header. "
                        + "LandStack does not verify ownership, title or legal status."));
    }
}
