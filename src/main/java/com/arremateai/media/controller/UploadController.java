package com.arremateai.media.controller;

import com.arremateai.media.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class UploadController {

    private final StorageService storageService;

    @PostMapping("/imagens")
    public ResponseEntity<Map<String, String>> uploadImagem(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        log.info("Upload de imagem por usuário: {}", userId);
        String url = storageService.salvarImagem(file);
        return ResponseEntity.ok(Map.of("url", url));
    }

    @PostMapping("/videos")
    public ResponseEntity<Map<String, String>> uploadVideo(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        log.info("Upload de vídeo por usuário: {}", userId);
        String url = storageService.salvarVideo(file);
        return ResponseEntity.ok(Map.of("url", url));
    }

    @PostMapping("/avatares")
    public ResponseEntity<Map<String, String>> uploadAvatar(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        log.info("Upload de avatar por usuário: {}", userId);
        String url = storageService.salvarAvatar(file);
        return ResponseEntity.ok(Map.of("url", url));
    }

    @DeleteMapping
    public ResponseEntity<Void> deletarArquivo(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam("url") String url
    ) throws IOException {
        log.info("Deleção de arquivo solicitada por {}: {}", userId, url);
        storageService.deletarArquivo(url);
        return ResponseEntity.noContent().build();
    }
}
