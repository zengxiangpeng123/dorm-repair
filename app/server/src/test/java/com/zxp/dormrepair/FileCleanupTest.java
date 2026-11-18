package com.zxp.dormrepair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxp.dormrepair.domain.Actor;
import com.zxp.dormrepair.file.FileCleanupService;
import com.zxp.dormrepair.file.FileRecord;
import com.zxp.dormrepair.file.FileService;
import com.zxp.dormrepair.file.InMemoryFileRepository;
import com.zxp.dormrepair.file.InMemoryFileStorage;
import com.zxp.dormrepair.statemachine.ActorType;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class FileCleanupTest {

    @Test
    @DisplayName("超期未绑定文件被清理，已绑定文件保留")
    void cleansOnlyOldUnboundFiles() throws Exception {
        var repo = new InMemoryFileRepository();
        var storage = new InMemoryFileStorage();
        var files = new FileService(storage, repo);
        var cleanup = new FileCleanupService(repo, storage, 1, 100);
        Actor student = new Actor(ActorType.STUDENT, "S001", "张同学");

        FileRecord old = files.upload(png("old.png"), student, "BEFORE");
        FileRecord bound = files.upload(png("bound.png"), student, "BEFORE");
        repo.bind(bound.id(), "BX20261118001", "BEFORE");

        var result = cleanup.sweep("2099-01-01 00:00:00");

        assertEquals(1, result.get("deleted"));
        assertNull(repo.find(old.id()).orElse(null));
        assertNotNull(repo.find(bound.id()).orElse(null));
    }

    @Test
    @DisplayName("进程崩溃遗留的过期清理租约会被回收")
    void releasesStaleCleanupClaim() throws Exception {
        var repo = new InMemoryFileRepository();
        var storage = new InMemoryFileStorage();
        var files = new FileService(storage, repo);
        var cleanup = new FileCleanupService(repo, storage, 1, 100);
        Actor student = new Actor(ActorType.STUDENT, "S001", "张同学");
        FileRecord stale = files.upload(png("stale.png"), student, "BEFORE");

        assertTrue(repo.claimForCleanup(stale.id(), "2020-01-01 00:00:00"));
        var result = cleanup.sweep("2099-01-01 00:00:00");

        assertEquals(1, result.get("recoveredClaims"));
        assertEquals(1, result.get("deleted"));
        assertNull(repo.find(stale.id()).orElse(null));
    }

    @Test
    @DisplayName("对象已不存在时清理仍完成元数据收口")
    void cleanupCompletesWhenObjectAlreadyMissing() throws Exception {
        var repo = new InMemoryFileRepository();
        var storage = new InMemoryFileStorage();
        var files = new FileService(storage, repo);
        var cleanup = new FileCleanupService(repo, storage, 1, 100);
        Actor student = new Actor(ActorType.STUDENT, "S001", "张同学");
        FileRecord old = files.upload(png("already-gone.png"), student, "BEFORE");
        storage.delete(old.objectKey());

        var result = cleanup.sweep("2099-01-01 00:00:00");

        assertEquals(1, result.get("deleted"));
        assertNull(repo.find(old.id()).orElse(null));
    }

    @Test
    @DisplayName("对象删除失败时释放租约，下一轮可重试")
    void failedObjectDeleteIsRetryable() throws Exception {
        var repo = new InMemoryFileRepository();
        var storage = new InMemoryFileStorage() {
            @Override
            public void delete(String objectKey) {
                throw new IllegalStateException("对象存储暂时不可用");
            }
        };
        var files = new FileService(storage, repo);
        var cleanup = new FileCleanupService(repo, storage, 1, 100);
        Actor student = new Actor(ActorType.STUDENT, "S001", "张同学");
        FileRecord old = files.upload(png("retry.png"), student, "BEFORE");

        var result = cleanup.sweep("2099-01-01 00:00:00");

        assertEquals(1, result.get("failed"));
        assertNotNull(repo.find(old.id()).orElse(null));
        assertTrue(repo.claimForCleanup(old.id(), "2099-01-02 00:00:00"));
    }

    private static MockMultipartFile png(String name) throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return new MockMultipartFile("file", name, "image/png", out.toByteArray());
    }
}
