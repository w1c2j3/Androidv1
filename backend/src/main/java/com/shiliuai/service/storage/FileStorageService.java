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
import java.util.Locale;
import java.util.HexFormat;
import java.util.Set;

@Service
public class FileStorageService {
    private static final Set<String> OCR_SUPPORTED_EXTENSIONS = Set.of(
            ".bmp", ".dib", ".jpeg", ".jpg", ".png", ".webp", ".pbm", ".pgm",
            ".ppm", ".pnm", ".sr", ".ras", ".tiff", ".tif", ".pdf"
    );

    private final ShiliuProperties properties;
    private final Clock clock;

    public FileStorageService(ShiliuProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public FileRef store(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        try {
            byte[] header;
            try (InputStream input = file.getInputStream()) {
                header = readHeader(input);
            }
            String extension = extensionOf(originalName, file.getContentType(), header);
            String fileId = Ids.fileId(clock);
            Path target = storageDir().resolve(fileId + extension).normalize();
            Files.createDirectories(target.getParent());
            file.transferTo(target);
            return toFileRef(fileId, target, file.getContentType());
        } catch (IOException exception) {
            throw new IllegalStateException("保存上传图片失败", exception);
        }
    }

    public FileRef storeBytes(String filename, String mimeType, byte[] bytes) {
        String fileId = Ids.fileId(clock);
        Path target = storageDir().resolve(fileId + extensionOf(filename, mimeType, bytes)).normalize();
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

    private static String extensionOf(String filename, String contentType, byte[] header) {
        String nameExtension = extensionFromName(filename);
        if (nameExtension != null) {
            return nameExtension;
        }
        String typeExtension = extensionFromContentType(contentType);
        if (typeExtension != null) {
            return typeExtension;
        }
        String sniffedExtension = extensionFromMagicBytes(header);
        if (sniffedExtension != null) {
            return sniffedExtension;
        }
        return ".jpg";
    }

    private static String extensionFromName(String filename) {
        if (filename == null || filename.isBlank()) return null;
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return null;
        String ext = filename.substring(dot).toLowerCase(Locale.ROOT);
        return OCR_SUPPORTED_EXTENSIONS.contains(ext) ? ext : null;
    }

    private static String extensionFromContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) return null;
        String value = contentType.toLowerCase(Locale.ROOT);
        int semicolon = value.indexOf(';');
        if (semicolon >= 0) {
            value = value.substring(0, semicolon).trim();
        }
        return switch (value) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/bmp", "image/x-ms-bmp" -> ".bmp";
            case "image/tiff" -> ".tif";
            case "application/pdf" -> ".pdf";
            default -> null;
        };
    }

    private static String extensionFromMagicBytes(byte[] header) {
        if (header == null || header.length < 4) return null;
        if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return ".jpg";
        }
        if ((header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G') {
            return ".png";
        }
        if (header[0] == 'B' && header[1] == 'M') {
            return ".bmp";
        }
        if (header.length >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return ".webp";
        }
        if ((header[0] == 'I' && header[1] == 'I' && (header[2] & 0xFF) == 0x2A && header[3] == 0)
                || (header[0] == 'M' && header[1] == 'M' && header[2] == 0 && (header[3] & 0xFF) == 0x2A)) {
            return ".tif";
        }
        if (header[0] == '%' && header[1] == 'P' && header[2] == 'D' && header[3] == 'F') {
            return ".pdf";
        }
        return null;
    }

    private static byte[] readHeader(InputStream input) throws IOException {
        byte[] header = new byte[16];
        int read = input.read(header);
        if (read <= 0) {
            return new byte[0];
        }
        if (read == header.length) {
            return header;
        }
        byte[] exact = new byte[read];
        System.arraycopy(header, 0, exact, 0, read);
        return exact;
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
