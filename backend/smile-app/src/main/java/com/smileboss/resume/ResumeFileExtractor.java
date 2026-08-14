package com.smileboss.resume;

import com.smileboss.common.BizException;
import com.smileboss.security.HashUtils;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** 负责简历文件校验、文本提取和本地归档，不处理业务结构化。 */
@Component
public class ResumeFileExtractor {
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx", "txt", "md");
    private static final Pattern HORIZONTAL_WHITESPACE = Pattern.compile("[ \\t]+");
    private static final Pattern EXCESSIVE_NEWLINES = Pattern.compile("\\n{3,}");

    private final Tika tika = new Tika();
    private final Path uploadDirectory;

    public ResumeFileExtractor(@Value("${smile.upload-dir:./uploads}") String uploadDirectory) {
        this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
    }

    public PreparedResume prepare(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("简历文件不能为空");
        }
        String originalFilename = Optional.ofNullable(file.getOriginalFilename()).orElse("resume.txt");
        String extension = extensionOf(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BizException("只支持 PDF、DOC、DOCX、TXT、MD 简历");
        }

        try {
            byte[] content = file.getBytes();
            String text;
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(content)) {
                text = tika.parseToString(inputStream);
            }
            if (text == null || text.isBlank()) {
                throw new BizException("未能提取简历文字；扫描件需要接入 OCR 后再解析");
            }
            return new PreparedResume(
                    originalFilename,
                    extension,
                    content,
                    HashUtils.sha256(content),
                    clean(text));
        } catch (IOException | TikaException exception) {
            throw new BizException("简历文本提取失败：" + exception.getMessage());
        }
    }

    public void archive(PreparedResume resume) {
        try {
            Files.createDirectories(uploadDirectory);
            Path target = uploadDirectory.resolve(UUID.randomUUID() + "." + resume.extension()).normalize();
            if (!target.startsWith(uploadDirectory)) {
                throw new BizException("非法文件归档路径");
            }
            Files.write(target, resume.content());
        } catch (IOException exception) {
            throw new BizException("简历文件归档失败：" + exception.getMessage());
        }
    }

    public static String clean(String text) {
        String withoutNull = text.replace("\u0000", "");
        String compactSpaces = HORIZONTAL_WHITESPACE.matcher(withoutNull).replaceAll(" ");
        return EXCESSIVE_NEWLINES.matcher(compactSpaces).replaceAll("\n\n").trim();
    }

    private static String extensionOf(String filename) {
        int separator = filename.lastIndexOf('.');
        if (separator < 0 || separator == filename.length() - 1) {
            return "";
        }
        return filename.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    public record PreparedResume(
            String originalFilename,
            String extension,
            byte[] content,
            String hash,
            String text) {}
}
