package com.nintails.ecommerce.service;

import com.nintails.ecommerce.dto.ProductImageUploadDto;
import com.nintails.ecommerce.exception.BadRequestException;
import com.nintails.ecommerce.exception.ExternalServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

@Service
@Slf4j
public class ProductImageStorageService {

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    @Value("${app.upload.directory:uploads/products}")
    private String uploadDirectory;

    @Value("${app.upload.public-base-url:http://localhost:8081/api}")
    private String publicBaseUrl;

    public ProductImageUploadDto store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Choose an image to upload.");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new BadRequestException("Each product image must be 5 MB or smaller.");
        }
        String extension = acceptedExtension(file.getContentType());
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new ExternalServiceUnavailableException("The product image could not be read.", exception);
        }
        if (!matchesSignature(content, extension)) {
            throw new BadRequestException("The uploaded file content does not match an allowed image type.");
        }

        String fileName = UUID.randomUUID() + "." + extension;
        Path directory = Path.of(uploadDirectory).toAbsolutePath().normalize();
        Path target = directory.resolve(fileName).normalize();
        if (!target.getParent().equals(directory)) {
            throw new BadRequestException("Invalid image file name.");
        }
        try {
            Files.createDirectories(directory);
            Path temporary = Files.createTempFile(directory, "upload-", ".tmp");
            try {
                Files.write(temporary, content);
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException exception) {
            log.error("Product image storage failed for generated file {}", fileName, exception);
            throw new ExternalServiceUnavailableException("Product image storage is unavailable.", exception);
        }
        return ProductImageUploadDto.builder()
                .url(publicBaseUrl.replaceAll("/+$", "") + "/uploads/products/" + fileName)
                .fileName(fileName)
                .sizeBytes(content.length)
                .build();
    }

    private String acceptedExtension(String contentType) {
        if (contentType == null) {
            throw new BadRequestException("Upload a JPEG, PNG, or WebP image.");
        }
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new BadRequestException("Upload a JPEG, PNG, or WebP image.");
        };
    }

    private boolean matchesSignature(byte[] bytes, String extension) {
        return switch (extension) {
            case "jpg" -> bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF
                    && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF;
            case "png" -> bytes.length >= 8 && (bytes[0] & 0xFF) == 0x89
                    && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47
                    && bytes[4] == 0x0D && bytes[5] == 0x0A
                    && bytes[6] == 0x1A && bytes[7] == 0x0A;
            case "webp" -> bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I'
                    && bytes[2] == 'F' && bytes[3] == 'F'
                    && bytes[8] == 'W' && bytes[9] == 'E'
                    && bytes[10] == 'B' && bytes[11] == 'P';
            default -> false;
        };
    }
}
