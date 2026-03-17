package org.tkit.onecx.file.storage;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.quarkiverse.mockserver.test.MockServerTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import io.restassured.RestAssured;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;

@QuarkusTestResource(MockServerTestResource.class)
public abstract class AbstractTest {

    protected static final String ADMIN = "alice";

    protected static final String USER = "bob";

    public KeycloakTestClient keycloakClient = new KeycloakTestClient();

    protected static final String APM_HEADER_PARAM;
    protected static final String CLAIMS_ORG_ID;

    static {
        Config cfg = ConfigProvider.getConfig();
        APM_HEADER_PARAM = cfg.getOptionalValue("%test.tkit.rs.context.token.header-param", String.class)
                .orElse("apm-principal-token");
        CLAIMS_ORG_ID = cfg.getOptionalValue("%test.tkit.rs.context.tenant-id.mock.claim-org-id", String.class)
                .orElse("orgId");
    }

    static {
        RestAssured.config = RestAssuredConfig.config().objectMapperConfig(
                ObjectMapperConfig.objectMapperConfig().jackson2ObjectMapperFactory(
                        (cls, charset) -> {
                            ObjectMapper objectMapper = new ObjectMapper();
                            objectMapper.registerModule(new JavaTimeModule());
                            objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
                            return objectMapper;
                        }));
    }

}
