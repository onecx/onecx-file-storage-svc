package org.tkit.onecx.file.storage.rs.external.v1.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;

import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tkit.onecx.file.storage.AbstractTest;
import org.tkit.onecx.file.storage.rs.external.v1.mappers.PresginedUrlMapper;
import org.tkit.quarkus.context.ApplicationContext;
import org.tkit.quarkus.context.Context;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.*;

@QuarkusTest
class S3APIServiceTest extends AbstractTest {

    @Inject
    S3APIService s3APIService;

    @InjectMock
    S3Client s3Client;

    @InjectMock
    S3Presigner presigner;

    @InjectMock
    PresginedUrlMapper mapper;

    @ConfigProperty(name = "onecx.file.storage.bucket")
    String bucketName;

    @BeforeEach
    void setUp() {
        ApplicationContext.start(Context.builder().tenantId("tenant1").build());
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenReturn(HeadBucketResponse.builder().build());
    }

    @AfterEach
    void tearDown() {
        ApplicationContext.close();
    }

    @Test
    void onInitThrowsWhenBucketDoesNotExistTest() {
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(NoSuchBucketException.builder().message("no bucket").build());

        assertThrows(IllegalStateException.class, () -> s3APIService.onInit());
    }

    @Test
    void bucketExistsReturnsFalseWhenNoSuchBucketTest() {
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(NoSuchBucketException.builder().message("bucket not found").build());

        assertFalse(s3APIService.bucketExists(bucketName));
    }

    @Test
    void bucketExistsThrowsWebApplicationExceptionOnSdkExceptionTest() {
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(S3Exception.builder().message("connection error").build());

        assertThrows(WebApplicationException.class,
                () -> s3APIService.bucketExists(bucketName));
    }

    @Test
    void uploadFileTest() throws Exception {
        String fileId = "my-file.txt";
        String productName = "product1";
        String applicationId = "app1";
        InputStream data = new ByteArrayInputStream("onecx content".getBytes());

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        assertDoesNotThrow(() -> s3APIService.uploadFile(fileId, data, productName, applicationId));

        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void uploadFileExceptionTest() {
        String fileId = "my-file.txt";
        String productName = "product1";
        String applicationId = "app1";
        InputStream data = new ByteArrayInputStream("onecx content".getBytes());

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("S3 error").build());

        assertThrows(Exception.class,
                () -> s3APIService.uploadFile(fileId, data, productName, applicationId));
    }

    @Test
    void uploadFileWithNullContentTypeTest() throws Exception {
        InputStream data = new ByteArrayInputStream(new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
        });

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        assertDoesNotThrow(() -> s3APIService.uploadFile("img.png", data, "product1", "app1"));
        verify(s3Client, atLeastOnce()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void uploadFileWithoutTenantIdTest() throws Exception {

        ApplicationContext.close();
        ApplicationContext.start(Context.builder().build());

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        assertDoesNotThrow(() -> s3APIService.uploadFile("file.txt",
                new ByteArrayInputStream("data".getBytes()), "product1", "app1"));
    }

    @Test
    void downloadFileTest() {
        String fileId = "my-file.txt";
        String productName = "product1";
        String applicationId = "app1";

        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(
                new ResponseInputStream<>(GetObjectResponse.builder().build(),
                        new ByteArrayInputStream("onecx content".getBytes())));

        assertDoesNotThrow(() -> s3APIService.downloadFile(fileId, productName, applicationId));

        verify(s3Client, times(1)).getObject(any(GetObjectRequest.class));
    }

    @Test
    void downloadFileWithoutTenantIdTest() {
        ApplicationContext.close();
        ApplicationContext.start(Context.builder().build());

        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(
                new ResponseInputStream<>(GetObjectResponse.builder().build(),
                        new ByteArrayInputStream("data".getBytes())));

        assertDoesNotThrow(() -> s3APIService.downloadFile("file.txt", "product1", "app1"));
    }

    @Test
    void deleteFileTest() {
        String fileId = "my-file.txt";
        String productName = "product1";
        String applicationId = "app1";

        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());

        assertDoesNotThrow(() -> s3APIService.deleteFile(fileId, productName, applicationId));

        verify(s3Client, times(1)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void deleteFileSdkExceptionTest() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("S3 delete error").build());

        assertThrows(WebApplicationException.class,
                () -> s3APIService.deleteFile("my-file.txt", "product1", "app1"));
    }

    @Test
    void deleteFileWithoutTenantIdTest() {

        ApplicationContext.close();
        ApplicationContext.start(Context.builder().build());

        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());

        assertDoesNotThrow(() -> s3APIService.deleteFile("file.txt", "product1", "app1"));
    }

    @Test
    void getPresignedDownloadUrlTest() throws Exception {
        String fileId = "my-file.txt";
        String productName = "product1";
        String applicationId = "app1";

        PresignedGetObjectRequest presignedRequest = mock(PresignedGetObjectRequest.class);
        when(presignedRequest.url()).thenReturn(URI.create("https://example.com/presigned-url").toURL());
        when(presignedRequest.expiration()).thenReturn(java.time.Instant.now().plusSeconds(900));
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedRequest);

        assertDoesNotThrow(() -> s3APIService.getPresignedDownloadUrl(fileId, productName, applicationId));

        verify(presigner, times(1)).presignGetObject(any(GetObjectPresignRequest.class));
    }

    @Test
    void getPresignedDownloadUrlWithoutTenantIdTest() throws Exception {

        ApplicationContext.close();
        ApplicationContext.start(Context.builder().build());

        PresignedGetObjectRequest presignedRequest = mock(PresignedGetObjectRequest.class);
        when(presignedRequest.url()).thenReturn(URI.create("https://example.com/presigned").toURL());
        when(presignedRequest.expiration()).thenReturn(java.time.Instant.now().plusSeconds(900));
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedRequest);

        assertDoesNotThrow(() -> s3APIService.getPresignedDownloadUrl("file.txt", "product1", "app1"));
    }

    @Test
    void getPresignedUploadUrlTest() throws Exception {
        String fileId = "my-file.txt";
        String productName = "product1";
        String applicationId = "app1";

        PresignedPutObjectRequest presignedRequest = mock(PresignedPutObjectRequest.class);
        when(presignedRequest.url()).thenReturn(URI.create("https://example.com/presigned-upload-url").toURL());
        when(presignedRequest.expiration()).thenReturn(java.time.Instant.now().plusSeconds(900));
        when(presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedRequest);

        assertDoesNotThrow(() -> s3APIService.getPresignedUploadUrl(fileId, productName, applicationId));

        verify(presigner, times(1)).presignPutObject(any(PutObjectPresignRequest.class));
    }

    @Test
    void getPresignedUploadUrlWithoutTenantIdTest() throws Exception {

        ApplicationContext.close();
        ApplicationContext.start(Context.builder().build());

        PresignedPutObjectRequest presignedRequest = mock(PresignedPutObjectRequest.class);
        when(presignedRequest.url()).thenReturn(URI.create("https://example.com/presigned-put").toURL());
        when(presignedRequest.expiration()).thenReturn(java.time.Instant.now().plusSeconds(900));
        when(presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedRequest);

        assertDoesNotThrow(() -> s3APIService.getPresignedUploadUrl("file.txt", "product1", "app1"));
    }
}