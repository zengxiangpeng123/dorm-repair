package com.zxp.dormrepair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxp.dormrepair.common.BizException;
import com.zxp.dormrepair.domain.Actor;
import com.zxp.dormrepair.file.FileRecord;
import com.zxp.dormrepair.file.FileService;
import com.zxp.dormrepair.file.InMemoryFileRepository;
import com.zxp.dormrepair.file.InMemoryFileStorage;
import com.zxp.dormrepair.statemachine.ActorType;
import com.zxp.dormrepair.dto.SubmitRequest;
import com.zxp.dormrepair.service.OrderService;
import com.zxp.dormrepair.service.SeedService;
import com.zxp.dormrepair.store.OrderRepository;
import java.util.List;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

@SpringBootTest
class FileBindingTest {

    @Autowired OrderService orders;
    @Autowired SeedService seeder;
    @Autowired OrderRepository orderStore;
    @Autowired InMemoryFileRepository fileRepository;
    @Test
    void uploadedFileCanBeBoundToOrder() throws Exception {
        var repo = new InMemoryFileRepository();
        var service = new FileService(new InMemoryFileStorage(), repo);
        var student = new Actor(ActorType.STUDENT, "S001", "张同学");
        FileRecord f = service.upload(png(), student, "BEFORE");
        repo.bind(f.id(), "BX20261118001", "BEFORE");
        assertEquals("BX20261118001", repo.find(f.id()).orElseThrow().orderNo());
    }

    @Test
    void fileCannotBeReboundToAnotherOrder() throws Exception {
        var repo = new InMemoryFileRepository();
        var service = new FileService(new InMemoryFileStorage(), repo);
        var student = new Actor(ActorType.STUDENT, "S001", "张同学");
        FileRecord f = service.upload(png(), student, "BEFORE");
        repo.bind(f.id(), "BX20261118001", "BEFORE");
        BizException e = assertThrows(BizException.class,
                () -> repo.bind(f.id(), "BX20261118002", "BEFORE"));
        assertEquals("FILE_ALREADY_BOUND", e.extra().get("reason"));
    }

    @Test
    void submissionRollsBackWhenPhotoBindingFails() throws Exception {
        seeder.reset();
        var service = new FileService(new InMemoryFileStorage(), fileRepository);
        var student = new Actor(ActorType.STUDENT, "S001", "张同学");
        FileRecord occupied = service.upload(png(), student, "BEFORE");
        fileRepository.bind(occupied.id(), "BX20261118001", "BEFORE");
        int before = orderStore.size();

        BizException error = assertThrows(BizException.class, () -> orders.submit(
                new SubmitRequest("B7", 4, "402", "门锁松动需要维修", "全天",
                        null, List.of(occupied.id()), null, null, null, null), student));

        assertEquals("FILE_ALREADY_BOUND", error.extra().get("reason"));
        assertEquals(before, orderStore.size());
        assertTrue(orderStore.all().stream().noneMatch(o -> "门锁松动需要维修".equals(o.getDescription())));
    }

    private static MockMultipartFile png() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return new MockMultipartFile("file", "room.png", "image/png", out.toByteArray());
    }
}
