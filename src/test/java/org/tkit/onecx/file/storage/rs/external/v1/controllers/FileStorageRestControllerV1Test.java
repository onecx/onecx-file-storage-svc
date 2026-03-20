package org.tkit.onecx.file.storage.rs.external.v1.controllers;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.hamcrest.Matchers.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.ws.rs.core.MediaType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tkit.onecx.file.storage.AbstractTest;
import org.tkit.quarkus.security.test.GenerateKeycloakClient;

import gen.org.tkit.onecx.file.storage.rs.external.v1.model.FileDownloadRequestDTOV1;
import gen.org.tkit.onecx.file.storage.rs.external.v1.model.FileMetadataRequestDTOV1;
import gen.org.tkit.onecx.file.storage.rs.external.v1.model.PresignedUrlRequestDTOV1;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@GenerateKeycloakClient(clientName = "testClient", scopes = { "ocx-fs:read", "ocx-fs:write", "ocx-fs:delete" })
class FileStorageRestControllerV1Test extends AbstractTest {

    String token;
    String idToken;

    @BeforeEach
    void setup() {
        token = keycloakClient.getClientAccessToken("testClient");
        idToken = createToken("org1");
    }

    @Test
    void uploadAndDownloadFileTest() {

        // no magic bytes, should be detected as "application/octet-stream"
        byte[] fileContentfallback = "onecx file content".getBytes(StandardCharsets.UTF_8);

        given()
                .auth().oauth2(token)
                .header(APM_HEADER_PARAM, idToken)
                .multiPart("applicationId", "app1")
                .multiPart("productName", "product1")
                .multiPart("fileName", "my-file-2.txt")
                .multiPart("file", "my-file-2.txt", fileContentfallback, null)
                .when()
                .post("/v1/file-storage/file/upload")
                .then()
                .statusCode(201);

        // Minimal PNG magic bytes - URLConnection.guessContentTypeFromStream detects this as "image/png"
        byte[] fileContent = new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A // PNG signature
        };

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
                .contentType("image/png")
                .extract()
                .asByteArray();
    }

    @Test
    void uploadFileBadRequestTest() {

        given()
                .auth().oauth2(token)
                .header(APM_HEADER_PARAM, idToken)
                .multiPart("applicationId", "app1")
                .multiPart("productName", "product1")
                .multiPart("fileName", "my-file.txt")
                .when()
                .post("/v1/file-storage/file/upload")
                .then()
                .statusCode(400);
    }

    @Test
    void downloadFileNotFoundTest() {

        FileDownloadRequestDTOV1 request = new FileDownloadRequestDTOV1();
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
                .post("/v1/file-storage/file/download")
                .then()
                .statusCode(404);
    }

    @Test
    void downloadFileGeneralExceptionTest() {

        FileDownloadRequestDTOV1 request = new FileDownloadRequestDTOV1();
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
                .statusCode(400);
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
        // Minimal PNG magic bytes - URLConnection.guessContentTypeFromStream detects this as "image/png"
        byte[] fileContent = new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A // PNG signature
        };

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
    void getMetadataForFilesNotFoundTest() {
        FileMetadataRequestDTOV1 request = new FileMetadataRequestDTOV1();
        request.setFileName("non-existent-file.txt");
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
                .statusCode(400);
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

}
