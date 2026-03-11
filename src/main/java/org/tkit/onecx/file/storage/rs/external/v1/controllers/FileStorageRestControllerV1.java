package org.tkit.onecx.file.storage.rs.external.v1.controllers;

import java.io.InputStream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

import org.tkit.onecx.file.storage.rs.external.v1.services.S3APIService;

import gen.org.tkit.onecx.file.storage.rs.external.v1.FileStorageV1Api;
import gen.org.tkit.onecx.file.storage.rs.external.v1.model.FileDeleteRequestDTOV1;
import gen.org.tkit.onecx.file.storage.rs.external.v1.model.FileDownloadRequestDTOV1;
import gen.org.tkit.onecx.file.storage.rs.external.v1.model.PresignedUrlRequestDTOV1;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

@ApplicationScoped
@Transactional(Transactional.TxType.NOT_SUPPORTED)
public class FileStorageRestControllerV1 implements FileStorageV1Api {

    @Inject
    S3APIService s3APIService;

    @Override
    public Response getPresignedDownloadUrl(PresignedUrlRequestDTOV1 requestDTOV1) {
        var result = s3APIService.getPresignedDownloadUrl(requestDTOV1.getFileName(), requestDTOV1.getProductName(),
                requestDTOV1.getApplicationId());
        return Response.ok(result).build();
    }

    @Override
    public Response getPresignedUploadUrl(PresignedUrlRequestDTOV1 requestDTOV1) {
        var result = s3APIService.getPresignedUploadUrl(requestDTOV1.getFileName(), requestDTOV1.getProductName(),
                requestDTOV1.getApplicationId());
        return Response.ok(result).build();
    }

    @Override
    public Response uploadFile(String applicationId, String productName, String fileId, InputStream file) {
        try {
            s3APIService.uploadFile(fileId, file, productName, applicationId);
            return Response.status(Response.Status.CREATED).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(e.getMessage())
                    .build();
        }
    }

    @Override
    public Response deleteFile(FileDeleteRequestDTOV1 fileDeleteRequestDTOV1) {
        s3APIService.deleteFile(fileDeleteRequestDTOV1.getFileName(), fileDeleteRequestDTOV1.getProductName(),
                fileDeleteRequestDTOV1.getApplicationId());
        return Response.ok().build();
    }

    @Override
    public Response downloadFile(FileDownloadRequestDTOV1 fileDownloadRequestDTOV1) {
        try (ResponseInputStream<GetObjectResponse> s3Object = s3APIService
                .downloadFile(fileDownloadRequestDTOV1.getFileName(), fileDownloadRequestDTOV1.getProductName(),
                        fileDownloadRequestDTOV1.getApplicationId())) {
            GetObjectResponse metadata = s3Object.response();
            byte[] bytes = s3Object.readAllBytes();

            StreamingOutput stream = output -> output.write(bytes);

            return Response.ok(stream, metadata.contentType())
                    .header("Content-Length", metadata.contentLength())
                    .build();
        } catch (NoSuchKeyException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(e.getMessage())
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(e.getMessage())
                    .build();
        }
    }
}
