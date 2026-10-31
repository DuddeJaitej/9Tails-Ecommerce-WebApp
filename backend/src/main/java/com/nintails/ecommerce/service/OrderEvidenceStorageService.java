package com.nintails.ecommerce.service;

import com.nintails.ecommerce.dto.ProductImageUploadDto;
import com.nintails.ecommerce.exception.BadRequestException;
import com.nintails.ecommerce.exception.ExternalServiceUnavailableException;
import com.nintails.ecommerce.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

@Service
@Slf4j
public class OrderEvidenceStorageService {

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final long MAX_VIDEO_BYTES = 100L * 1024 * 1024;

    @Value("${app.upload.evidence-directory:uploads/order-evidence}")
    private String uploadDirectory;

    @Value("${app.upload.public-base-url:http://localhost:8081/api}")
    private String publicBaseUrl;

    public ProductImageUploadDto store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Choose a photo or video to upload.");
        }
        String contentType = file.getContentType() == null
                ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        boolean video = contentType.equals("video/mp4");
        long maxBytes = video ? MAX_VIDEO_BYTES : MAX_IMAGE_BYTES;
        if (file.getSize() > maxBytes) {
            throw new BadRequestException(video
                    ? "The return video must be 100 MB or smaller."
                    : "The return photo must be 5 MB or smaller.");
        }
        String extension = acceptedExtension(contentType);
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new ExternalServiceUnavailableException("The order evidence could not be read.", exception);
        }
        if (!matchesSignature(content, extension)) {
            throw new BadRequestException("The uploaded file content does not match an allowed photo or MP4 video.");
        }
        if (video && mp4DurationSeconds(content) > 120) {
            throw new BadRequestException("The return video must be no longer than 2 minutes.");
        }

        String fileName = UUID.randomUUID() + "." + extension;
        Path directory = Path.of(uploadDirectory).toAbsolutePath().normalize();
        Path target = directory.resolve(fileName).normalize();
        if (!target.getParent().equals(directory)) {
            throw new BadRequestException("Invalid evidence file name.");
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
            log.error("Order evidence storage failed for generated file {}", fileName, exception);
            throw new ExternalServiceUnavailableException("Order evidence storage is unavailable.", exception);
        }
        return ProductImageUploadDto.builder()
                .url(publicBaseUrl.replaceAll("/+$", "") + "/uploads/order-evidence/" + fileName)
                .fileName(fileName)
                .sizeBytes(content.length)
                .build();
    }

    public Resource load(String fileName) {
        if (fileName == null || !fileName.matches("^[a-fA-F0-9-]+\\.(jpg|png|webp|mp4)$")) {
            throw new BadRequestException("Invalid return evidence file.");
        }
        Path directory = Path.of(uploadDirectory).toAbsolutePath().normalize();
        Path file = directory.resolve(fileName).normalize();
        if (!file.getParent().equals(directory) || !Files.isRegularFile(file) || !Files.isReadable(file)) {
            throw new ResourceNotFoundException("Return evidence not found");
        }
        try {
            return new UrlResource(file.toUri());
        } catch (IOException exception) {
            throw new ExternalServiceUnavailableException("Return evidence could not be opened.", exception);
        }
    }

    private String acceptedExtension(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "video/mp4" -> "mp4";
            default -> throw new BadRequestException("Upload a JPEG, PNG, WebP photo or MP4 video.");
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
            case "mp4" -> bytes.length >= 12 && bytes[4] == 'f' && bytes[5] == 't'
                    && bytes[6] == 'y' && bytes[7] == 'p';
            default -> false;
        };
    }

    private double mp4DurationSeconds(byte[] bytes) {
        Box moov = findBox(bytes, 0, bytes.length, "moov");
        Box mvhd = moov == null ? null : findBox(bytes, moov.contentStart(), moov.end(), "mvhd");
        if (mvhd == null || mvhd.end() - mvhd.contentStart() < 20) {
            throw new BadRequestException("The MP4 video duration could not be verified.");
        }
        int contentStart = mvhd.contentStart();
        int version = bytes[contentStart] & 0xFF;
        ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        long timeScale;
        double duration;
        if (version == 0) {
            timeScale = Integer.toUnsignedLong(data.getInt(contentStart + 12));
            duration = Integer.toUnsignedLong(data.getInt(contentStart + 16));
        } else if (version == 1 && mvhd.end() - contentStart >= 32) {
            timeScale = Integer.toUnsignedLong(data.getInt(contentStart + 20));
            long durationUnits = data.getLong(contentStart + 24);
            if (durationUnits <= 0) {
                throw new BadRequestException("The MP4 video duration could not be verified.");
            }
            duration = durationUnits;
        } else {
            throw new BadRequestException("The MP4 video duration could not be verified.");
        }
        if (timeScale == 0 || duration <= 0) {
            throw new BadRequestException("The MP4 video duration could not be verified.");
        }
        return duration / timeScale;
    }

    private Box findBox(byte[] bytes, int start, int end, String targetType) {
        int offset = start;
        while (offset <= end - 8) {
            long size = Integer.toUnsignedLong(ByteBuffer.wrap(bytes, offset, 4)
                    .order(ByteOrder.BIG_ENDIAN).getInt());
            String type = new String(bytes, offset + 4, 4, java.nio.charset.StandardCharsets.US_ASCII);
            int headerSize = 8;
            if (size == 1) {
                if (offset > end - 16) return null;
                size = ByteBuffer.wrap(bytes, offset + 8, 8).order(ByteOrder.BIG_ENDIAN).getLong();
                headerSize = 16;
            } else if (size == 0) {
                size = end - offset;
            }
            if (size < headerSize || size > end - (long) offset) return null;
            int boxEnd = (int) (offset + size);
            if (type.equals(targetType)) {
                return new Box(offset + headerSize, boxEnd);
            }
            offset = boxEnd;
        }
        return null;
    }

    private record Box(int contentStart, int end) {}
}
