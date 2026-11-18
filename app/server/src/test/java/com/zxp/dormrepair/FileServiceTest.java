package com.zxp.dormrepair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxp.dormrepair.domain.Actor;
import com.zxp.dormrepair.file.FileRecord;
import com.zxp.dormrepair.file.FileService;
import com.zxp.dormrepair.file.InMemoryFileRepository;
import com.zxp.dormrepair.file.InMemoryFileStorage;
import com.zxp.dormrepair.statemachine.ActorType;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.ResponseEntity;

class FileServiceTest {

    private FileService files;
    private Actor student;
    private Actor other;

    @BeforeEach
    void setUp() {
        files = new FileService(new InMemoryFileStorage(), new InMemoryFileRepository());
        student = new Actor(ActorType.STUDENT, "S001", "张同学");
        other = new Actor(ActorType.STUDENT, "S002", "李同学");
    }

    @Test
    @DisplayName("真实 PNG 可上传并生成不可预测对象键与校验摘要")
    void acceptsRealPng() throws Exception {
        FileRecord f = files.upload(png("room.png"), student, "BEFORE");
        assertEquals("image/png", f.mime());
        assertEquals("S001", f.uploadedBy());
        assertTrue(f.objectKey().endsWith(".png"));
        assertTrue(f.objectKey().contains("/student/"));
        assertFalse(f.objectKey().contains("room"));
        assertEquals(64, f.sha256().length());
        assertTrue(files.download(f.id()).object().contentLength() > 0);
    }

    @Test
    @DisplayName("受权照片响应禁止浏览器和中间代理缓存")
    void privatePhotoResponseIsNotCacheable() throws Exception {
        FileRecord f = files.upload(png("private.png"), student, "BEFORE");
        var request = new MockHttpServletRequest();
        request.setAttribute(com.zxp.dormrepair.auth.AuthFilter.ATTR, student);
        var orders = org.mockito.Mockito.mock(com.zxp.dormrepair.service.OrderService.class);
        var controller = new com.zxp.dormrepair.controller.FileController(files, orders);

        ResponseEntity<byte[]> response = controller.download(f.id(), request);

        assertEquals("private, no-store, max-age=0", response.getHeaders().getCacheControl());
        assertEquals("no-cache", response.getHeaders().getFirst("Pragma"));
    }

    @Test
    @DisplayName("伪装成 PNG 的文本被拒绝")
    void rejectsSpoofedContent() {
        MockMultipartFile fake = new MockMultipartFile(
                "file", "fake.png", "image/png", "not-an-image".getBytes());
        var e = org.junit.jupiter.api.Assertions.assertThrows(
                com.zxp.dormrepair.common.BizException.class,
                () -> files.upload(fake, student, "BEFORE"));
        assertEquals("FILE_CONTENT_NOT_ALLOWED", e.extra().get("reason"));
    }

    @Test
    @DisplayName("声明 MIME 与实际图片格式不一致时拒绝")
    void rejectsMimeMismatch() throws Exception {
        MockMultipartFile fake = new MockMultipartFile(
                "file", "room.png", "image/jpeg", png("room.png").getBytes());
        var e = org.junit.jupiter.api.Assertions.assertThrows(
                com.zxp.dormrepair.common.BizException.class,
                () -> files.upload(fake, student, "BEFORE"));
        assertEquals("FILE_TYPE_MISMATCH", e.extra().get("reason"));
    }

    @Test
    @DisplayName("对象写入失败时补偿删除已登记元数据")
    void deletesMetadataWhenObjectWriteFails() throws Exception {
        var storage = new InMemoryFileStorage();
        var created = new java.util.concurrent.atomic.AtomicReference<FileRecord>();
        var repository = new InMemoryFileRepository() {
            @Override
            public FileRecord create(String objectKey, String originalName, String mime, long byteSize,
                                     String sha256, String uploadedBy, String purpose) {
                FileRecord record = super.create(objectKey, originalName, mime, byteSize,
                        sha256, uploadedBy, purpose);
                created.set(record);
                return record;
            }
        };
        var failingStorage = new InMemoryFileStorage() {
            @Override
            public void put(String objectKey, java.io.InputStream input, long contentLength,
                            String contentType) {
                throw new IllegalStateException("对象存储暂时不可用");
            }
        };
        var service = new FileService(failingStorage, repository);

        IllegalStateException error = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> service.upload(png("orphan.png"), student, "BEFORE"));

        assertEquals("对象存储暂时不可用", error.getMessage());
        assertEquals(null, created.get() == null ? null : repository.find(created.get().id()).orElse(null));
    }

    private static MockMultipartFile png(String name) throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return new MockMultipartFile("file", name, "image/png", out.toByteArray());
    }
}
