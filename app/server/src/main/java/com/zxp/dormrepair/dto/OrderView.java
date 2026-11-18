package com.zxp.dormrepair.dto;

import com.zxp.dormrepair.domain.OrderEventLog;
import com.zxp.dormrepair.domain.ReworkReview;
import com.zxp.dormrepair.domain.WorkOrder;
import com.zxp.dormrepair.statemachine.StateMachine;
import java.math.BigDecimal;
import java.util.List;

/**
 * 给前端的工单视图。字段名就是契约，改名前先改 SYS-003。
 */
public record OrderView(
        String no,
        String status,
        String statusLabel,
        int version,
        String building,
        int floor,
        String room,
        String location,
        String studentName,
        String desc,
        String window,
        List<String> photosBefore,
        List<String> photosAfter,
        List<String> photoIdsBefore,
        List<String> photoIdsAfter,
        String category,
        String level,
        String levelSource,
        String aiBasis,
        String workerId,
        String workerName,
        String partsName,
        BigDecimal partsAmount,
        String partsEta,
        String createdAt,
        String assignedAt,
        String arriveAt,
        String finishAt,
        String signAt,
        String closedAt,
        String cancelledAt,
        String statusAt,
        String reworkOf,
        Integer reworkIndex,
        int reworkCount,
        List<String> allowed,
        List<OrderEventLog> events,
        CancelRequest cancelRequest,
        ReworkReviewView reworkReview) {

    public record ReworkReviewView(
            long id,
            int attemptNo,
            String status,
            Integer reworkIndex,
            String requestedBy,
            String reason,
            String requestedAt,
            String decidedBy,
            String decisionReason,
            String decidedAt) {
    }

    /** 学生申请撤销只留记录、不改状态；这里把最近一次申请翻出来给界面用。 */
    public record CancelRequest(String at, String by, String reason) {
    }

    private static CancelRequest cancelRequestOf(WorkOrder o) {
        if (o.getEvents() == null) {
            return null;
        }
        CancelRequest hit = null;
        for (OrderEventLog e : o.getEvents()) {
            if (e.event() == com.zxp.dormrepair.statemachine.OrderEvent.CANCEL_REQUEST) {
                String why = e.note() == null ? "" : e.note();
                // 事件 note 里带了「申请撤销：」前缀，出参只要原因本身，别让界面再说两遍
                if (why.startsWith("申请撤销：")) {
                    why = why.substring("申请撤销：".length());
                }
                hit = new CancelRequest(e.at(), e.actorName(), why);
            }
        }
        return hit;
    }

    public static OrderView of(WorkOrder o) {
        return of(o, null);
    }

    public static OrderView of(WorkOrder o, ReworkReview review) {
        return new OrderView(
                o.getNo(),
                o.getStatus().name(),
                o.getStatus().label(),
                o.getVersion(),
                o.getBuilding(),
                o.getFloor(),
                o.getRoom(),
                o.location(),
                o.getStudentName(),
                o.getDescription(),
                o.getVisitWindow(),
                o.getPhotosBefore(),
                o.getPhotosAfter(),
                o.getPhotoIdsBefore(),
                o.getPhotoIdsAfter(),
                o.getCategory(),
                o.getLevel(),
                o.getLevelSource(),
                o.getAiBasis(),
                o.getWorkerId(),
                o.getWorkerName(),
                o.getPartsName(),
                o.getPartsAmount(),
                o.getPartsEta(),
                o.getCreatedAt(),
                o.getAssignedAt(),
                o.getArriveAt(),
                o.getFinishAt(),
                o.getSignAt(),
                o.getClosedAt(),
                o.getCancelledAt(),
                o.getStatusAt(),
                o.getReworkOf(),
                o.getReworkIndex(),
                o.getReworkCount() == null ? 0 : o.getReworkCount(),
                StateMachine.allowedEvents(o.getStatus()),
                o.getEvents(),
                cancelRequestOf(o),
                review == null ? null : new ReworkReviewView(
                        review.id(), review.attemptNo(), review.status(), review.reworkIndex(),
                        review.requestedBy(), review.requestReason(), review.requestedAt(),
                        review.decidedBy(), review.decisionReason(), review.decidedAt()));
    }
}
