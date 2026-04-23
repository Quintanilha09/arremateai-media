package com.arremateai.media.controller;

import com.arremateai.media.service.StorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadControllerTest {

    @Mock
    private StorageService storageService;

    @InjectMocks
    private UploadController uploadController;

    private static final String USER_ID_PADRAO = "123e4567-e89b-12d3-a456-426614174000";
    private static final String URL_PADRAO = "/uploads/imagens/foto.jpg";

    private MultipartFile criarArquivoMock() {
        return new MockMultipartFile("file", "foto.jpg", "image/jpeg", "conteudo".getBytes());
    }

    // ===== uploadImagem =====

    @Test
    @DisplayName("Deve retornar URL ao fazer upload de imagem com sucesso")
    void deveRetornarUrlAoFazerUploadDeImagemComSucesso() throws IOException {
        when(storageService.salvarImagem(any(MultipartFile.class))).thenReturn(URL_PADRAO);

        var resultado = uploadController.uploadImagem(USER_ID_PADRAO, criarArquivoMock());

        assertThat(resultado.getStatusCode().value()).isEqualTo(200);
        assertThat(resultado.getBody()).containsEntry("url", URL_PADRAO);
        verify(storageService).salvarImagem(any(MultipartFile.class));
    }

    @Test
    @DisplayName("Deve propagar IOException ao falhar upload de imagem")
    void devePropagarIOExceptionAoFalharUploadDeImagem() throws IOException {
        when(storageService.salvarImagem(any())).thenThrow(new IOException("Disco cheio"));

        assertThatThrownBy(() -> uploadController.uploadImagem(USER_ID_PADRAO, criarArquivoMock()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Disco cheio");
    }

    // ===== uploadVideo =====

    @Test
    @DisplayName("Deve retornar URL ao fazer upload de vídeo com sucesso")
    void deveRetornarUrlAoFazerUploadDeVideoComSucesso() throws IOException {
        var urlVideo = "/uploads/videos/video.mp4";
        when(storageService.salvarVideo(any(MultipartFile.class))).thenReturn(urlVideo);

        var resultado = uploadController.uploadVideo(USER_ID_PADRAO, criarArquivoMock());

        assertThat(resultado.getStatusCode().value()).isEqualTo(200);
        assertThat(resultado.getBody()).containsEntry("url", urlVideo);
        verify(storageService).salvarVideo(any(MultipartFile.class));
    }

    // ===== uploadAvatar =====

    @Test
    @DisplayName("Deve retornar URL ao fazer upload de avatar com sucesso")
    void deveRetornarUrlAoFazerUploadDeAvatarComSucesso() throws IOException {
        var urlAvatar = "/uploads/avatares/avatar.jpg";
        when(storageService.salvarAvatar(any(MultipartFile.class))).thenReturn(urlAvatar);

        var resultado = uploadController.uploadAvatar(USER_ID_PADRAO, criarArquivoMock());

        assertThat(resultado.getStatusCode().value()).isEqualTo(200);
        assertThat(resultado.getBody()).containsEntry("url", urlAvatar);
        verify(storageService).salvarAvatar(any(MultipartFile.class));
    }

    // ===== deletarArquivo =====

    @Test
    @DisplayName("Deve retornar 204 ao deletar arquivo com sucesso")
    void deveRetornar204AoDeletarArquivoComSucesso() throws IOException {
        doNothing().when(storageService).deletarArquivo(URL_PADRAO);

        var resultado = uploadController.deletarArquivo(USER_ID_PADRAO, URL_PADRAO);

        assertThat(resultado.getStatusCode().value()).isEqualTo(204);
        verify(storageService).deletarArquivo(URL_PADRAO);
    }

    @Test
    @DisplayName("Deve propagar IOException ao falhar deleção de arquivo")
    void devePropagarIOExceptionAoFalharDelecao() throws IOException {
        doThrow(new IOException("Arquivo não encontrado")).when(storageService).deletarArquivo(anyString());

        assertThatThrownBy(() -> uploadController.deletarArquivo(USER_ID_PADRAO, URL_PADRAO))
                .isInstanceOf(IOException.class);
    }
}
