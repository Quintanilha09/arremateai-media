package com.arremateai.media.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Tratador global de exceções no formato RFC 7807 (application/problem+json).
 *
 * <p>Converte todas as exceções não tratadas em respostas {@link ProblemDetail}
 * padronizadas, com tipo URN {@code urn:arremateai:error:*}, título, detalhe,
 * instância, timestamp e path. Exceções 4xx são logadas em WARN (sem stack);
 * 5xx em ERROR (com stack). O corpo de 5xx nunca expõe detalhes internos.</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String TYPE_PREFIX = "urn:arremateai:error:";
    private static final String DETALHE_INTERNO = "Erro interno do servidor";
    private static final String DETALHE_VALIDACAO = "Um ou mais campos não passaram na validação.";

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex, HttpServletRequest requisicao) {
        log.warn("Regra de negócio violada em {}: {}", requisicao.getRequestURI(), ex.getMessage());
        return construirProblema(HttpStatus.BAD_REQUEST, "Regra de negócio violada",
                ex.getMessage(), "business", requisicao);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest requisicao) {
        log.warn("Argumento inválido em {}: {}", requisicao.getRequestURI(), ex.getMessage());
        return construirProblema(HttpStatus.BAD_REQUEST, "Argumento inválido",
                ex.getMessage(), "illegal-argument", requisicao);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException ex, HttpServletRequest requisicao) {
        log.warn("Estado inválido em {}: {}", requisicao.getRequestURI(), ex.getMessage());
        return construirProblema(HttpStatus.CONFLICT, "Operação em conflito com o estado atual",
                ex.getMessage(), "conflict", requisicao);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest requisicao) {
        List<Map<String, String>> erros = ex.getBindingResult().getFieldErrors().stream()
                .map(campo -> Map.of(
                        "field", campo.getField(),
                        "message", Optional.ofNullable(campo.getDefaultMessage()).orElse("inválido")))
                .toList();
        log.warn("Validação falhou em {}: {} erro(s)", requisicao.getRequestURI(), erros.size());
        ProblemDetail problema = construirProblema(HttpStatus.BAD_REQUEST,
                "Dados de entrada inválidos", DETALHE_VALIDACAO, "validation", requisicao);
        problema.setProperty("errors", erros);
        return problema;
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest requisicao) {
        String nomeHeader = ex.getHeaderName();
        if (nomeHeader != null && nomeHeader.startsWith("X-User-")) {
            log.warn("Header de autenticação ausente em {}: {}", requisicao.getRequestURI(), nomeHeader);
            return construirProblema(HttpStatus.UNAUTHORIZED, "Autenticação necessária",
                    "Header obrigatório ausente: " + nomeHeader, "unauthenticated", requisicao);
        }
        log.warn("Header obrigatório ausente em {}: {}", requisicao.getRequestURI(), nomeHeader);
        return construirProblema(HttpStatus.BAD_REQUEST, "Requisição inválida",
                ex.getMessage(), "illegal-argument", requisicao);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest requisicao) {
        log.warn("Upload excedeu tamanho máximo em {}: {}", requisicao.getRequestURI(), ex.getMessage());
        return construirProblema(HttpStatus.PAYLOAD_TOO_LARGE,
                "Arquivo excede o tamanho máximo permitido",
                "Arquivo excede o tamanho máximo permitido.", "payload-too-large", requisicao);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex, HttpServletRequest requisicao) {
        log.error("Erro interno não tratado em {}", requisicao.getRequestURI(), ex);
        return construirProblema(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno do servidor",
                DETALHE_INTERNO, "internal", requisicao);
    }

    private ProblemDetail construirProblema(HttpStatus status, String titulo, String detalhe,
                                             String tipoSufixo, HttpServletRequest requisicao) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status,
                detalhe == null ? "" : detalhe);
        problema.setTitle(titulo);
        problema.setType(URI.create(TYPE_PREFIX + tipoSufixo));
        problema.setInstance(URI.create(requisicao.getRequestURI()));
        problema.setProperty("timestamp", OffsetDateTime.now().toString());
        problema.setProperty("path", requisicao.getRequestURI());
        return problema;
    }
}
