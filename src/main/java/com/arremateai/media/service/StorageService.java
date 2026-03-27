package com.arremateai.media.service;

import com.arremateai.media.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class StorageService {

    private static final List<String> ALLOWED_IMAGE_EXTENSIONS = Arrays.asList("jpg", "jpeg", "png", "webp");
    private static final List<String> ALLOWED_VIDEO_EXTENSIONS = Arrays.asList("mp4", "mov", "avi", "mkv", "webm");

    private final Path storageRoot;
    private final String baseUrl;
    private final long maxImageSize;
    private final long maxVideoSize;
    private final long maxAvatarSize;

    public StorageService(
            @Value("${app.media.storage-location:./uploads}") String storageLocation,
            @Value("${app.media.base-url:http://localhost:8083}") String baseUrl,
            @Value("${app.media.max-image-size:5242880}") long maxImageSize,
            @Value("${app.media.max-video-size:524288000}") long maxVideoSize,
            @Value("${app.media.max-avatar-size:2097152}") long maxAvatarSize
    ) throws IOException {
        this.storageRoot = Paths.get(storageLocation).toAbsolutePath().normalize();
        this.baseUrl = baseUrl;
        this.maxImageSize = maxImageSize;
        this.maxVideoSize = maxVideoSize;
        this.maxAvatarSize = maxAvatarSize;

        Files.createDirectories(this.storageRoot.resolve("imoveis"));
        Files.createDirectories(this.storageRoot.resolve("videos"));
        Files.createDirectories(this.storageRoot.resolve("avatares"));
        log.info("Storage root: {}", this.storageRoot);
    }

    public String salvarImagem(MultipartFile file) throws IOException {
        validarTamanho(file, maxImageSize, "Imagem");
        String ext = extrairExtensao(file.getOriginalFilename());
        validarExtensao(ext, ALLOWED_IMAGE_EXTENSIONS, "Imagem");
        validarImagemReal(file);
        return salvar(file, "imoveis", ext);
    }

    public String salvarVideo(MultipartFile file) throws IOException {
        validarTamanho(file, maxVideoSize, "Vídeo");
        String ext = extrairExtensao(file.getOriginalFilename());
        validarExtensao(ext, ALLOWED_VIDEO_EXTENSIONS, "Vídeo");
        return salvar(file, "videos", ext);
    }

    public String salvarAvatar(MultipartFile file) throws IOException {
        validarTamanho(file, maxAvatarSize, "Avatar");
        String ext = extrairExtensao(file.getOriginalFilename());
        validarExtensao(ext, ALLOWED_IMAGE_EXTENSIONS, "Avatar");
        validarImagemReal(file);
        return salvar(file, "avatares", ext);
    }

    public void deletarArquivo(String url) throws IOException {
        try {
            URI uri = URI.create(url);
            String urlPath = uri.getPath(); // e.g. /uploads/imoveis/uuid.jpg

            // Resolve path against storage root safely
            // urlPath starts with /uploads/, strip that prefix
            String relativePath = urlPath.replaceFirst("^/uploads/", "");
            Path target = storageRoot.resolve(relativePath).normalize();

            // Security: ensure target is inside storageRoot
            if (!target.startsWith(storageRoot)) {
                log.warn("Tentativa de deletar arquivo fora do diretório de uploads: {}", url);
                throw new BusinessException("URL inválida para deleção");
            }

            boolean deleted = Files.deleteIfExists(target);
            log.info("Arquivo {}: {}", deleted ? "deletado" : "não encontrado", target);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("URL inválida: " + url);
        }
    }

    // ---- private helpers ----

    private String salvar(MultipartFile file, String subdir, String ext) throws IOException {
        String filename = UUID.randomUUID() + "." + ext;
        Path destino = storageRoot.resolve(subdir).resolve(filename);
        try (InputStream is = file.getInputStream()) {
            Files.copy(is, destino, StandardCopyOption.REPLACE_EXISTING);
        }
        String url = baseUrl + "/uploads/" + subdir + "/" + filename;
        log.info("Arquivo salvo: {}", url);
        return url;
    }

    private void validarTamanho(MultipartFile file, long maxBytes, String tipo) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Arquivo " + tipo + " não pode estar vazio");
        }
        if (file.getSize() > maxBytes) {
            long maxMb = maxBytes / (1024 * 1024);
            throw new BusinessException(tipo + " excede o tamanho máximo de " + maxMb + "MB");
        }
    }

    private String extrairExtensao(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new BusinessException("Nome de arquivo inválido ou sem extensão");
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private void validarExtensao(String ext, List<String> allowed, String tipo) {
        if (!allowed.contains(ext)) {
            throw new BusinessException(tipo + " deve ser um dos formatos: " + String.join(", ", allowed));
        }
    }

    private void validarImagemReal(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream()) {
            var image = ImageIO.read(is);
            if (image == null) {
                throw new BusinessException("Arquivo não é uma imagem válida");
            }
        }
    }
}
