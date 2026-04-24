package com.arremateai.media.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@DisplayName("GlobalExceptionHandler — respostas RFC 7807 (ProblemDetail)")
class GlobalExceptionHandlerTest {

    private static final String URI_TESTE = "/api/media/upload";
    private static final String TIPO_PREFIXO = "urn:arremateai:error:";

    private GlobalExceptionHandler handler;
    private HttpServletRequest requisicao;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        requisicao = mock(HttpServletRequest.class);
        when(requisicao.getRequestURI()).thenReturn(URI_TESTE);
    }

    @Test
    @DisplayName("handleBusiness → 400 business")
    void handleBusinessDeveRetornar400ComTipoBusiness() {
        ProblemDetail problema = handler.handleBusiness(
                new BusinessException("Mídia inválida"), requisicao);

        assertThat(problema.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problema.getTitle()).isEqualTo("Regra de negócio violada");
        assertThat(problema.getDetail()).isEqualTo("Mídia inválida");
        assertThat(problema.getType().toString()).isEqualTo(TIPO_PREFIXO + "business");
        assertThat(problema.getInstance().toString()).isEqualTo(URI_TESTE);
        assertThat(problema.getProperties()).containsKeys("timestamp", "path");
        assertThat(problema.getProperties()).containsEntry("path", URI_TESTE);
    }

    @Test
    @DisplayName("handleIllegalArgument → 400 illegal-argument")
    void handleIllegalArgumentDeveRetornar400ComTipoIllegalArgument() {
        ProblemDetail problema = handler.handleIllegalArgument(
                new IllegalArgumentException("argumento inválido"), requisicao);

        assertThat(problema.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problema.getTitle()).isEqualTo("Argumento inválido");
        assertThat(problema.getDetail()).isEqualTo("argumento inválido");
        assertThat(problema.getType().toString()).isEqualTo(TIPO_PREFIXO + "illegal-argument");
    }

    @Test
    @DisplayName("handleIllegalState → 409 conflict")
    void handleIllegalStateDeveRetornar409ComTipoConflict() {
        ProblemDetail problema = handler.handleIllegalState(
                new IllegalStateException("estado inválido"), requisicao);

        assertThat(problema.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problema.getTitle()).isEqualTo("Operação em conflito com o estado atual");
        assertThat(problema.getType().toString()).isEqualTo(TIPO_PREFIXO + "conflict");
    }

    @Test
    @DisplayName("handleValidation → 400 validation com lista de errors[]")
    @SuppressWarnings("unchecked")
    void handleValidationDeveRetornar400ComErrosDeCampo() {
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError campoArquivo = new FieldError("objeto", "arquivo", "não pode ser vazio");
        FieldError campoTipo = new FieldError("objeto", "tipo", "obrigatório");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(campoArquivo, campoTipo));

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ProblemDetail problema = handler.handleValidation(ex, requisicao);

        assertThat(problema.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problema.getTitle()).isEqualTo("Dados de entrada inválidos");
        assertThat(problema.getType().toString()).isEqualTo(TIPO_PREFIXO + "validation");

        List<Map<String, String>> erros = (List<Map<String, String>>) problema.getProperties().get("errors");
        assertThat(erros).hasSize(2);
        assertThat(erros.get(0)).containsEntry("field", "arquivo").containsEntry("message", "não pode ser vazio");
        assertThat(erros.get(1)).containsEntry("field", "tipo").containsEntry("message", "obrigatório");
    }

    @Test
    @DisplayName("handleValidation → usa mensagem padrão quando defaultMessage é nulo")
    @SuppressWarnings("unchecked")
    void handleValidationDeveUsarMensagemPadraoQuandoDefaultMessageNulo() {
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError semMensagem = new FieldError("objeto", "campo", null);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(semMensagem));

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ProblemDetail problema = handler.handleValidation(ex, requisicao);

        List<Map<String, String>> erros = (List<Map<String, String>>) problema.getProperties().get("errors");
        assertThat(erros).hasSize(1);
        assertThat(erros.get(0)).containsEntry("message", "inválido");
    }

    @Test
    @DisplayName("handleMissingHeader → 401 unauthenticated quando header começa com X-User-")
    void handleMissingHeaderDeveRetornar401QuandoHeaderIdentidadeAusente() {
        MissingRequestHeaderException ex = mock(MissingRequestHeaderException.class);
        when(ex.getHeaderName()).thenReturn("X-User-Id");
        when(ex.getMessage()).thenReturn("Required header 'X-User-Id' is not present.");

        ProblemDetail problema = handler.handleMissingHeader(ex, requisicao);

        assertThat(problema.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(problema.getTitle()).isEqualTo("Autenticação necessária");
        assertThat(problema.getType().toString()).isEqualTo(TIPO_PREFIXO + "unauthenticated");
        assertThat(problema.getDetail()).contains("X-User-Id");
    }

    @Test
    @DisplayName("handleMissingHeader → 400 illegal-argument quando header comum ausente")
    void handleMissingHeaderDeveRetornar400QuandoHeaderComumAusente() {
        MissingRequestHeaderException ex = mock(MissingRequestHeaderException.class);
        when(ex.getHeaderName()).thenReturn("Content-Type");
        when(ex.getMessage()).thenReturn("Required header 'Content-Type' is not present.");

        ProblemDetail problema = handler.handleMissingHeader(ex, requisicao);

        assertThat(problema.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problema.getType().toString()).isEqualTo(TIPO_PREFIXO + "illegal-argument");
    }

    @Test
    @DisplayName("handleMaxUploadSize → 413 payload-too-large")
    void handleMaxUploadSizeDeveRetornar413() {
        ProblemDetail problema = handler.handleMaxUploadSize(
                new MaxUploadSizeExceededException(5 * 1024 * 1024L), requisicao);

        assertThat(problema.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
        assertThat(problema.getTitle()).isEqualTo("Arquivo excede o tamanho máximo permitido");
        assertThat(problema.getType().toString()).isEqualTo(TIPO_PREFIXO + "payload-too-large");
    }

    @Test
    @DisplayName("handleGeneric → 500 internal com detalhe padrão sem vazar stack")
    void handleGenericDeveRetornar500SemVazarDetalheInterno() {
        ProblemDetail problema = handler.handleGeneric(
                new RuntimeException("NullPointerException em linha 42"), requisicao);

        assertThat(problema.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problema.getTitle()).isEqualTo("Erro interno do servidor");
        assertThat(problema.getDetail()).isEqualTo("Erro interno do servidor");
        assertThat(problema.getType().toString()).isEqualTo(TIPO_PREFIXO + "internal");
        assertThat(problema.getDetail()).doesNotContain("NullPointerException");
    }

    @Test
    @DisplayName("construirProblema → deve aceitar detalhe nulo sem lançar NPE")
    void construirProblemaDeveAceitarDetalheNulo() {
        ProblemDetail problema = handler.handleBusiness(new BusinessException(null), requisicao);

        assertThat(problema).isNotNull();
        assertThat(problema.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }
}
