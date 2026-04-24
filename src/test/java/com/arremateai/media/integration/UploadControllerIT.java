package com.arremateai.media.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UploadControllerIT {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void overrideStorage(DynamicPropertyRegistry registry) {
        registry.add("app.media.storage-location", () -> tempDir.toAbsolutePath().toString());
    }

    @Autowired
    private MockMvc mockMvc;

    private byte[] pngValido() throws Exception {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    @Test
    @DisplayName("POST /api/media/imagens sem X-User-Id retorna 401")
    void deveRejeitarImagemSemUserId() throws Exception {
        MockMultipartFile arquivo = new MockMultipartFile(
                "file", "foto.png", "image/png", pngValido());
        mockMvc.perform(multipart("/api/media/imagens").file(arquivo))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/media/imagens com PNG válido retorna 200 + url")
    void deveAceitarImagemValida() throws Exception {
        MockMultipartFile arquivo = new MockMultipartFile(
                "file", "foto.png", "image/png", pngValido());
        mockMvc.perform(multipart("/api/media/imagens")
                        .file(arquivo)
                        .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").exists());
    }

    @Test
    @DisplayName("POST /api/media/imagens com extensão inválida retorna 400")
    void deveRejeitarImagemComExtensaoInvalida() throws Exception {
        MockMultipartFile arquivo = new MockMultipartFile(
                "file", "arquivo.txt", "text/plain", "conteudo".getBytes());
        mockMvc.perform(multipart("/api/media/imagens")
                        .file(arquivo)
                        .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/media/videos sem X-User-Id retorna 401")
    void deveRejeitarVideoSemUserId() throws Exception {
        MockMultipartFile arquivo = new MockMultipartFile(
                "file", "video.mp4", "video/mp4", new byte[]{1, 2, 3, 4});
        mockMvc.perform(multipart("/api/media/videos").file(arquivo))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/media/videos com extensão inválida retorna 400")
    void deveRejeitarVideoComExtensaoInvalida() throws Exception {
        MockMultipartFile arquivo = new MockMultipartFile(
                "file", "video.txt", "text/plain", "abc".getBytes());
        mockMvc.perform(multipart("/api/media/videos")
                        .file(arquivo)
                        .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/media/avatares sem X-User-Id retorna 401")
    void deveRejeitarAvatarSemUserId() throws Exception {
        MockMultipartFile arquivo = new MockMultipartFile(
                "file", "avatar.png", "image/png", pngValido());
        mockMvc.perform(multipart("/api/media/avatares").file(arquivo))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/media/avatares com PNG válido retorna 200 + url")
    void deveAceitarAvatarValido() throws Exception {
        MockMultipartFile arquivo = new MockMultipartFile(
                "file", "avatar.png", "image/png", pngValido());
        mockMvc.perform(multipart("/api/media/avatares")
                        .file(arquivo)
                        .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").exists());
    }

    @Test
    @DisplayName("POST /api/media/avatares com extensão inválida retorna 400")
    void deveRejeitarAvatarComExtensaoInvalida() throws Exception {
        MockMultipartFile arquivo = new MockMultipartFile(
                "file", "avatar.bmp", "image/bmp", new byte[]{1, 2, 3});
        mockMvc.perform(multipart("/api/media/avatares")
                        .file(arquivo)
                        .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/media sem X-User-Id retorna 401")
    void deveRejeitarDeletarSemUserId() throws Exception {
        mockMvc.perform(delete("/api/media").param("url", "http://localhost:8084/uploads/imoveis/x.png"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /api/media com url inexistente retorna 204")
    void deveAceitarDeletarUrlInexistente() throws Exception {
        mockMvc.perform(delete("/api/media")
                        .param("url", "http://localhost:8084/uploads/imoveis/inexistente.png")
                        .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isNoContent());
    }
}
