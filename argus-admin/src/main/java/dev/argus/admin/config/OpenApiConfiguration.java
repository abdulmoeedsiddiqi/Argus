package dev.argus.admin.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Description of the Admin API for springdoc: served as JSON at {@code /v3/api-docs} and as a
 * page at {@code /swagger-ui.html}. The endpoints themselves are found from the controllers.
 */
@Configuration
public class OpenApiConfiguration {

    private static final String SCHEME = "adminToken";

    @Bean
    OpenAPI adminOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Argus Admin API")
                        .version("v1")
                        .description("Routes, upstream instances, consumers, plans and API keys of"
                                + " the Argus gateway. Sign in at POST /admin/login, then send the"
                                + " token as 'Authorization: Bearer <token>'."))
                // Declares the bearer token, so the Authorize button of Swagger UI works.
                .components(new Components().addSecuritySchemes(SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME));
    }
}
