package com.zxp.dormrepair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.zxp.dormrepair.domain.Actor;
import com.zxp.dormrepair.domain.AiReviewTask;
import com.zxp.dormrepair.domain.AiReviewTaskStatus;
import com.zxp.dormrepair.domain.WorkOrder;
import com.zxp.dormrepair.dto.SubmitRequest;
import com.zxp.dormrepair.service.AiClient;
import com.zxp.dormrepair.service.AiReviewService;
import com.zxp.dormrepair.service.OrderService;
import com.zxp.dormrepair.statemachine.ActorType;
import com.zxp.dormrepair.statemachine.OrderStatus;
import com.zxp.dormrepair.store.AiReviewTaskRepository;
import com.zxp.dormrepair.store.OrderRepository;
import com.zxp.dormrepair.file.FileService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.mock.web.MockMultipartFile;

@SpringBootTest(properties = "app.order.auto-assign=false")
class AiReviewAuditTest {

    @Autowired OrderService orders;
    @Autowired AiReviewService reviews;
    @Autowired OrderRepository store;
    @Autowired AiReviewTaskRepository tasks;
    @Autowired FileService fileService;
    @MockitoBean AiClient ai;

    private Actor student;

    @BeforeEach
    void setUp() {
        tasks.clear();
        store.clear();
        student = new Actor(ActorType.STUDENT, "S001", "张同学");
    }

    @Test
    @DisplayName("中断的 AI 复核任务会被标记失败，不覆盖规则定级")
    void staleTaskIsFailedWithoutTouchingOrder() {
        when(ai.classifyFast(org.mockito.ArgumentMatchers.anyString())).thenReturn(
                new AiClient.Verdict("门窗家具", "普通", 0.8, "规则定级", false, "AI",
                        "test-model", 1, null, null, 1L));
        String photoId;
        try {
            BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(image, "png", bytes);
            photoId = fileService.upload(new MockMultipartFile("file", "stale.png", "image/png",
                    bytes.toByteArray()), student, "BEFORE").id();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        var order = orders.submit(new SubmitRequest("B7", 4, "402", "门把手松动",
                "全天", null, List.of(photoId), null, null, null, null), student);
        var repository = new com.zxp.dormrepair.store.InMemoryAiReviewTaskRepository();
        var task = repository.create("BX-STALE", 1, "hash", "model", "provider");
        var orderBefore = store.get(order.no());
        assertNotNull(orderBefore);

        int changed = repository.failStale("2099-01-01 00:00:00",
                "AI_REVIEW_ABANDONED", "超时未完成");

        assertEquals(1, changed);
        var failed = repository.find(task.id()).orElseThrow();
        assertEquals(com.zxp.dormrepair.domain.AiReviewTaskStatus.FAILED, failed.status());
        assertEquals("AI_REVIEW_ABANDONED", failed.errorCode());
        assertEquals(orderBefore.getLevel(), store.get(order.no()).getLevel());
    }

    @Test
    @DisplayName("成功复核只改定级字段，并记录模型、版本、摘要和 applied")
    void appliesAuditedResult() {
        when(ai.model()).thenReturn("raccoon/sn-deepseek-v4-1-flash-1017");
        when(ai.provider()).thenReturn("agent2api");
        when(ai.classifyFast(org.mockito.ArgumentMatchers.anyString())).thenReturn(
                new AiClient.Verdict("给排水", "较急", 0.5, "规则", false, "AI",
                        "test-model", 1, null, null, 1));
        when(ai.classify(org.mockito.ArgumentMatchers.anyString())).thenReturn(
                new AiClient.Verdict("门窗家具", "普通", 0.88, "模型判断", false, "AI",
                        "raccoon/sn-deepseek-v4-1-flash-1017", 1, null, null, 1200));
        WorkOrder order = submit("衣柜门关不上，门缝很大");
        AiReviewTask task = tasks.create(order.getNo(), order.getVersion(), "hash",
                "raccoon/sn-deepseek-v4-1-flash-1017", "agent2api");

        reviews.runTask(task, order.getDescription());

        AiReviewTask saved = tasks.find(task.id()).orElseThrow();
        assertEquals(AiReviewTaskStatus.SUCCEEDED, saved.status());
        assertTrue(saved.applied());
        assertEquals("raccoon/sn-deepseek-v4-1-flash-1017", saved.model());
        assertEquals("agent2api", saved.provider());
        WorkOrder updated = store.require(order.getNo());
        assertEquals("门窗家具", updated.getCategory());
        assertEquals(OrderStatus.PENDING, updated.getStatus());
    }

    @Test
    @DisplayName("任务创建后工单版本变化：晚到结果标记 superseded，不覆盖新定级")
    void staleTaskDoesNotOverwrite() {
        when(ai.model()).thenReturn("model-x");
        when(ai.provider()).thenReturn("provider-x");
        when(ai.classifyFast(org.mockito.ArgumentMatchers.anyString())).thenReturn(
                new AiClient.Verdict("给排水", "普通", 0.5, "规则", false, "AI",
                        "test-model", 1, null, null, 1));
        when(ai.classify(org.mockito.ArgumentMatchers.anyString())).thenReturn(
                new AiClient.Verdict("电气", "紧急", 0.9, "晚到结果", false, "AI",
                        "model-x", 1, null, null, 500));
        WorkOrder order = submit("门把手有点松");
        AiReviewTask task = tasks.create(order.getNo(), order.getVersion(), "hash", "model-x", "provider-x");
        store.update(order.getNo(), order.getVersion(), o -> o.setLevel("较急"));

        reviews.runTask(task, order.getDescription());

        AiReviewTask saved = tasks.find(task.id()).orElseThrow();
        assertEquals(AiReviewTaskStatus.SUPERSEDED, saved.status());
        assertFalse(saved.applied());
        assertEquals("较急", store.require(order.getNo()).getLevel());
    }

    @Test
    @DisplayName("提交链路创建任务时使用自动派工后的最终版本")
    void submitUsesFinalVersionForAuditTask() {
        when(ai.model()).thenReturn("model-x");
        when(ai.provider()).thenReturn("provider-x");
        when(ai.classifyFast(org.mockito.ArgumentMatchers.anyString())).thenReturn(
                new AiClient.Verdict("给排水", "普通", 0.5, "规则", false, "AI",
                        "test-model", 1, null, null, 1));
        when(ai.classify(org.mockito.ArgumentMatchers.anyString())).thenReturn(
                new AiClient.Verdict("给排水", "较急", 0.0, "模型不可用", true, "FALLBACK",
                        "model-x", 0, "AI_TIMEOUT", "timeout", 1000));

        WorkOrder order = submitWithoutCategory("门把手有点松");
        AiReviewTask task = tasks.latest(order.getNo()).orElseThrow();

        assertEquals(order.getVersion(), task.orderVersion());
    }

    @Test
    @DisplayName("模型兜底失败保留规则结果，但任务仍可审计")
    void fallbackTaskIsAudited() {
        when(ai.model()).thenReturn("model-x");
        when(ai.provider()).thenReturn("provider-x");
        when(ai.classifyFast(org.mockito.ArgumentMatchers.anyString())).thenReturn(
                new AiClient.Verdict("给排水", "普通", 0.5, "规则", false, "AI",
                        "test-model", 1, null, null, 1));
        when(ai.classify(org.mockito.ArgumentMatchers.anyString())).thenReturn(
                new AiClient.Verdict("给排水", "较急", 0.0, "模型不可用", true, "FALLBACK",
                        "model-x", 0, "AI_TIMEOUT", "timeout", 1000));
        WorkOrder order = submit("门把手有点松");
        AiReviewTask task = tasks.create(order.getNo(), order.getVersion(), "hash", "model-x", "provider-x");

        reviews.runTask(task, order.getDescription());

        AiReviewTask saved = tasks.find(task.id()).orElseThrow();
        assertEquals(AiReviewTaskStatus.FALLBACK, saved.status());
        assertEquals("AI_TIMEOUT", saved.errorCode());
        assertFalse(saved.applied());
        assertEquals("普通", store.require(order.getNo()).getLevel());
    }

    @Test
    @DisplayName("事务未提交时不创建 AI 复核任务，提交后才投递")
    void reviewWaitsForTransactionCommit() {
        when(ai.model()).thenReturn("model-x");
        when(ai.provider()).thenReturn("provider-x");
        when(ai.classify(org.mockito.ArgumentMatchers.anyString())).thenReturn(
                new AiClient.Verdict("门窗家具", "普通", 0.9, "模型", false, "AI",
                        "model-x", 1, null, null, 1));

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            reviews.review("TX-PENDING", 1, "门把手松动");
            assertEquals(java.util.Optional.empty(), tasks.latest("TX-PENDING"));
            for (var sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }
            AiReviewTask task = tasks.latest("TX-PENDING").orElseThrow();
            assertEquals(AiReviewTaskStatus.PENDING, task.status());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private WorkOrder submit(String desc) {
        orders.submit(new SubmitRequest("B7", 4, "402", desc, "全天",
                List.of("demo://x.jpg"), null, "给排水", "普通", null, null), student);
        return store.all().stream().filter(o -> desc.equals(o.getDescription())).findFirst().orElseThrow();
    }

    private WorkOrder submitWithoutCategory(String desc) {
        orders.submit(new SubmitRequest("B7", 4, "402", desc, "全天",
                List.of("demo://x.jpg"), null, null, null, null, null), student);
        return store.all().stream().filter(o -> desc.equals(o.getDescription())).findFirst().orElseThrow();
    }
}
