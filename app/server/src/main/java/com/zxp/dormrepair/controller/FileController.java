package com.zxp.dormrepair.controller;

import com.zxp.dormrepair.auth.AuthFilter;
import com.zxp.dormrepair.common.ApiResponses;
import com.zxp.dormrepair.common.BizException;
import com.zxp.dormrepair.domain.Actor;
import com.zxp.dormrepair.file.FileRecord;
import com.zxp.dormrepair.file.FileService;
import com.zxp.dormrepair.service.OrderService;
import com.zxp.dormrepair.statemachine.ActorType;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 图片上传与受控读取。文件 ID 由服务端生成，下载必须带有效身份。 */
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileService files;
    private final OrderService orders;

    public FileController(FileService files, OrderService orders) {
        this.files = files;
        this.orders = orders;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file,
                                      @RequestParam(defaultValue = "BEFORE") String purpose,
                                      HttpServletRequest http) {
        Actor actor = (Actor) http.getAttribute(AuthFilter.ATTR);
        if (actor == null) throw BizException.unauthorized("请先登录");
        String p = purpose == null ? "BEFORE" : purpose.trim().toUpperCase();
        if (!p.equals("BEFORE") && !p.equals("AFTER")) {
            throw BizException.badRequest("purpose 只能是 BEFORE 或 AFTER");
        }
        FileRecord f = files.upload(file, actor, p);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", f.id());
        out.put("url", "/api/v1/files/" + f.id());
        out.put("mime", f.mime());
        out.put("byteSize", f.byteSize());
        out.put("sha256", f.sha256());
        return ApiResponses.ok(out);
    }

    @GetMapping("/{id}/metadata")
    public Map<String, Object> metadata(@PathVariable String id, HttpServletRequest http) {
        Actor actor = (Actor) http.getAttribute(AuthFilter.ATTR);
        if (actor == null) throw BizException.unauthorized("请先登录");
        FileRecord f = files.require(id);
        if (f.orderNo() == null && !actor.id().equals(f.uploadedBy()) && actor.type() != ActorType.ADMIN) {
            throw BizException.forbidden("不能读取别人的未绑定文件");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", f.id());
        out.put("mime", f.mime());
        out.put("byteSize", f.byteSize());
        out.put("sha256", f.sha256());
        out.put("purpose", f.purpose());
        return ApiResponses.ok(out);
    }

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> download(@PathVariable String id, HttpServletRequest http) throws IOException {
        Actor actor = (Actor) http.getAttribute(AuthFilter.ATTR);
        if (actor == null) throw BizException.unauthorized("请先登录");
        FileRecord f = files.require(id);
        if (f.orderNo() == null) {
            if (!actor.id().equals(f.uploadedBy()) && actor.type() != ActorType.ADMIN) {
                throw BizException.forbidden("不能读取别人的未绑定文件");
            }
        } else {
            orders.detailForActor(f.orderNo(), actor);
        }
        var download = files.download(id);
        try (InputStream in = download.object().stream()) {
            byte[] bytes = in.readAllBytes();
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(f.mime()))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"photo\"")
                    .header("X-Content-Type-Options", "nosniff")
                    .body(bytes);
        }
    }
}
