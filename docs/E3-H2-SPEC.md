# [E3-H2] Migrar Media Service do Armazenamento Local para MinIO (S3)

**Épico:** [E3] Stateless + Storage Externo
**Microsserviço(s):** arremateai-media (porta 8083)
**Story Points:** 5
**Prioridade:** alta

---

## 1. Objetivo

Substituir o armazenamento local em disco (`./uploads`) do Media Service pela biblioteca compartilhada `arremateai-storage-lib` (MinIO/S3), eliminando a dependência de volumes Docker e tornando o serviço **stateless** — pré-requisito para escalabilidade horizontal e deploys zero-downtime.

---

## 2. Contexto Técnico

### Estado Atual

O `StorageService` atual (`com.arremateai.media.service.StorageService`) opera inteiramente sobre o sistema de arquivos local:

- **Escrita**: `java.nio.file.Files.copy()` para `./uploads/{subdir}/{uuid}.{ext}`
- **Leitura**: `WebConfig` mapeia `/uploads/**` como recurso estático via `ResourceHandlerRegistry`
- **Deleção**: `Files.deleteIfExists()` com validação de path traversal
- **Subdiretórios**: `imoveis/`, `videos/`, `avatares/` — criados no construtor do service
- **URLs retornadas**: `{baseUrl}/uploads/imoveis/{uuid}.jpg` (servidas pelo próprio serviço)

### Estado Desejado

- **Escrita**: Delegada ao `com.arremateai.storage.StorageService` (interface da storage-lib) → MinIO
- **Leitura**: URLs presigned do MinIO (tempo limitado) ou URLs públicas via proxy do Gateway
- **Deleção**: Via `storageService.delete(key)` da storage-lib
- **Validações**: Permanecem idênticas (tamanho, extensão, ImageIO) — executadas ANTES do upload ao MinIO
- **API Contract**: Endpoints REST inalterados; apenas o formato da URL de resposta muda

### Mapeamento de Prefixos S3

| Subdiretório Local | Prefixo S3 (key) | Bucket |
|---|---|---|
| `./uploads/imoveis/` | `media/imoveis/` | `arremateai-media` |
| `./uploads/videos/` | `media/videos/` | `arremateai-media` |
| `./uploads/avatares/` | `media/avatares/` | `arremateai-media` |

### Propriedades de Configuração (novas)

| Propriedade | Variável de Ambiente | Valor Default (dev) |
|---|---|---|
| `arremateai.storage.type` | `ARREMATEAI_STORAGE_TYPE` | `minio` |
| `arremateai.storage.endpoint` | `ARREMATEAI_STORAGE_ENDPOINT` | `http://localhost:9000` |
| `arremateai.storage.access-key` | `ARREMATEAI_STORAGE_ACCESS_KEY` | `arremateai` |
| `arremateai.storage.secret-key` | `ARREMATEAI_STORAGE_SECRET_KEY` | `arremateai123` |
| `arremateai.storage.bucket` | `ARREMATEAI_STORAGE_BUCKET` | `arremateai-media` |
| `arremateai.storage.region` | `ARREMATEAI_STORAGE_REGION` | `us-east-1` |

### Propriedades Removidas

| Propriedade | Motivo |
|---|---|
| `app.media.storage-location` | Substituída por MinIO — sem diretório local |

### Propriedades Mantidas

| Propriedade | Valor | Motivo |
|---|---|---|
| `app.media.base-url` | `${MEDIA_BASE_URL:http://localhost:8083}` | Usado na construção de URLs de proxy/fallback |
| `app.media.max-image-size` | `5242880` | Validação pré-upload |
| `app.media.max-video-size` | `524288000` | Validação pré-upload |
| `app.media.max-avatar-size` | `2097152` | Validação pré-upload |

---

## 3. Requisitos Funcionais

- [ ] RF-01: Adicionar dependência `arremateai-storage-lib` ao `pom.xml` do Media Service
- [ ] RF-02: Refatorar `StorageService` para usar a interface `com.arremateai.storage.StorageService` (da lib) no lugar de `java.nio.file.*`
- [ ] RF-03: Manter TODAS as validações existentes (tamanho, extensão, ImageIO) — executá-las ANTES de chamar a storage-lib
- [ ] RF-04: O método `salvarImagem()` deve fazer upload via `storageService.upload(key, inputStream, contentType)` com key `media/imoveis/{uuid}.{ext}` e retornar a URL presigned ou URL do proxy
- [ ] RF-05: O método `salvarVideo()` deve fazer upload com key `media/videos/{uuid}.{ext}`
- [ ] RF-06: O método `salvarAvatar()` deve fazer upload com key `media/avatares/{uuid}.{ext}`
- [ ] RF-07: O método `deletarArquivo()` deve extrair a key S3 da URL recebida e chamar `storageService.delete(key)`
- [ ] RF-08: Criar endpoint `GET /api/media/files/**` que funciona como proxy — recebe o path, gera presigned URL via `storageService.getPresignedUrl(key)` e redireciona (HTTP 302)
- [ ] RF-09: Remover a classe `WebConfig.java` (handler de recursos estáticos para `/uploads/**`)
- [ ] RF-10: Adicionar propriedades de configuração MinIO no `application.properties`
- [ ] RF-11: Remover a propriedade `app.media.storage-location`
- [ ] RF-12: Atualizar `Dockerfile` removendo criação de diretórios `/app/uploads/*` e instrução VOLUME
- [ ] RF-13: Atualizar `docker-compose.yml` removendo volume `arremateai-media-uploads` do service media e adicionando variáveis de ambiente `ARREMATEAI_STORAGE_*`
- [ ] RF-14: Atualizar testes unitários de `StorageServiceTest` para mockar a interface da storage-lib
- [ ] RF-15: Criar teste de integração com Testcontainers (MinIO container) validando upload/download/delete real

---

## 4. Requisitos Não-Funcionais

- [ ] RNF-01: Upload de imagem (≤ 5MB) deve completar em < 3 segundos em ambiente local (MinIO Docker)
- [ ] RNF-02: Upload de vídeo (≤ 500MB) deve completar em < 60 segundos em ambiente local
- [ ] RNF-03: URLs presigned devem ter validade de 1 hora (3600 segundos)
- [ ] RNF-04: O serviço deve iniciar com sucesso (health check UP) mesmo que o bucket ainda não exista — o bucket deve ser criado automaticamente no primeiro uso
- [ ] RNF-05: Não deve haver nenhum secret (access-key, secret-key) hardcoded no código — apenas via environment variables ou application.properties com placeholders

---

## 5. Regras de Negócio

| # | Regra | Descrição |
|---|---|---|
| RN-01 | Validação antes do upload | TODAS as validações (tamanho, extensão, conteúdo real) DEVEM ser executadas ANTES de enviar o arquivo ao MinIO. Se a validação falhar, o arquivo NÃO é enviado e o erro 400 é retornado com a mesma mensagem atual. |
| RN-02 | Key S3 com prefixo por tipo | Cada tipo de arquivo deve ser armazenado com prefixo distinto: `media/imoveis/` para imagens de imóveis, `media/videos/` para vídeos, `media/avatares/` para avatares. O nome do arquivo é sempre `{UUID}.{extensão}`. |
| RN-03 | URL retornada deve ser acessível | A URL retornada pelo endpoint de upload deve permitir acesso direto ao arquivo. Pode ser uma URL presigned do MinIO ou uma URL do proxy (`/api/media/files/{key}`). |
| RN-04 | Deleção por URL completa | O endpoint de deleção recebe a URL completa do arquivo. O serviço deve ser capaz de extrair a key S3 da URL (seja presigned ou proxy) e deletar o objeto do MinIO. |
| RN-05 | Contrato de API preservado | Os endpoints REST (`POST /api/media/imagens`, `POST /api/media/videos`, `POST /api/media/avatares`, `DELETE /api/media`) DEVEM manter exatamente os mesmos paths, métodos HTTP, headers obrigatórios (`X-User-Id`) e formato de response (`{"url": "..."}` para POST, 204 para DELETE). |
| RN-06 | Formato de resposta de erro preservado | Erros de validação devem continuar retornando `{"timestamp", "status": 400, "error", "message": "..."}` com as mesmas mensagens de texto. |

---

## 6. Critérios de Aceite

- [ ] CA-01: `POST /api/media/imagens` com arquivo JPG válido (< 5MB) retorna 200 com `{"url": "..."}` e o arquivo está acessível no MinIO via a URL retornada
- [ ] CA-02: `POST /api/media/videos` com arquivo MP4 válido retorna 200 com URL acessível
- [ ] CA-03: `POST /api/media/avatares` com arquivo PNG válido (< 2MB) retorna 200 com URL acessível
- [ ] CA-04: `DELETE /api/media?url={url}` com URL de arquivo existente retorna 204 e o objeto é removido do MinIO
- [ ] CA-05: Upload de arquivo > 5MB retorna 400 com mensagem "Imagem excede o tamanho máximo de 5MB"
- [ ] CA-06: Upload de arquivo com extensão `.gif` retorna 400 com mensagem "Imagem deve ser um dos formatos: jpg, jpeg, png, webp"
- [ ] CA-07: Upload de arquivo com extensão `.jpg` mas que não é imagem real retorna 400 com mensagem "Arquivo não é uma imagem válida"
- [ ] CA-08: O serviço inicia com sucesso (`/actuator/health` retorna UP) com MinIO rodando
- [ ] CA-09: Não existe mais o diretório `./uploads` no container em execução
- [ ] CA-10: Não existe mais o mapeamento `/uploads/**` como recurso estático
- [ ] CA-11: O endpoint `GET /api/media/files/media/imoveis/{uuid}.jpg` redireciona (302) para a URL presigned do MinIO
- [ ] CA-12: Todos os testes unitários passam (cobertura ≥ 90% no `StorageService` refatorado)
- [ ] CA-13: Teste de integração com Testcontainers MinIO passa (upload + download + delete)
- [ ] CA-14: `docker build` completa com sucesso e imagem < 300MB

---

## 7. Cenários BDD

```gherkin
Cenário: Upload de imagem com sucesso via MinIO
  Dado que o MinIO está rodando e o bucket "arremateai-media" existe
  E o usuário autenticado possui header "X-User-Id" válido
  Quando o usuário faz POST para "/api/media/imagens" com um arquivo JPG de 2MB
  Então o response status é 200
  E o body contém {"url": "<url_acessível>"}
  E o objeto existe no MinIO com key "media/imoveis/<uuid>.jpg"

Cenário: Upload de vídeo com sucesso via MinIO
  Dado que o MinIO está rodando e o bucket "arremateai-media" existe
  E o usuário autenticado possui header "X-User-Id" válido
  Quando o usuário faz POST para "/api/media/videos" com um arquivo MP4 de 50MB
  Então o response status é 200
  E o body contém {"url": "<url_acessível>"}
  E o objeto existe no MinIO com key "media/videos/<uuid>.mp4"

Cenário: Upload de avatar com sucesso via MinIO
  Dado que o MinIO está rodando e o bucket "arremateai-media" existe
  E o usuário autenticado possui header "X-User-Id" válido
  Quando o usuário faz POST para "/api/media/avatares" com um arquivo PNG de 1MB
  Então o response status é 200
  E o body contém {"url": "<url_acessível>"}
  E o objeto existe no MinIO com key "media/avatares/<uuid>.png"

Cenário: Rejeitar imagem que excede tamanho máximo
  Dado que o usuário autenticado possui header "X-User-Id" válido
  Quando o usuário faz POST para "/api/media/imagens" com um arquivo JPG de 10MB
  Então o response status é 400
  E o body contém "Imagem excede o tamanho máximo de 5MB"
  E NENHUM objeto é criado no MinIO

Cenário: Rejeitar arquivo com extensão inválida
  Dado que o usuário autenticado possui header "X-User-Id" válido
  Quando o usuário faz POST para "/api/media/imagens" com um arquivo "foto.gif"
  Então o response status é 400
  E o body contém "Imagem deve ser um dos formatos: jpg, jpeg, png, webp"
  E NENHUM objeto é criado no MinIO

Cenário: Rejeitar arquivo que não é imagem real
  Dado que o usuário autenticado possui header "X-User-Id" válido
  Quando o usuário faz POST para "/api/media/imagens" com um arquivo "fake.jpg" (texto renomeado)
  Então o response status é 400
  E o body contém "Arquivo não é uma imagem válida"
  E NENHUM objeto é criado no MinIO

Cenário: Deletar arquivo existente do MinIO
  Dado que o MinIO contém um objeto com key "media/imoveis/abc123.jpg"
  E o usuário autenticado possui header "X-User-Id" válido
  Quando o usuário faz DELETE para "/api/media" com url do arquivo
  Então o response status é 204
  E o objeto "media/imoveis/abc123.jpg" não existe mais no MinIO

Cenário: Proxy endpoint redireciona para URL presigned
  Dado que o MinIO contém um objeto com key "media/imoveis/abc123.jpg"
  Quando um GET é feito para "/api/media/files/media/imoveis/abc123.jpg"
  Então o response status é 302
  E o header "Location" contém uma URL presigned válida do MinIO
```

---

## 8. Arquivos Alvo (guia para o dev — pode ser ajustado durante a implementação)

| Ação | Arquivo | Observação |
|------|---------|------------|
| Modificar | `pom.xml` | Adicionar dependência `arremateai-storage-lib` |
| Modificar | `src/main/resources/application.properties` | Adicionar props `arremateai.storage.*`, remover `app.media.storage-location` |
| Refatorar | `src/main/java/com/arremateai/media/service/StorageService.java` | Substituir `java.nio.file.*` por calls à storage-lib. Manter validações. |
| Remover | `src/main/java/com/arremateai/media/config/WebConfig.java` | Handler de recurso estático não é mais necessário |
| Criar | `src/main/java/com/arremateai/media/controller/FileProxyController.java` | Endpoint `GET /api/media/files/**` — proxy para presigned URLs |
| Modificar | `Dockerfile` | Remover `mkdir` de `/app/uploads/*` e referências a VOLUME |
| Refatorar | `src/test/java/com/arremateai/media/service/StorageServiceTest.java` | Mockar `com.arremateai.storage.StorageService`, testar validações + delegação |
| Criar | `src/test/java/com/arremateai/media/integration/MinioIntegrationTest.java` | Testcontainers com MinIO real |
| Modificar | `src/test/java/com/arremateai/media/controller/UploadControllerTest.java` | Verificar se testes existentes continuam passando sem alteração |

**Arquivo externo (fora do repositório):**

| Ação | Arquivo | Observação |
|------|---------|------------|
| Modificar | `c:\Projetos\docker-compose.yml` | Remover volume `arremateai-media-uploads` do service media. Adicionar env vars `ARREMATEAI_STORAGE_*`. Manter definição do volume no top-level (E3-H4 remove). |

---

## 9. Dependências

- **Depende de:** [E3-H1] Setup MinIO + Biblioteca Storage — a `arremateai-storage-lib` deve estar publicada/instalada no repositório Maven local antes de iniciar esta história
- **Bloqueia:** [E3-H3] Migrar Userprofile + Vendor para MinIO — usa o mesmo padrão de integração com a storage-lib
- **Bloqueia:** [E3-H4] Garantir Stateless + Limpeza — validação final de que o Media Service não usa disco local

---

## 10. Notas para o Desenvolvedor

### Padrão de Refatoração do StorageService

O `StorageService` atual tem duas responsabilidades:
1. **Validação** (tamanho, extensão, ImageIO) — MANTER
2. **Persistência** (Files.copy, Files.deleteIfExists) — SUBSTITUIR

A refatoração ideal:

```java
@Service
public class StorageService {
    // Injetar a interface da storage-lib
    private final com.arremateai.storage.StorageService storageClient;
    
    // Manter @Value para max sizes
    
    public String salvarImagem(MultipartFile file) throws IOException {
        validarTamanho(file, maxImageSize, "Imagem");
        String ext = extrairExtensao(file.getOriginalFilename());
        validarExtensao(ext, ALLOWED_IMAGE_EXTENSIONS, "Imagem");
        validarImagemReal(file);
        
        String key = "media/imoveis/" + UUID.randomUUID() + "." + ext;
        storageClient.upload(key, file.getInputStream(), file.getContentType());
        return storageClient.getPresignedUrl(key); // ou URL do proxy
    }
}
```

### Construtor do StorageService

O construtor atual cria diretórios locais. Após a migração, remover toda lógica de `Files.createDirectories()`. O construtor deve apenas injetar o `storageClient` e as configurações de tamanho máximo.

### Extração de Key para Deleção

Atualmente `deletarArquivo()` faz parse da URL para extrair o path relativo no filesystem. Após a migração, o parse precisa extrair a key S3:

- Se a URL for presigned do MinIO: extrair o path do objeto (parte antes de `?`)
- Se a URL for do proxy (`/api/media/files/media/imoveis/abc.jpg`): extrair a parte após `/api/media/files/`

Recomendação: criar método `private String extrairKey(String url)` que trata ambos os formatos.

### FileProxyController

Endpoint simples que recebe o path S3, gera presigned URL via storage-lib e retorna redirect 302. Exemplo:

```java
@GetMapping("/files/**")
public ResponseEntity<Void> proxyFile(HttpServletRequest request) {
    String key = request.getRequestURI().substring("/api/media/files/".length());
    String presignedUrl = storageClient.getPresignedUrl(key);
    return ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, presignedUrl)
            .build();
}
```

### docker-compose.yml — Alterações Mínimas

Apenas no bloco do service `media`:
```yaml
media:
  build: { context: ./arremateai-media }
  profiles: [full]
  ports: ["8083:8083"]
  environment:
    MEDIA_BASE_URL: http://localhost:8080
    ARREMATEAI_STORAGE_TYPE: minio
    ARREMATEAI_STORAGE_ENDPOINT: http://minio:9000
    ARREMATEAI_STORAGE_ACCESS_KEY: arremateai
    ARREMATEAI_STORAGE_SECRET_KEY: arremateai123
    ARREMATEAI_STORAGE_BUCKET: arremateai-media
  # REMOVIDO: volumes: [arremateai-media-uploads:/app/uploads]
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:8083/actuator/health"]
    interval: 30s
    timeout: 10s
    retries: 3
  depends_on:
    minio:
      condition: service_healthy
```

### Atenção: NÃO remover o volume top-level `arremateai-media-uploads`

O volume fica definido no `volumes:` top-level do docker-compose. NÃO removê-lo nesta história — a E3-H4 fará a limpeza final após validar que NENHUM serviço ainda usa volumes locais.

### Testcontainers para MinIO

Usar a imagem `minio/minio:latest` com Testcontainers GenericContainer:

```java
@Container
static GenericContainer<?> minio = new GenericContainer<>("minio/minio:latest")
    .withExposedPorts(9000)
    .withEnv("MINIO_ROOT_USER", "minioadmin")
    .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
    .withCommand("server /data");
```

---

## 11. Notas para o QA

### Como Testar em Ambiente Local

1. Subir MinIO via docker-compose (`docker compose up minio -d`)
2. Subir Media Service com `ARREMATEAI_STORAGE_TYPE=minio`
3. Acessar MinIO Console em `http://localhost:9001` (admin/arremateai123) para verificar objetos

### Testes Manuais Essenciais

1. **Upload de imagem**: `curl -X POST -H "X-User-Id: test" -F "file=@foto.jpg" http://localhost:8083/api/media/imagens` → verificar no MinIO Console que o objeto está em `media/imoveis/`
2. **Upload de vídeo**: Mesmo teste com arquivo `.mp4` — verificar em `media/videos/`
3. **Upload de avatar**: Mesmo teste com arquivo pequeno — verificar em `media/avatares/`
4. **Acessar arquivo**: Copiar URL retornada → abrir no navegador → arquivo deve ser baixado/exibido
5. **Deletar arquivo**: `curl -X DELETE -H "X-User-Id: test" "http://localhost:8083/api/media?url={url}"` → verificar no MinIO Console que o objeto foi removido
6. **Proxy endpoint**: Acessar `http://localhost:8083/api/media/files/media/imoveis/{uuid}.jpg` → deve redirecionar para URL presigned

### Edge Cases

- Upload de arquivo com nome contendo caracteres especiais (acentos, espaços)
- Upload de arquivo com tamanho exatamente no limite (5MB para imagem, 2MB para avatar)
- Deleção de arquivo que não existe no MinIO (deve retornar 204 mesmo assim — idempotente)
- Upload quando MinIO está fora do ar → deve retornar 500 com mensagem genérica (NÃO expor detalhes do MinIO)
- Acessar proxy endpoint com key inexistente → deve retornar 404

### Verificar Não-Regressão

- Endpoints de upload retornam exatamente o mesmo formato `{"url": "..."}` (status 200)
- Endpoint de deleção retorna exatamente 204 sem body
- Erros de validação retornam exatamente o mesmo formato `{"timestamp", "status", "error", "message"}`
- Header `X-User-Id` continua sendo obrigatório (teste sem header → 401)

---

## 12. Definition of Done

### DoD — Backend (Java/Spring Boot)
- [ ] Código implementado e compilando sem erros
- [ ] Testes unitários passando (cobertura ≥ 90% nos cenários especificados)
- [ ] Teste de integração com Testcontainers MinIO passando
- [ ] Serviço inicia e responde em /actuator/health com MinIO disponível
- [ ] Endpoints documentados (Swagger/OpenAPI quando aplicável)
- [ ] Sem secrets ou senhas hardcoded no código
- [ ] Code review aprovado pelo @revisor-codigo

### DoD — Infraestrutura (Docker)
- [ ] Dockerfile atualizado (sem referências a /app/uploads)
- [ ] docker-compose.yml atualizado (sem volume mount, com env vars MinIO)
- [ ] `docker build` completa com sucesso e imagem < 300MB
- [ ] Validação end-to-end executada com sucesso (upload via curl → arquivo no MinIO → acesso via URL)
- [ ] Sem secrets expostos em arquivos versionados

---

## 13. Extras

### Estimativa de Risco: **Médio**

**Justificativa:** A refatoração do `StorageService` é bem delimitada (substituir filesystem por storage-lib), mas há risco médio porque:

1. **URLs mudam de formato** — qualquer serviço que persista URLs de media (ex: `ImagemImovel.url` no Property-Catalog) terá URLs antigas em formato `/uploads/...`. É preciso garantir que URLs antigas continuem sendo resolvidas ou planejar migração de dados (fora do escopo desta história).
2. **Vídeos grandes (500MB)** — upload para MinIO pode sofrer timeout em redes lentas. Considerar multipart upload da SDK MinIO para vídeos grandes.
3. **Testcontainers** — testes de integração dependem do Docker estar disponível na máquina do dev e no CI.

### Estratégia de Rollback

Se algo der errado em produção:
- Alterar `arremateai.storage.type` de `minio` para `local` (se a storage-lib suportar implementação local)
- OU reverter para a versão anterior da imagem Docker (que ainda usa filesystem)
- Arquivos já gravados no MinIO permanecerão lá (não são perdidos)

### Impacto em Outros Serviços

| Serviço | Impacto | Ação Necessária |
|---|---|---|
| **arremateai-front** | URLs de imagem/vídeo mudarão de formato | O frontend já trata URLs como strings opacas — sem alteração necessária se as URLs forem acessíveis |
| **arremateai-property-catalog** | Tabela `imagem_imovel.url` contém URLs no formato antigo | URLs antigas precisarão de migração futura ou compatibilidade no proxy |
| **arremateai-userprofile** | Chama `/api/media/avatares` para upload | Sem alteração — API contract preservado |
| **arremateai-gateway** | Roteia para Media Service | Sem alteração — mesmos paths |
