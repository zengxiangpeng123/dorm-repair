package com.zxp.dormrepair.file;

import com.zxp.dormrepair.common.BizException;
import com.zxp.dormrepair.domain.Actor;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 上传校验与元数据登记。真实图片必须能解码，扩展名、声明 MIME 和实际格式三者一致；
 * 对象键由服务端生成，客户端不能决定最终路径。
 */
@Service
public class FileService {

    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_BYTES = 8L * 1024 * 1024;

    private final FileStorage storage;
    private final FileRepository repository;

    public FileService(FileStorage storage, FileRepository repository) {
        this.storage = storage;
        this.repository = repository;
    }

    public FileRecord upload(MultipartFile file, Actor actor, String purpose) {
        if (file == null || file.isEmpty()) {
            throw BizException.precondition("请选择要上传的图片", "FILE_REQUIRED");
        }
        if (file.getSize() > MAX_BYTES) {
            throw BizException.precondition("单张图片不能超过 8 MB", "FILE_TOO_LARGE");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new IllegalStateException("文件读取失败", e);
        }
        String detected = detect(bytes);
        String declared = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (detected == null) {
            throw BizException.precondition("文件不是可识别的 JPEG、PNG 或 WebP 图片", "FILE_CONTENT_NOT_ALLOWED");
        }
        if (!TYPES.contains(declared) || !declared.equals(detected)) {
            throw BizException.precondition("文件类型与实际内容不一致", "FILE_TYPE_MISMATCH");
        }
        String ext = extension(detected);
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!original.toLowerCase(Locale.ROOT).endsWith("." + ext)
                && !(ext.equals("jpg") && original.toLowerCase(Locale.ROOT).endsWith(".jpeg"))) {
            throw BizException.precondition("文件扩展名与实际内容不一致", "FILE_EXTENSION_MISMATCH");
        }
        String objectKey = java.time.LocalDate.now().toString().replace("-", "") + "/"
                + actor.type().name().toLowerCase(Locale.ROOT) + "/"
                + java.util.UUID.randomUUID() + "." + ext;
        String sha256 = sha256(bytes);
        FileRecord metadata = repository.create(
                objectKey, original, detected, bytes.length, sha256, actor.id(), purpose);
        try {
            storage.put(objectKey, new java.io.ByteArrayInputStream(bytes), bytes.length, detected);
            return metadata;
        } catch (RuntimeException error) {
            // 先登记元数据再写对象：若对象写入失败或进程中断，清理任务始终能发现并回收孤儿记录。
            try {
                repository.delete(metadata.id());
            } catch (RuntimeException cleanupError) {
                error.addSuppressed(cleanupError);
            }
            throw error;
        }
    }

    public FileRecord require(String id) {
        return repository.find(id).orElseThrow(() -> BizException.notFound("文件不存在：" + id));
    }

    public StoredDownload download(String id) {
        FileRecord f = require(id);
        var object = storage.open(f.objectKey())
                .orElseThrow(() -> BizException.notFound("文件对象不存在：" + id));
        return new StoredDownload(f, object);
    }

    private static String extension(String mime) {
        return switch (mime) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new IllegalArgumentException("不支持的文件类型");
        };
    }

    private static String detect(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xff) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length >= 3 && (b[0] & 0xff) == 0xff && (b[1] & 0xff) == 0xd8 && (b[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    public record StoredDownload(FileRecord metadata, FileStorage.StoredObject object) {
    }
}
