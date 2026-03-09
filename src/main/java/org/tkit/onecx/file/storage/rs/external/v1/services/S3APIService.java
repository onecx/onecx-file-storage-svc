package org.tkit.onecx.file.storage.rs.external.v1.services;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.URLConnection;
import java.time.Duration;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.tkit.onecx.file.storage.rs.external.v1.mappers.PresginedUrlMapper;
import org.tkit.quarkus.context.ApplicationContext;

import gen.org.tkit.onecx.file.storage.rs.external.v1.model.PresignedUrlResponseDTOV1;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@ApplicationScoped
public class S3APIService {

    @ConfigProperty(name = "onecx.file.storage.bucket")
    private String bucketName;

    @ConfigProperty(name = "onecx.file.storage.default-tenant-id")
    private String defaultTenantId;

    @Inject
    S3Client s3Client;

    @Inject
    S3Presigner presigner;

    @Inject
    PresginedUrlMapper mapper;

    @PostConstruct
    void onInit() {
        if (!bucketExists(bucketName)) {
            throw new IllegalStateException("S3 bucket '" + bucketName + "' does not exist.");
        }
    }

    public PresignedUrlResponseDTOV1 getPresignedDownloadUrl(String fileId, String productName, String applicationId) {
        var tenantId = ApplicationContext.get().hasTenantId() ? ApplicationContext.get().getTenantId() : defaultTenantId;
        var filePath = buildFilePath(tenantId, productName, applicationId, fileId);

        GetObjectRequest objectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(filePath)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .getObjectRequest(objectRequest)
                .build();

        PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(presignRequest);
        return mapper.map(presignedRequest.url().toExternalForm(), presignedRequest.expiration().toString());
    }

    public PresignedUrlResponseDTOV1 getPresignedUploadUrl(String id, String productName, String applicationId) {
        var tenantId = ApplicationContext.get().hasTenantId() ? ApplicationContext.get().getTenantId() : defaultTenantId;
        var filePath = buildFilePath(tenantId, productName, applicationId, id);

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(filePath)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .putObjectRequest(objectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = presigner.presignPutObject(presignRequest);
        return mapper.map(presignedRequest.url().toExternalForm(), presignedRequest.expiration().toString());
    }

    public boolean bucketExists(String bucket) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            return true;
        } catch (NoSuchBucketException e) {
            return false;
        } catch (SdkException ex) {
            throw new WebApplicationException("Error checking bucket existence: " + ex.getMessage(), ex);
        }
    }

    public void uploadFile(String fileId, InputStream data, String productName, String applicationId) throws Exception {
        var tenantId = ApplicationContext.get().hasTenantId() ? ApplicationContext.get().getTenantId() : defaultTenantId;
        var filePath = buildFilePath(tenantId, productName, applicationId, fileId);

        BufferedInputStream buffered = new BufferedInputStream(data);
        String contentType = URLConnection.guessContentTypeFromStream(buffered);
        if (contentType == null) {
            contentType = "application/octet-stream";
        }
        byte[] bytes = buffered.readAllBytes();

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(filePath)
                .contentType(contentType)
                .contentLength((long) bytes.length)
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(bytes));
    }

    public ResponseInputStream<GetObjectResponse> downloadFile(String fileId, String productName, String applicationId) {
        var tenantId = ApplicationContext.get().hasTenantId() ? ApplicationContext.get().getTenantId() : defaultTenantId;
        var filePath = buildFilePath(tenantId, productName, applicationId, fileId);

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(filePath)
                .build();

        return s3Client.getObject(getObjectRequest);
    }

    private String buildFilePath(String tenantId, String productName, String applicationId, String fileName) {
        return tenantId + "/" + productName + "/" + applicationId + "/" + fileName;
    }

    public void deleteFile(String fileId, String productName, String applicationId) {
        var tenantId = ApplicationContext.get().hasTenantId() ? ApplicationContext.get().getTenantId() : defaultTenantId;
        var filePath = buildFilePath(tenantId, productName, applicationId, fileId);

        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(filePath)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
        } catch (SdkException ex) {
            throw new WebApplicationException("Error deleting file: " + ex.getMessage(), ex);
        }
    }

}
