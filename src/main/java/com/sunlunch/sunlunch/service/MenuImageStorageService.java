package com.sunlunch.sunlunch.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class MenuImageStorageService {

    public static final String MENU_UPLOAD_URL_PREFIX = "/uploads/menu/";
    public static final String DEFAULT_MENU_IMAGE_PATH = "/images/default-menu.svg";

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png");

    private final Path uploadDir;
    private final long maxImageSizeBytes;

    public MenuImageStorageService(
            @Value("${app.upload.base-dir:uploads}") String uploadBaseDir,
            @Value("${app.upload.max-image-size-bytes:5242880}") long maxImageSizeBytes) throws IOException {
        this.uploadDir = Path.of(uploadBaseDir, "menu").toAbsolutePath().normalize();
        this.maxImageSizeBytes = maxImageSizeBytes;
        Files.createDirectories(this.uploadDir);
    }

    public String storeUploadedImage(MultipartFile imageFile) throws IOException {
        if (imageFile == null || imageFile.isEmpty()) {
            throw new IllegalArgumentException("Please select an image file.");
        }
        if (imageFile.getSize() > maxImageSizeBytes) {
            throw new IllegalArgumentException("Image size must be 5MB or less.");
        }

        String extension = extractExtension(imageFile.getOriginalFilename());
        validateExtension(extension);
        BufferedImage image = readAsImage(imageFile.getInputStream());
        String normalizedExtension = normalizeExtension(extension);

        String storedFilename = createUniqueFilename(normalizedExtension);
        Path targetPath = uploadDir.resolve(storedFilename).normalize();
        ensureInsideUploadDir(targetPath);

        ImageIO.write(image, normalizedExtension, targetPath.toFile());
        return MENU_UPLOAD_URL_PREFIX + storedFilename;
    }

    public String downloadAndStoreImage(String imageUrl) throws IOException {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Please enter an image URL.");
        }

        URL url = new URL(imageUrl.trim());
        String protocol = url.getProtocol();
        if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
            throw new IllegalArgumentException("Only http/https URLs are supported.");
        }

        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);

        String extension = extractExtension(url.getPath());
        if (extension == null || extension.isBlank()) {
            extension = "jpg";
        }
        validateExtension(extension);
        String normalizedExtension = normalizeExtension(extension);

        Path tempFile = Files.createTempFile("menu-image-", "." + normalizedExtension);
        try (InputStream in = connection.getInputStream()) {
            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }

        long downloadedSize = Files.size(tempFile);
        if (downloadedSize > maxImageSizeBytes) {
            Files.deleteIfExists(tempFile);
            throw new IllegalArgumentException("Image size must be 5MB or less.");
        }

        BufferedImage image;
        try (InputStream imageStream = Files.newInputStream(tempFile)) {
            image = readAsImage(imageStream);
        } finally {
            Files.deleteIfExists(tempFile);
        }

        String storedFilename = createUniqueFilename(normalizedExtension);
        Path targetPath = uploadDir.resolve(storedFilename).normalize();
        ensureInsideUploadDir(targetPath);
        ImageIO.write(image, normalizedExtension, targetPath.toFile());
        return MENU_UPLOAD_URL_PREFIX + storedFilename;
    }

    public String getDefaultImagePath() {
        return DEFAULT_MENU_IMAGE_PATH;
    }

    public void deleteManagedImageIfExists(String imagePath) {
        if (imagePath == null || !imagePath.startsWith(MENU_UPLOAD_URL_PREFIX)) {
            return;
        }

        String filename = imagePath.substring(MENU_UPLOAD_URL_PREFIX.length());
        if (filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            return;
        }

        Path filePath = uploadDir.resolve(filename).normalize();
        if (!filePath.startsWith(uploadDir)) {
            return;
        }

        try {
            Files.deleteIfExists(filePath);
        } catch (IOException ignored) {
            // Ignore delete failures to avoid interrupting menu updates.
        }
    }

    private BufferedImage readAsImage(InputStream inputStream) throws IOException {
        BufferedImage image = ImageIO.read(inputStream);
        if (image == null) {
            throw new IllegalArgumentException("Only JPG and PNG image formats are supported.");
        }
        return image;
    }

    private String createUniqueFilename(String extension) {
        return UUID.randomUUID() + "." + extension;
    }

    private String extractExtension(String filename) {
        if (filename == null) {
            return null;
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return null;
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private void validateExtension(String extension) {
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Only JPG and PNG image formats are supported.");
        }
    }

    private String normalizeExtension(String extension) {
        String lower = extension.toLowerCase(Locale.ROOT);
        return "jpeg".equals(lower) ? "jpg" : lower;
    }

    private void ensureInsideUploadDir(Path targetPath) {
        if (!targetPath.startsWith(uploadDir)) {
            throw new IllegalArgumentException("Invalid file path.");
        }
    }
}
