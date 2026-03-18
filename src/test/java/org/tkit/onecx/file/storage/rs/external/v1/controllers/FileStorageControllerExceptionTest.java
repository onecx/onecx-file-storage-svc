package org.tkit.onecx.file.storage.rs.external.v1.controllers;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.mockito.ArgumentMatchers.anyString;

import jakarta.ws.rs.core.MediaType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.tkit.onecx.file.storage.AbstractTest;
import org.tkit.onecx.file.storage.rs.external.v1.services.S3APIService;
import org.tkit.quarkus.security.test.GenerateKeycloakClient;

import gen.org.tkit.onecx.file.storage.rs.external.v1.model.FileDownloadRequestDTOV1;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@GenerateKeycloakClient(clientName = "testClient", scopes = { "ocx-fs:read", "ocx-fs:write", "ocx-fs:delete" })
public class FileStorageControllerExceptionTest extends AbstractTest {

    @InjectMock
    S3APIService s3apiService;

    String token;

    @BeforeEach
    void setup() {
        token = keycloakClient.getClientAccessToken("testClient");
    }

    @Test
    void downloadFileExceptionTest() {
        Mockito.when(s3apiService.getPresignedDownloadUrl(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("simulated error"));

        FileDownloadRequestDTOV1 request = new FileDownloadRequestDTOV1();
        request.setFileName("any-file.txt");
        request.setProductName("product1");
        request.setApplicationId("app1");

        given()
                .auth().oauth2(token)
                .header(APM_HEADER_PARAM, createToken("org1"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .when()
                .post("/v1/file-storage/file/download")
                .then()
                .statusCode(400);
    }
}
