package org.tkit.onecx.file.storage.rs;

import io.quarkus.runtime.annotations.ConfigDocFilename;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * File Storage SVC configuration
 */
@ConfigDocFilename("onecx-file-storage-svc.adoc")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "onecx.file.storage")
public interface FileStorageConfig {

    /**
     * Bucket name configuration
     */
    @WithName("bucket")
    @WithDefault("onecx")
    String bucket();

    /**
     * S3 client configuration
     */
    @WithName("s3-client")
    S3ClientConfig s3Client();

    interface S3ClientConfig {

        /**
         * S3 client configuration
         */
        @WithName("endpoint")
        String endpointUrl();

        /**
         * S3 region configuration
         */
        @WithName("region")
        String region();

        /**
         * S3 access key configuration
         */
        @WithName("access-key-id")
        String accessKeyId();

        /**
         * S3 secret key configuration
         */
        @WithName("secret-access-key")
        String secretAccessKey();
    }

}