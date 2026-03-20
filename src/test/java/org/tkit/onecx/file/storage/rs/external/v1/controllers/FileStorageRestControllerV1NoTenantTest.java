package org.tkit.onecx.file.storage.rs.external.v1.controllers;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import jakarta.ws.rs.core.MediaType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tkit.onecx.file.storage.AbstractTest;
import org.tkit.quarkus.security.test.GenerateKeycloakClient;

import gen.org.tkit.onecx.file.storage.rs.external.v1.model.FileDownloadRequestDTOV1;
import gen.org.tkit.onecx.file.storage.rs.external.v1.model.FileMetadataRequestDTOV1;
import gen.org.tkit.onecx.file.storage.rs.external.v1.model.PresignedUrlRequestDTOV1;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(FileStorageRestControllerV1NoTenantTest.class)
@GenerateKeycloakClient(clientName = "testClient", scopes = { "ocx-fs:read", "ocx-fs:write", "ocx-fs:delete" })
public class FileStorageRestControllerV1NoTenantTest extends AbstractTest implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "%test.tkit.rs.context.tenant-id.mock.enabled", "false");
    }

    String token;
    String idToken;

    @BeforeEach
    void setup() {
        token = keycloakClient.getClientAccessToken("testClient");
        idToken = createToken("noTenant");
    }

    @Test
    void uploadAndDownloadFileTest() {
        byte[] fileContent = "onecx file content".getBytes(StandardCharsets.UTF_8);

        given()
                .auth().oauth2(token)
                .header(APM_HEADER_PARAM, idToken)
                .multiPart("applicationId", "app1")
                .multiPart("productName", "product1")
                .multiPart("fileName", "my-file.txt")
                .multiPart("file", "my-file.txt", fileContent, null)
                .when()
                .post("/v1/file-storage/file/upload")
                .then()
                .statusCode(201);

        FileDownloadRequestDTOV1 request = new FileDownloadRequestDTOV1();
        request.setFileName("my-file.txt");
        request.setProductName("product1");
        request.setApplicationId("app1");

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .when()
                .auth().oauth2(token)
                .header(APM_HEADER_PARAM, idToken)
                .contentType(APPLICATION_JSON)
                .post("/v1/file-storage/file/download")
                .then()
                .statusCode(200)
                .contentType("application/octet-stream")
                .extract()
                .asByteArray();
    }

    @Test
    void uploadAndDownloadUnknownContentTypeFileTest() {
        // Bytes with no recognizable magic bytes - guessContentTypeFromStream returns null,
        // so the service must fall back to "application/octet-stream"
        byte[] unknownFileContent = new byte[] { 0x00, 0x01, 0x02, 0x03, 0x04, 0x05 };

        given()
                .auth().oauth2(token)
                .header(APM_HEADER_PARAM, idToken)
                .multiPart("applicationId", "app1")
                .multiPart("productName", "product1")
                .multiPart("fileName", "unknown-type.bin")
                // pass null as MIME type so the service is forced to detect it from stream
                .multiPart("file", "unknown-type.bin", unknownFileContent, null)
                .when()
                .post("/v1/file-storage/file/upload")
                .then()
                .statusCode(201);

        FileDownloadRequestDTOV1 downloadRequest = new FileDownloadRequestDTOV1();
        downloadRequest.setFileName("unknown-type.bin");
        downloadRequest.setProductName("product1");
        downloadRequest.setApplicationId("app1");

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(downloadRequest)
                .when()
                .auth().oauth2(token)
                .header(APM_HEADER_PARAM, idToken)
                .contentType(APPLICATION_JSON)
                .post("/v1/file-storage/file/download")
                .then()
                .statusCode(200)
                // fallback content type expected because stream detection failed
                .contentType("application/octet-stream")
                .extract()
                .asByteArray();
    }

    @Test
    void getPresignedUploadUrlTest() {

        PresignedUrlRequestDTOV1 request = new PresignedUrlRequestDTOV1();
        request.setFileName("test-file.txt");
        request.setProductName("product1");
        request.setApplicationId("app1");

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .when()
                .auth().oauth2(token)
                .header(APM_HEADER_PARAM, idToken)
                .contentType(APPLICATION_JSON)
                .post("/v1/file-storage/presigned/upload")
                .then()
                .statusCode(200);
    }

    @Test
    void getMetadataForFilesTest() {
        byte[] fileContent = "onecx file content".getBytes(StandardCharsets.UTF_8);

        given()
                .auth().oauth2(token)
                .header(APM_HEADER_PARAM, idToken)
                .multiPart("applicationId", "app1")
                .multiPart("productName", "product1")
                .multiPart("fileName", "my-file.txt")
                .multiPart("file", "my-file.txt", fileContent, "application/octet-stream")
                .when()
                .post("/v1/file-storage/file/upload")
                .then()
                .statusCode(201);

        FileMetadataRequestDTOV1 request = new FileMetadataRequestDTOV1();
        request.setFileName("my-file.txt");
        request.setProductName("product1");
        request.setApplicationId("app1");

        given()
                .auth().oauth2(token)
                .header(APM_HEADER_PARAM, idToken)
                .contentType(APPLICATION_JSON)
                .body(List.of(request))
                .when()
                .post("/v1/file-storage/file/metadata")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].fileName", equalTo("my-file.txt"))
                .body("[0].size", notNullValue())
                .body("[0].type", notNullValue());
    }

    @Test
    void getPresignedDownloadUrlTest() {
        PresignedUrlRequestDTOV1 request = new PresignedUrlRequestDTOV1();
        request.setFileName("test-file.txt");
        request.setProductName("product1");
        request.setApplicationId("app1");

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .when()
                .auth().oauth2(token)
                .header(APM_HEADER_PARAM, idToken)
                .contentType(APPLICATION_JSON)
                .post("/v1/file-storage/presigned/download")
                .then()
                .statusCode(200);
    }

    @Test
    void deleteFileTest() {
        FileDownloadRequestDTOV1 request = new FileDownloadRequestDTOV1();
        request.setFileName("test-file.txt");
        request.setProductName("product1");
        request.setApplicationId("app1");

        given().contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .when()
                .auth().oauth2(token)
                .header(APM_HEADER_PARAM, idToken)
                .contentType(APPLICATION_JSON)
                .post("/v1/file-storage/file/delete")
                .then()
                .statusCode(200);

    }
}
