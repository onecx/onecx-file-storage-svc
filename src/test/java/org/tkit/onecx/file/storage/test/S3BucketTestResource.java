package org.tkit.onecx.file.storage.test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Map;

import org.eclipse.microprofile.config.ConfigProvider;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;

public class S3BucketTestResource implements QuarkusTestResourceLifecycleManager {

    private S3Client s3Client;

    @Override
    public Map<String, String> start() {
        // Use test config if present, otherwise fallback to localhost:4566
        String endpoint = ConfigProvider.getConfig().getOptionalValue("onecx.file.storage.s3-client.endpoint", String.class)
                .orElse("http://localhost:4566");
        String accessKey = ConfigProvider.getConfig()
                .getOptionalValue("onecx.file.storage.s3-client.access-key-id", String.class)
                .orElse("rustfsadmin");
        String secretKey = ConfigProvider.getConfig()
                .getOptionalValue("onecx.file.storage.s3-client.secret-access-key", String.class)
                .orElse("rustfsadmin");
        String region = ConfigProvider.getConfig().getOptionalValue("onecx.file.storage.s3-client.region", String.class)
                .orElse("eu-central-1");
        String bucket = ConfigProvider.getConfig().getOptionalValue("onecx.file.storage.bucket", String.class)
                .orElse("test-bucket");

        System.out.println("[S3BucketTestResource] starting - endpoint=" + endpoint + " bucket=" + bucket);

        // Wait for LocalStack health endpoint to be ready to avoid initial 500/unknown-operation logs
        // Longer timeout to handle slower container startup in CI
        boolean ready = waitForLocalstackHealth(endpoint, 60);
        if (!ready) {
            System.out.println(
                    "[S3BucketTestResource] LocalStack health check failed or timed out; proceeding anyway (may cause errors)");
        }

        s3Client = S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();

        int maxAttempts = 8;
        int attempt = 0;
        boolean bucketReady = false;

        while (attempt < maxAttempts && !bucketReady) {
            attempt++;
            try {
                // First try HeadBucket to see if it already exists
                s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
                System.out.println("[S3BucketTestResource] bucket already exists: " + bucket + " (attempt " + attempt + ")");
                bucketReady = true;
                break;
            } catch (NoSuchBucketException nsb) {
                // bucket does not exist -> try create
                try {
                    s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                    System.out.println("[S3BucketTestResource] bucket created: " + bucket + " (attempt " + attempt + ")");
                    bucketReady = true;
                    break;
                } catch (S3Exception se) {
                    // If bucket already owned by you, treat as success
                    String code = se.awsErrorDetails() != null ? se.awsErrorDetails().errorCode() : null;
                    int status = se.statusCode();
                    if ("BucketAlreadyOwnedByYou".equals(code) || status == 409) {
                        System.out.println("[S3BucketTestResource] bucket exists (BucketAlreadyOwnedByYou): " + bucket
                                + " (attempt " + attempt + ")");
                        bucketReady = true;
                        break;
                    }
                    System.out.println(
                            "[S3BucketTestResource] createBucket failed (attempt " + attempt + "): " + se.getMessage());
                    // fallthrough to retry
                } catch (SdkClientException sce) {
                    System.out.println(
                            "[S3BucketTestResource] createBucket client error (attempt " + attempt + "): " + sce.getMessage());
                }
            } catch (SdkClientException sce) {
                System.out.println(
                        "[S3BucketTestResource] headBucket client error (attempt " + attempt + "): " + sce.getMessage());
            } catch (S3Exception se) {
                // Other S3 exception codes -> if 409 treat as success
                String code = se.awsErrorDetails() != null ? se.awsErrorDetails().errorCode() : null;
                int status = se.statusCode();
                if ("BucketAlreadyOwnedByYou".equals(code) || status == 409) {
                    System.out.println("[S3BucketTestResource] bucket exists (create returned 409): " + bucket + " (attempt "
                            + attempt + ")");
                    bucketReady = true;
                    break;
                }
                System.out
                        .println("[S3BucketTestResource] headBucket S3Exception (attempt " + attempt + "): " + se.getMessage());
            }

            // wait before retrying
            try {
                Thread.sleep(300);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (!bucketReady) {
            System.out.println("[S3BucketTestResource] giving up creating/checking bucket after " + attempt
                    + " attempts. Continuing tests may fail if S3 is not available.");
        }

        // Return config overrides for Quarkus runtime so the application's S3 client
        // and our FileStorageConfig mapping use the same LocalStack endpoint and credentials during tests.
        return Map.of(
                // Quarkus S3 extension
                "quarkus.s3.endpoint-override", endpoint,
                "quarkus.s3.aws.credentials.static-provider.access-key-id", accessKey,
                "quarkus.s3.aws.credentials.static-provider.secret-access-key", secretKey,
                "quarkus.s3.aws.region", region,
                "quarkus.s3.path-style-access", "true",
                // onecx file storage config (in case code reads these keys directly)
                "onecx.file.storage.s3-client.endpoint", endpoint,
                "onecx.file.storage.s3-client.access-key-id", accessKey,
                "onecx.file.storage.s3-client.secret-access-key", secretKey,
                "onecx.file.storage.s3-client.region", region);
    }

    private boolean waitForLocalstackHealth(String endpoint, int timeoutSeconds) {
        String[] paths = { "/_localstack/health", "/health", "/" };
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            for (String p : paths) {
                try {
                    String url = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) + p
                            : endpoint + p;
                    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(2000);
                    conn.setReadTimeout(2000);
                    int code = conn.getResponseCode();
                    if (code == 200) {
                        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                            String line;
                            StringBuilder sb = new StringBuilder();
                            while ((line = br.readLine()) != null) {
                                sb.append(line);
                            }
                            String body = sb.toString().toLowerCase();
                            if (body.contains("s3")
                                    && (body.contains("running") || body.contains("ready") || body.contains("available"))) {
                                return true;
                            }
                        } catch (Exception e) {
                            // ignore parsing errors
                            return true; // 200 is good enough
                        }
                    }
                } catch (Exception e) {
                    // ignore and retry
                }
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    @Override
    public void stop() {
        if (s3Client != null) {
            try {
                s3Client.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }
}
