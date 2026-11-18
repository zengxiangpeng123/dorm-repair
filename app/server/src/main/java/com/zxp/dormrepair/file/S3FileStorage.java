package com.zxp.dormrepair.file;

import java.io.InputStream;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

/** S3 兼容对象存储（MinIO / OSS S3 接口 / AWS S3）。桶必须保持私有。 */
@Component
@ConditionalOnProperty(name = "app.file.storage", havingValue = "s3")
public class S3FileStorage implements FileStorage {

    private final S3Client client;
    private final String bucket;

    public S3FileStorage(@Value("${app.file.s3.endpoint:}") String endpoint,
                         @Value("${app.file.s3.region:us-east-1}") String region,
                         @Value("${app.file.s3.bucket:}") String bucket) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("app.file.s3.bucket 不能为空");
        }
        this.bucket = bucket;
        var builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create());
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(java.net.URI.create(endpoint));
        }
        this.client = builder.build();
    }

    @Override
    public void put(String objectKey, InputStream input, long contentLength, String contentType) {
        client.putObject(PutObjectRequest.builder().bucket(bucket).key(objectKey)
                        .contentType(contentType).contentLength(contentLength).build(),
                RequestBody.fromInputStream(input, contentLength));
    }

    @Override
    public Optional<StoredObject> open(String objectKey) {
        try {
            var response = client.getObject(GetObjectRequest.builder().bucket(bucket).key(objectKey).build());
            return Optional.of(new StoredObject(response, response.response().contentLength(),
                    response.response().contentType()));
        } catch (software.amazon.awssdk.services.s3.model.NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    public void delete(String objectKey) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
    }
}
