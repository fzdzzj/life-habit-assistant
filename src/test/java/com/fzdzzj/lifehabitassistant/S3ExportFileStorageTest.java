package com.fzdzzj.lifehabitassistant;

import com.fzdzzj.lifehabitassistant.server.service.ExportFileStorageException;
import com.fzdzzj.lifehabitassistant.server.service.ExportFileNotFoundException;
import com.fzdzzj.lifehabitassistant.server.service.S3ExportFileStorage;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3ExportFileStorageTest {

    @Test
    void storeWritesToBucket() throws Exception {
        MinioClient client = mock(MinioClient.class);
        S3ExportFileStorage storage = new S3ExportFileStorage(client, "exports");

        String key = storage.store("export/1.xlsx", new byte[]{1, 2});

        assertEquals("export/1.xlsx", key);
        verify(client).putObject(any(PutObjectArgs.class));
    }

    @Test
    void loadReturnsObjectStream() throws Exception {
        MinioClient client = mock(MinioClient.class);
        GetObjectResponse response = mock(GetObjectResponse.class);
        when(client.getObject(any(GetObjectArgs.class))).thenReturn(response);
        S3ExportFileStorage storage = new S3ExportFileStorage(client, "exports");

        InputStream loaded = storage.load("export/1.xlsx");

        assertSame(response, loaded);
    }

    @Test
    void deleteTreatsNoSuchKeyAsNoOp() throws Exception {
        MinioClient client = mock(MinioClient.class);
        ErrorResponse error = new ErrorResponse("NoSuchKey", "not found", null, null, null, null, null);
        doThrow(new ErrorResponseException(error, null, "request-id"))
                .when(client).removeObject(any(RemoveObjectArgs.class));
        S3ExportFileStorage storage = new S3ExportFileStorage(client, "exports");

        assertDoesNotThrow(() -> storage.delete("export/1.xlsx"));
    }

    @Test
    void loadMissingObjectIsReportedAsNotFound() throws Exception {
        MinioClient client = mock(MinioClient.class);
        ErrorResponse error = new ErrorResponse("NoSuchKey", "not found", null, null, null, null, null);
        when(client.getObject(any(GetObjectArgs.class)))
                .thenThrow(new ErrorResponseException(error, null, "request-id"));
        S3ExportFileStorage storage = new S3ExportFileStorage(client, "exports");

        assertThrows(ExportFileNotFoundException.class, () -> storage.load("export/missing.xlsx"));
    }

    @Test
    void wrapsStorageFailures() throws Exception {
        MinioClient client = mock(MinioClient.class);
        doThrow(new IOException("s3 down")).when(client).putObject(any(PutObjectArgs.class));
        S3ExportFileStorage storage = new S3ExportFileStorage(client, "exports");

        assertThrows(ExportFileStorageException.class, () -> storage.store("export/1.xlsx", new byte[]{1}));
    }
}
