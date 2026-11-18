package com.zxp.dormrepair.dto;

import java.util.List;

/**
 * 学生提交报修。字段名与 SYS-003 一致，前端不用改名。
 */
public record SubmitRequest(
        String building,
        Integer floor,
        String room,
        String desc,
        String window,
        List<String> photosBefore,
        List<String> photoIdsBefore,
        String category,
        String level,
        String studentId,
        String studentName) {
}
