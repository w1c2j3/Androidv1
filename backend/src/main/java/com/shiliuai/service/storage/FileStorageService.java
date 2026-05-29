package com.shiliuai.service.storage;

import com.shiliuai.config.ShiliuProperties;
import com.shiliuai.dto.FileRef;
import com.shiliuai.util.Ids;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;

@Service
public class FileStorageService {
    private final ShiliuProperties properties;
    private final Clock clock;

    public FileStorageService(ShiliuProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public FileRef store(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        String extension = extensionOf(originalName);
        String fileId = Ids.fileId(clock);
        Path target = storageDir().resolve(fileId + extension).normalize();
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
            return toFileRef(fileId, target, file.getContentType());
        } catch (IOException exception) {
            throw new IllegalStateException("保存上传图片失败", exception);
        }
    }

    public FileRef storeBytes(String filename, String mimeType, byte[] bytes) {
        String fileId = Ids.fileId(clock);
        Path target = storageDir().resolve(fileId + extensionOf(filename)).normalize();
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
            return toFileRef(fileId, target, mimeType);
        } catch (IOException exception) {
            throw new IllegalStateException("保存文件失败", exception);
        }
    }

    private FileRef toFileRef(String fileId, Path target, String contentType) throws IOException {
        FileRef ref = new FileRef();
        ref.localFileId = fileId;
        ref.localPath = target.toAbsolutePath().toString();
        ref.mimeType = contentType == null ? "application/octet-stream" : contentType;
        ref.sizeBytes = Files.size(target);
        ref.sha256 = sha256(target);
        ref.downloadStatus = "success";
        return ref;
    }

    private Path storageDir() {
        return Path.of(properties.getFileStorageDir()).toAbsolutePath().normalize();
    }

    private static String extensionOf(String filename) {
        if (filename == null || filename.isBlank()) {
            return ".bin";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return ".bin";
        }
        String ext = filename.substring(dot).toLowerCase();
        return ext.matches("\\.[a-z0-9]{1,8}") ? ext : ".bin";
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream inputStream = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = inputStream.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
