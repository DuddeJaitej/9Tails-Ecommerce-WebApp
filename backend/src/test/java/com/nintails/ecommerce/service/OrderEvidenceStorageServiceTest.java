package com.nintails.ecommerce.service;

import com.nintails.ecommerce.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderEvidenceStorageServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsVideoAtTwoMinuteLimit() {
        OrderEvidenceStorageService service = service();
        MockMultipartFile video = new MockMultipartFile(
                "file", "evidence.mp4", "video/mp4", mp4WithDuration(120_000));

        assertTrue(service.store(video).getUrl().contains("/uploads/order-evidence/"));
    }

    @Test
    void rejectsVideoLongerThanTwoMinutes() {
        OrderEvidenceStorageService service = service();
        MockMultipartFile video = new MockMultipartFile(
                "file", "evidence.mp4", "video/mp4", mp4WithDuration(120_001));

        assertThrows(BadRequestException.class, () -> service.store(video));
    }

    private OrderEvidenceStorageService service() {
        OrderEvidenceStorageService service = new OrderEvidenceStorageService();
        ReflectionTestUtils.setField(service, "uploadDirectory",
                temporaryDirectory.resolve("evidence").toString());
        ReflectionTestUtils.setField(service, "publicBaseUrl", "http://localhost:8081/api");
        return service;
    }

    private byte[] mp4WithDuration(int durationMillis) {
        ByteBuffer data = ByteBuffer.allocate(52);
        data.putInt(16).put("ftyp".getBytes(StandardCharsets.US_ASCII))
                .put("isom".getBytes(StandardCharsets.US_ASCII)).putInt(0);
        data.putInt(36).put("moov".getBytes(StandardCharsets.US_ASCII));
        data.putInt(28).put("mvhd".getBytes(StandardCharsets.US_ASCII));
        data.putInt(0).putInt(0).putInt(0).putInt(1000).putInt(durationMillis);
        return data.array();
    }
}
