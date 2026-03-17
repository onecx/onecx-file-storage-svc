package org.tkit.onecx.file.storage.rs.external.v1.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tkit.onecx.file.storage.AbstractTest;
import org.tkit.quarkus.context.ApplicationContext;
import org.tkit.quarkus.context.Context;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

@QuarkusTest
class S3APIServiceExceptionTest extends AbstractTest {

    @Inject
    S3APIService s3APIService;

    @InjectMock
    S3Client s3Client;

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
    void bucketExistsThrowsSdkExceptionTest() {
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(S3Exception.builder().message("connection error").build());

        assertThrows(WebApplicationException.class,
                () -> s3APIService.bucketExists("any-bucket"));
    }

    @Test
    void deleteFileSdkExceptionTest() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("S3 delete error").build());

        assertThrows(WebApplicationException.class,
                () -> s3APIService.deleteFile("my-file.txt", "product1", "app1"));
    }

    @Test
    void onInitThrowsWhenBucketDoesNotExistTest() {
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(NoSuchBucketException.builder().message("no bucket").build());

        assertThrows(IllegalStateException.class, () -> s3APIService.onInit());
    }
}
