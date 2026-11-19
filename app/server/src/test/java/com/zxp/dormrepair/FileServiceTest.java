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

        ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> response =
                controller.download(f.id(), request);

        assertEquals("private, no-store, max-age=0", response.getHeaders().getCacheControl());
        assertEquals("no-cache", response.getHeaders().getFirst("Pragma"));
        assertEquals(f.byteSize(), response.getHeaders().getContentLength());
        var output = new ByteArrayOutputStream();
        response.getBody().writeTo(output);
        assertEquals(f.byteSize(), output.size());
    }

    @Test
    @DisplayName("学生不能读取他人未绑定照片")
    void studentCannotReadAnotherUsersUnboundPhoto() throws Exception {
        FileRecord f = files.upload(png("private-owner.png"), student, "BEFORE");
        var request = new MockHttpServletRequest();
        request.setAttribute(com.zxp.dormrepair.auth.AuthFilter.ATTR, other);
        var orders = org.mockito.Mockito.mock(com.zxp.dormrepair.service.OrderService.class);
        var controller = new com.zxp.dormrepair.controller.FileController(files, orders);

        var e = org.junit.jupiter.api.Assertions.assertThrows(
                com.zxp.dormrepair.common.BizException.class,
                () -> controller.download(f.id(), request));

        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, e.status());
    }

    @Test
    @DisplayName("已绑定照片元数据同样由工单访问权限控制")
    void boundPhotoMetadataUsesOrderVisibility() throws Exception {
        var repository = new InMemoryFileRepository();
        var service = new FileService(new InMemoryFileStorage(), repository);
        FileRecord bound = service.upload(png("bound-meta.png"), student, "BEFORE");
        repository.bind(bound.id(), "BX20261119002", "BEFORE");
        var request = new MockHttpServletRequest();
        request.setAttribute(com.zxp.dormrepair.auth.AuthFilter.ATTR, other);
        var orders = org.mockito.Mockito.mock(com.zxp.dormrepair.service.OrderService.class);
        org.mockito.Mockito.when(orders.detailForActor("BX20261119002", other))
                .thenThrow(com.zxp.dormrepair.common.BizException.forbidden("不能查看不属于你的工单"));
        var controller = new com.zxp.dormrepair.controller.FileController(service, orders);

        var e = org.junit.jupiter.api.Assertions.assertThrows(
                com.zxp.dormrepair.common.BizException.class,
                () -> controller.metadata(bound.id(), request));

        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, e.status());
    }

    @Test
    @DisplayName("已绑定照片由工单访问权限控制")
    void boundPhotoUsesOrderVisibility() throws Exception {
        var repository = new InMemoryFileRepository();
        var service = new FileService(new InMemoryFileStorage(), repository);
        FileRecord bound = service.upload(png("bound-real.png"), student, "BEFORE");
        repository.bind(bound.id(), "BX20261119001", "BEFORE");
        var request = new MockHttpServletRequest();
        request.setAttribute(com.zxp.dormrepair.auth.AuthFilter.ATTR, other);
        var orders = org.mockito.Mockito.mock(com.zxp.dormrepair.service.OrderService.class);
        org.mockito.Mockito.when(orders.detailForActor("BX20261119001", other))
                .thenThrow(com.zxp.dormrepair.common.BizException.forbidden("不能查看不属于你的工单"));
        var controller = new com.zxp.dormrepair.controller.FileController(service, orders);

        var e = org.junit.jupiter.api.Assertions.assertThrows(
                com.zxp.dormrepair.common.BizException.class,
                () -> controller.download(bound.id(), request));

        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, e.status());
    }

    @Test
    @DisplayName("同一上传者重复上传同一图片返回同一文件，不生成重复对象")
    void duplicateUploadReusesExistingFile() throws Exception {
        FileRecord first = files.upload(png("same.png"), student, "BEFORE");
        FileRecord second = files.upload(png("same-copy.png"), student, "BEFORE");

        assertEquals(first.id(), second.id());
        assertEquals(first.objectKey(), second.objectKey());
    }

    @Test
    @DisplayName("不同上传者的相同图片仍各自保留")
    void sameImageForDifferentUploadersStaysSeparate() throws Exception {
        FileRecord mine = files.upload(png("same.png"), student, "BEFORE");
        FileRecord theirs = files.upload(png("same.png"), other, "BEFORE");

        assertFalse(mine.id().equals(theirs.id()));
    }

    @Test
    @DisplayName("图片绑定后再次上传会生成新记录，不复活已绑定文件")
    void boundImageDoesNotBlockNewUpload() throws Exception {
        var repository = new InMemoryFileRepository();
        var service = new FileService(new InMemoryFileStorage(), repository);
        FileRecord bound = service.upload(png("first.png"), student, "BEFORE");
        repository.bind(bound.id(), "BX20261119001", "BEFORE");

        FileRecord next = service.upload(png("again.png"), student, "BEFORE");

        assertFalse(bound.id().equals(next.id()));
        assertEquals(null, next.orderNo());
    }

    @Test
    @DisplayName("并发上传同一图片只登记一个文件")
    void concurrentUploadsShareOneFile() throws Exception {
        var repository = new InMemoryFileRepository();
        var service = new FileService(new InMemoryFileStorage(), repository);
        var bytes = png("race.png").getBytes();
        var ready = new java.util.concurrent.CountDownLatch(8);
        var start = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        try {
            var tasks = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(i -> (java.util.concurrent.Callable<FileRecord>) () -> {
                        ready.countDown();
                        start.await();
                        return service.upload(new MockMultipartFile(
                                "file", "race-" + i + ".png", "image/png", bytes), student, "BEFORE");
                    }).toList();
            var futures = tasks.stream().map(pool::submit).toList();
            ready.await();
            start.countDown();
            var ids = new java.util.HashSet<String>();
            for (var future : futures) ids.add(future.get().id());
            assertEquals(1, ids.size());
        } finally {
            pool.shutdownNow();
        }
    }
    @Test
    @DisplayName("并发请求不会覆盖已经存在的对象字节")
    void concurrentWriterDoesNotOverwriteExistingObject() throws Exception {
        var storage = new InMemoryFileStorage();
        byte[] first = {1, 2, 3};
        byte[] second = {9, 8, 7};
        assertTrue(storage.putIfAbsent("same-key", new java.io.ByteArrayInputStream(first), first.length, "image/png"));
        assertFalse(storage.putIfAbsent("same-key", new java.io.ByteArrayInputStream(second), second.length, "image/png"));
        var stored = storage.open("same-key").orElseThrow();
        assertEquals(1, stored.stream().readAllBytes()[0]);
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
            public boolean putIfAbsent(String objectKey, java.io.InputStream input, long contentLength,
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
