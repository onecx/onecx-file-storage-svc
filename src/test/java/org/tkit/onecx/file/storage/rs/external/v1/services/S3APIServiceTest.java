package org.tkit.onecx.file.storage.rs.external.v1.services;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tkit.onecx.file.storage.AbstractTest;
import org.tkit.onecx.file.storage.test.S3BucketTestResource;
import org.tkit.quarkus.context.ApplicationContext;
import org.tkit.quarkus.context.Context;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

@QuarkusTest
@QuarkusTestResource(S3BucketTestResource.class)
class S3APIServiceTest extends AbstractTest {

    @Inject
    S3APIService s3APIService;

    @Inject
    S3Client s3Client;

    @ConfigProperty(name = "onecx.file.storage.bucket")
    String bucketName;

    @BeforeEach
    void setUp() {
        ApplicationContext.start(Context.builder().tenantId("tenant1").build());
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
        } catch (S3Exception e) {
            // ignore if bucket already exists or other S3-specific errors
        }
    }

    @AfterEach
    void tearDown() {
        ApplicationContext.close();
    }

    // ---------------------------------------------------------------------------
    // bucketExists
    // ---------------------------------------------------------------------------

    @Test
    void bucketExistsTrueTest() {
        // LocalStack ma już bucket utworzony przez devservices.buckets=test-bucket
        assertTrue(s3APIService.bucketExists(bucketName));
    }

    @Test
    void bucketExistsReturnsFalseWhenNoSuchBucketTest() {
        assertFalse(s3APIService.bucketExists("non-existing-bucket-xyz"));
    }

    // ---------------------------------------------------------------------------
    // uploadFile
    // ---------------------------------------------------------------------------

    @Test
    void uploadFileTest() throws Exception {
        // hasTenantId() == true (setUp ustawia tenantId)
        InputStream data = new ByteArrayInputStream("hello onecx".getBytes());
        assertDoesNotThrow(() -> s3APIService.uploadFile("test-file.txt", data, "product1", "app1"));
    }

    @Test
    void uploadFileWithoutTenantIdTest() throws Exception {
        // hasTenantId() == false — gałąź defaultTenantId
        ApplicationContext.close();
        ApplicationContext.start(Context.builder().build());

        InputStream data = new ByteArrayInputStream("hello onecx".getBytes());
        assertDoesNotThrow(() -> s3APIService.uploadFile("test-file-no-tenant.txt", data, "product1", "app1"));
    }

    @Test
    void uploadFileWithKnownContentTypeTest() throws Exception {
        // contentType != null — PNG magic bytes wykryte przez guessContentTypeFromStream
        InputStream data = new ByteArrayInputStream(new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
        });
        assertDoesNotThrow(() -> s3APIService.uploadFile("img.png", data, "product1", "app1"));
    }

    // ---------------------------------------------------------------------------
    // downloadFile
    // ---------------------------------------------------------------------------

    @Test
    void downloadFileTest() throws Exception {
        // najpierw uploadujemy, potem pobieramy
        byte[] content = "download content".getBytes();
        s3APIService.uploadFile("download-test.txt", new ByteArrayInputStream(content), "product1", "app1");

        try (ResponseInputStream<GetObjectResponse> result = s3APIService.downloadFile("download-test.txt", "product1",
                "app1")) {
            assertNotNull(result);
            assertArrayEquals(content, result.readAllBytes());
        }
    }

    @Test
    void downloadFileWithoutTenantIdTest() throws Exception {
        // hasTenantId() == false
        ApplicationContext.close();
        ApplicationContext.start(Context.builder().build());

        byte[] content = "no-tenant content".getBytes();
        s3APIService.uploadFile("no-tenant.txt", new ByteArrayInputStream(content), "product1", "app1");

        try (ResponseInputStream<GetObjectResponse> result = s3APIService.downloadFile("no-tenant.txt", "product1", "app1")) {
            assertNotNull(result);
        }
    }

    // ---------------------------------------------------------------------------
    // deleteFile
    // ---------------------------------------------------------------------------

    @Test
    void deleteFileTest() throws Exception {
        s3APIService.uploadFile("to-delete.txt", new ByteArrayInputStream("data".getBytes()), "product1", "app1");
        assertDoesNotThrow(() -> s3APIService.deleteFile("to-delete.txt", "product1", "app1"));
    }

    @Test
    void deleteFileWithoutTenantIdTest() throws Exception {
        // hasTenantId() == false
        ApplicationContext.close();
        ApplicationContext.start(Context.builder().build());

        s3APIService.uploadFile("to-delete-no-tenant.txt",
                new ByteArrayInputStream("data".getBytes()), "product1", "app1");
        assertDoesNotThrow(() -> s3APIService.deleteFile("to-delete-no-tenant.txt", "product1", "app1"));
    }

    // ---------------------------------------------------------------------------
    // getPresignedDownloadUrl
    // ---------------------------------------------------------------------------

    @Test
    void getPresignedDownloadUrlTest() {
        var result = s3APIService.getPresignedDownloadUrl("test-file.txt", "product1", "app1");
        assertNotNull(result);
        assertNotNull(result.getUrl());
    }

    @Test
    void getPresignedDownloadUrlWithoutTenantIdTest() {
        // hasTenantId() == false
        ApplicationContext.close();
        ApplicationContext.start(Context.builder().build());

        var result = s3APIService.getPresignedDownloadUrl("test-file.txt", "product1", "app1");
        assertNotNull(result);
        assertNotNull(result.getUrl());
    }

    // ---------------------------------------------------------------------------
    // getPresignedUploadUrl
    // ---------------------------------------------------------------------------

    @Test
    void getPresignedUploadUrlTest() {
        var result = s3APIService.getPresignedUploadUrl("test-file.txt", "product1", "app1");
        assertNotNull(result);
        assertNotNull(result.getUrl());
    }

    @Test
    void getPresignedUploadUrlWithoutTenantIdTest() {
        // hasTenantId() == false
        ApplicationContext.close();
        ApplicationContext.start(Context.builder().build());

        var result = s3APIService.getPresignedUploadUrl("test-file.txt", "product1", "app1");
        assertNotNull(result);
        assertNotNull(result.getUrl());
    }
}
