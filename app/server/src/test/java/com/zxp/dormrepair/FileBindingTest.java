package com.zxp.dormrepair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zxp.dormrepair.common.BizException;
import com.zxp.dormrepair.domain.Actor;
import com.zxp.dormrepair.file.FileRecord;
import com.zxp.dormrepair.file.FileService;
import com.zxp.dormrepair.file.InMemoryFileRepository;
import com.zxp.dormrepair.file.InMemoryFileStorage;
import com.zxp.dormrepair.statemachine.ActorType;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class FileBindingTest {
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

    private static MockMultipartFile png() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return new MockMultipartFile("file", "room.png", "image/png", out.toByteArray());
    }
}
