package com.fzdzzj.lifehabitassistant.server.service;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * S3-compatible object storage backed by the MinIO client. Works with AWS S3,
 * MinIO and other S3-compatible services; the bucket must exist beforehand.
 */
public class S3ExportFileStorage implements ExportFileStorage {
    private static final Logger log = LoggerFactory.getLogger(S3ExportFileStorage.class);

    private final MinioClient client;
    private final String bucket;

    public S3ExportFileStorage(MinioClient client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public String store(String key, byte[] content) {
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .build());
            log.debug("Export file stored in object storage bucket={} key={} bytes={}",
                    bucket, key, content.length);
            return key;
        } catch (Exception ex) {
            throw new ExportFileStorageException("导出文件写入对象存储失败: " + key, ex);
        }
    }

    @Override
    public InputStream load(String key) {
        try {
            return client.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());
        } catch (ErrorResponseException ex) {
            if ("NoSuchKey".equals(ex.errorResponse().code())) {
                throw new ExportFileNotFoundException(key, ex);
            }
            throw new ExportFileStorageException("导出文件读取对象存储失败: " + key, ex);
        } catch (Exception ex) {
            throw new ExportFileStorageException("导出文件读取对象存储失败: " + key, ex);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());
        } catch (ErrorResponseException ex) {
            if (!"NoSuchKey".equals(ex.errorResponse().code())) {
                throw new ExportFileStorageException("导出文件删除对象存储失败: " + key, ex);
            }
        } catch (Exception ex) {
            throw new ExportFileStorageException("导出文件删除对象存储失败: " + key, ex);
        }
    }
}
