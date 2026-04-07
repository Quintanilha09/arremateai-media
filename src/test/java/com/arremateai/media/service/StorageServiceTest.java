package com.arremateai.media.service;

import com.arremateai.media.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class StorageServiceTest {

    private StorageService storageService;

    @TempDir
    Path tempDir;

    private static final String BASE_URL = "http://localhost:8083";
    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024;
    private static final long MAX_VIDEO_SIZE = 500 * 1024 * 1024;
    private static final long MAX_AVATAR_SIZE = 2 * 1024 * 1024;

    @BeforeEach
    void setUp() throws IOException {
        storageService = new StorageService(
                tempDir.toString(), BASE_URL, MAX_IMAGE_SIZE, MAX_VIDEO_SIZE, MAX_AVATAR_SIZE);
    }

    private byte[] criarBytesImagemValida() throws IOException {
        BufferedImage imagem = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(imagem, "jpg", baos);
        return baos.toByteArray();
    }

    private MockMultipartFile criarImagemValida() throws IOException {
        return new MockMultipartFile("file", "foto.jpg", "image/jpeg", criarBytesImagemValida());
    }

    // ---- salvarImagem ----

    @Test
    @DisplayName("Deve salvar imagem com sucesso e retornar URL")
    void deveSalvarImagemComSucessoERetornarUrl() throws IOException {
        var arquivo = criarImagemValida();

        String url = storageService.salvarImagem(arquivo);

        assertThat(url).startsWith(BASE_URL + "/uploads/imoveis/");
        assertThat(url).endsWith(".jpg");
    }

    @Test
    @DisplayName("Deve lançar exceção quando imagem for nula")
    void deveLancarExcecaoQuandoImagemForNula() {
        assertThatThrownBy(() -> storageService.salvarImagem(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("não pode estar vazio");
    }

    @Test
    @DisplayName("Deve lançar exceção quando imagem estiver vazia")
    void deveLancarExcecaoQuandoImagemEstiverVazia() {
        var arquivo = new MockMultipartFile("file", "foto.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> storageService.salvarImagem(arquivo))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("não pode estar vazio");
    }

    @Test
    @DisplayName("Deve lançar exceção quando imagem exceder tamanho máximo")
    void deveLancarExcecaoQuandoImagemExcederTamanhoMaximo() {
        byte[] conteudo = new byte[(int) (MAX_IMAGE_SIZE + 1)];
        var arquivo = new MockMultipartFile("file", "foto.jpg", "image/jpeg", conteudo);

        assertThatThrownBy(() -> storageService.salvarImagem(arquivo))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("excede o tamanho máximo");
    }

    @Test
    @DisplayName("Deve lançar exceção quando extensão da imagem for inválida")
    void deveLancarExcecaoQuandoExtensaoDaImagemForInvalida() {
        var arquivo = new MockMultipartFile("file", "arquivo.gif", "image/gif", new byte[]{1});

        assertThatThrownBy(() -> storageService.salvarImagem(arquivo))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("deve ser um dos formatos");
    }

    @Test
    @DisplayName("Deve lançar exceção quando arquivo não for imagem real")
    void deveLancarExcecaoQuandoArquivoNaoForImagemReal() {
        var arquivo = new MockMultipartFile("file", "falsa.jpg", "image/jpeg", "texto-nao-imagem".getBytes());

        assertThatThrownBy(() -> storageService.salvarImagem(arquivo))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("não é uma imagem válida");
    }

    // ---- salvarVideo ----

    @Test
    @DisplayName("Deve salvar vídeo com sucesso")
    void deveSalvarVideoComSucesso() throws IOException {
        var arquivo = new MockMultipartFile("file", "video.mp4", "video/mp4", new byte[]{1, 2, 3});

        String url = storageService.salvarVideo(arquivo);

        assertThat(url).startsWith(BASE_URL + "/uploads/videos/");
        assertThat(url).endsWith(".mp4");
    }

    @Test
    @DisplayName("Deve lançar exceção quando vídeo estiver vazio")
    void deveLancarExcecaoQuandoVideoEstiverVazio() {
        assertThatThrownBy(() -> storageService.salvarVideo(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("não pode estar vazio");
    }

    @Test
    @DisplayName("Deve lançar exceção quando extensão do vídeo for inválida")
    void deveLancarExcecaoQuandoExtensaoDoVideoForInvalida() {
        var arquivo = new MockMultipartFile("file", "video.flv", "video/x-flv", new byte[]{1});

        assertThatThrownBy(() -> storageService.salvarVideo(arquivo))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("deve ser um dos formatos");
    }

    // ---- salvarAvatar ----

    @Test
    @DisplayName("Deve salvar avatar com sucesso")
    void deveSalvarAvatarComSucesso() throws IOException {
        var arquivo = new MockMultipartFile("file", "avatar.png", "image/png", criarBytesImagemValida());

        String url = storageService.salvarAvatar(arquivo);

        assertThat(url).startsWith(BASE_URL + "/uploads/avatares/");
        assertThat(url).endsWith(".png");
    }

    @Test
    @DisplayName("Deve lançar exceção quando avatar exceder tamanho máximo")
    void deveLancarExcecaoQuandoAvatarExcederTamanhoMaximo() {
        byte[] conteudo = new byte[(int) (MAX_AVATAR_SIZE + 1)];
        var arquivo = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", conteudo);

        assertThatThrownBy(() -> storageService.salvarAvatar(arquivo))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("excede o tamanho máximo");
    }

    // ---- deletarArquivo ----

    @Test
    @DisplayName("Deve deletar arquivo existente com sucesso")
    void deveDeletarArquivoExistenteComSucesso() throws IOException {
        Path subdir = tempDir.resolve("imoveis");
        Files.createDirectories(subdir);
        Path arquivo = subdir.resolve("test-file.jpg");
        Files.writeString(arquivo, "conteudo");

        String url = BASE_URL + "/uploads/imoveis/test-file.jpg";

        assertThatCode(() -> storageService.deletarArquivo(url)).doesNotThrowAnyException();
        assertThat(Files.exists(arquivo)).isFalse();
    }

    @Test
    @DisplayName("Deve lançar exceção ao tentar deletar arquivo com path traversal")
    void deveLancarExcecaoAoTentarDeletarArquivoComPathTraversal() {
        String url = BASE_URL + "/uploads/../../../etc/passwd";

        assertThatThrownBy(() -> storageService.deletarArquivo(url))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Deve lançar exceção quando URL for inválida ao deletar")
    void deveLancarExcecaoQuandoUrlForInvalidaAoDeletar() {
        assertThatThrownBy(() -> storageService.deletarArquivo("://url invalida com espaco"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("URL inválida");
    }

    // ---- extrairExtensao (testada indiretamente) ----

    @Test
    @DisplayName("Deve lançar exceção quando nome do arquivo não tiver extensão")
    void deveLancarExcecaoQuandoNomeDoArquivoNaoTiverExtensao() {
        var arquivo = new MockMultipartFile("file", "arquivo", "image/jpeg", new byte[]{1});

        assertThatThrownBy(() -> storageService.salvarImagem(arquivo))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inválido ou sem extensão");
    }

    @Test
    @DisplayName("Deve lançar exceção quando nome tiver múltiplas extensões")
    void deveLancarExcecaoQuandoNomeTiverMultiplasExtensoes() {
        var arquivo = new MockMultipartFile("file", "shell.php.jpg", "image/jpeg", new byte[]{1});

        assertThatThrownBy(() -> storageService.salvarImagem(arquivo))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("múltiplas extensões");
    }
}
