package com.nintails.ecommerce.service;

import com.nintails.ecommerce.dto.ProductImageUploadDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductImageStorageServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void storesImageAndReturnsPubliclyServedUrl() throws Exception {
        ProductImageStorageService service = new ProductImageStorageService();
        ReflectionTestUtils.setField(service, "uploadDirectory",
                temporaryDirectory.resolve("products").toString());
        ReflectionTestUtils.setField(service, "publicBaseUrl", "http://localhost:8081/api");
        byte[] imageBytes = {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00
        };
        MockMultipartFile upload = new MockMultipartFile(
                "file", "product.png", "image/png", imageBytes);

        ProductImageUploadDto result = service.store(upload);

        assertEquals("http://localhost:8081/api/uploads/products/" + result.getFileName(),
                result.getUrl());
        assertArrayEquals(imageBytes, Files.readAllBytes(
                temporaryDirectory.resolve("products").resolve(result.getFileName())));
    }
}
