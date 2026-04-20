# [E2-H2] Dockerfiles Multi-Stage — Serviços Auxiliares (Media, Vendor, Notification, Orchestrator)

## Resumo
**Como** engenheiro DevOps / desenvolvedor do time ArremateAI,
**Quero** que cada microsserviço auxiliar tenha um Dockerfile multi-stage otimizado e um .dockerignore configurado,
**Para** que possamos construir imagens Docker leves, seguras e prontas para orquestração com Docker Compose e futura implantação em produção, completando a containerização de todos os microsserviços da plataforma.

## Contexto
Esta é a segunda história do Épico E2 (Containerização com Docker). A **E2-H1** já foi concluída com sucesso, entregando Dockerfiles para os 4 serviços core (Gateway, Identity, Userprofile, Property-Catalog). Agora precisamos aplicar o mesmo padrão aos 4 serviços auxiliares. Diferente dos serviços core, estes possuem particularidades adicionais: o **Media** e o **Vendor** possuem diretórios `uploads/` que NÃO devem ser incluídos na imagem (serão volumes Docker); o **Notification** usa Flyway e depende de banco para iniciar; o **Media** e o **Orchestrator** NÃO usam banco de dados. Nenhum desses 4 repositórios possui Dockerfile ou .dockerignore atualmente.

## Pré-condições
- E2-H1 concluída (Dockerfiles dos serviços core funcionando)
- Padrão de Dockerfile multi-stage já validado no time
- Docker Engine instalado na máquina do desenvolvedor
- Acesso aos repositórios: `arremateai-media`, `arremateai-vendor`, `arremateai-notification`, `arremateai-orchestrator`

## Inventário dos Serviços

| Serviço | Porta | Artifact ID | Config | Banco | Flyway | Uploads |
|---|---|---|---|---|---|---|
| **Media** | 8083 | `arremateai-media-0.0.1-SNAPSHOT.jar` | `application.properties` | Nenhum | Não | `./uploads` (avatares, imoveis, videos) |
| **Vendor** | 8084 | `arremateai-vendor-0.0.1-SNAPSHOT.jar` | `application.properties` | `arremateai` (PostgreSQL) | Não (ddl-auto=none) | `./uploads/documentos` |
| **Notification** | 8086 | `arremateai-notification-0.0.1-SNAPSHOT.jar` | `application.properties` | `arremateai` (PostgreSQL) | Sim (`flyway_schema_history_notification`) | Nenhum |
| **Orchestrator** | 8087 | `arremateai-orchestrator-0.0.1-SNAPSHOT.jar` | `application.yml` | Nenhum | Não | Nenhum |

## Regras de Negócio

### Regras Herdadas da E2-H1 (Padrão do Time)

| # | Regra | Descrição |
|---|---|---|
| RN01 | Multi-stage obrigatório | Toda imagem DEVE usar build multi-stage: Stage 1 (build) com Maven+JDK, Stage 2 (runtime) com JRE slim |
| RN02 | Imagem base padronizada | Build: `maven:3.9-eclipse-temurin-17-alpine`. Runtime: `eclipse-temurin:17-jre-alpine` |
| RN03 | Usuário não-root | O container DEVE rodar com um usuário não-root (`appuser`, UID 1001) |
| RN04 | Porta correta | Cada Dockerfile DEVE expor (EXPOSE) apenas a porta do seu serviço |
| RN05 | HEALTHCHECK embutido | Cada Dockerfile DEVE conter instrução `HEALTHCHECK` usando `curl` contra `/actuator/health` |
| RN06 | .dockerignore obrigatório | Cada repositório DEVE ter um `.dockerignore` |
| RN07 | Sem secrets hardcoded | Nenhum valor de credencial pode estar no Dockerfile |
| RN08 | Cache de dependências Maven | Copiar `pom.xml` antes do código-fonte para cachear dependências |
| RN09 | JAR mínimo | Apenas o JAR fat deve ser copiado para o stage de runtime |
| RN10 | Tamanho de imagem | Imagem final < 300MB |

### Regras Específicas da E2-H2

| # | Regra | Descrição |
|---|---|---|
| RN11 | Exclusão de uploads | Os diretórios `uploads/` dos serviços Media e Vendor NÃO devem ser copiados para a imagem Docker. O `.dockerignore` DEVE conter a entrada `uploads/` |
| RN12 | Serviços sem banco devem subir sozinhos | Media e Orchestrator NÃO usam banco de dados. Devem iniciar com sucesso sem nenhuma variável DB_* configurada |
| RN13 | Serviço com Flyway requer banco | Notification usa Flyway e DEVE falhar com mensagem clara se o banco não estiver disponível ao iniciar |
| RN14 | Volumes para uploads | Media e Vendor devem documentar no Dockerfile (via VOLUME ou comentário) os diretórios que precisam de volume Docker para persistência |
| RN15 | Diretórios de upload pré-criados | No stage de runtime, os diretórios de upload (`/app/uploads/avatares`, `/app/uploads/imoveis`, `/app/uploads/videos` para Media; `/app/uploads/documentos` para Vendor) devem ser criados com permissões do `appuser` |

## Cenários BDD

### Cenário 1: Build com sucesso
```gherkin
Dado que o repositório do serviço "<servico>" possui um Dockerfile na raiz
E possui um .dockerignore na raiz
Quando eu executo "docker build -t arremateai/<servico>:latest ." na raiz do repositório
Então o build completa sem erros
E a imagem é criada com sucesso

Exemplos:
  | servico        |
  | media          |
  | vendor         |
  | notification   |
  | orchestrator   |
```

### Cenário 2: Usuário não-root
```gherkin
Dado que a imagem "arremateai/<servico>:latest" foi construída
Quando eu executo "docker run --rm --entrypoint whoami arremateai/<servico>:latest"
Então a saída deve ser "appuser"

Exemplos:
  | servico        |
  | media          |
  | vendor         |
  | notification   |
  | orchestrator   |
```

### Cenário 3: Porta correta exposta
```gherkin
Dado que a imagem "arremateai/<servico>:latest" foi construída
Quando eu inspeciono com "docker inspect arremateai/<servico>:latest"
Então a porta exposta deve ser "<porta>/tcp"

Exemplos:
  | servico        | porta |
  | media          | 8083  |
  | vendor         | 8084  |
  | notification   | 8086  |
  | orchestrator   | 8087  |
```

### Cenário 4: HEALTHCHECK configurado
```gherkin
Dado que a imagem "arremateai/<servico>:latest" foi construída
Quando eu inspeciono o HEALTHCHECK via "docker inspect"
Então deve conter chamada a "/actuator/health"
E intervalo=30s, timeout=10s, retries=3, start-period=40s

Exemplos:
  | servico        |
  | media          |
  | vendor         |
  | notification   |
  | orchestrator   |
```

### Cenário 5: Tamanho da imagem
```gherkin
Dado que a imagem "arremateai/<servico>:latest" foi construída
Quando verifico o tamanho com "docker images arremateai/<servico>:latest"
Então deve ser inferior a 300MB

Exemplos:
  | servico        |
  | media          |
  | vendor         |
  | notification   |
  | orchestrator   |
```

### Cenário 6: Media sobe sem banco de dados
```gherkin
Dado que o Media NÃO usa banco de dados
Quando eu inicio o container sem nenhuma variável DB_*
Então o container inicia com sucesso
E /actuator/health retorna 200
```

### Cenário 7: Orchestrator sobe sem banco de dados
```gherkin
Dado que o Orchestrator NÃO usa banco de dados
Quando eu inicio o container com INTERNAL_API_KEY e ADMIN_USER_ID definidos
E SEM nenhuma variável DB_*
Então o container inicia com sucesso
E /actuator/health retorna 200
```

### Cenário 8: Notification falha sem banco disponível
```gherkin
Dado que o Notification usa Flyway e requer PostgreSQL
Quando eu inicio o container com DB_HOST apontando para host inexistente
Então o Flyway falha ao tentar migrar
E o container encerra com exit code diferente de 0
```

### Cenário 9: Vendor falha sem banco disponível
```gherkin
Dado que o Vendor usa PostgreSQL (ddl-auto=none)
Quando eu inicio o container com DB_HOST apontando para host inexistente
Então o Spring Boot falha ao conectar no banco
E o container encerra com exit code diferente de 0
```

### Cenário 10: Diretório uploads/ NÃO está na imagem do Media
```gherkin
Dado que a imagem "arremateai/media:latest" foi construída
Quando eu executo "docker run --rm --entrypoint ls arremateai/media:latest /app/uploads/"
Então os diretórios "avatares", "imoveis" e "videos" existem (criados no Dockerfile)
Mas NÃO contêm nenhum arquivo de upload do host
```

### Cenário 11: Diretório uploads/ NÃO está na imagem do Vendor
```gherkin
Dado que a imagem "arremateai/vendor:latest" foi construída
Quando eu executo "docker run --rm --entrypoint ls arremateai/vendor:latest /app/uploads/"
Então o diretório "documentos" existe (criado no Dockerfile)
Mas NÃO contém nenhum arquivo de upload do host
```

### Cenário 12: Diretórios de upload com permissões corretas
```gherkin
Dado que a imagem "arremateai/<servico>:latest" foi construída
Quando eu verifico o dono dos diretórios de upload
Então devem pertencer ao usuário "appuser" (UID 1001)

Exemplos:
  | servico |
  | media   |
  | vendor  |
```

## Particularidades por Serviço

### Media (porta 8083)
- **Banco**: NÃO usa banco de dados. Sem variáveis `DB_*`
- **Config**: `application.properties`
- **Upload**: Sistema de uploads com limite de até 510MB por arquivo (`spring.servlet.multipart.max-file-size=510MB`)
- **Diretórios de upload**: `./uploads/avatares`, `./uploads/imoveis`, `./uploads/videos`
- **3 tipos de arquivo**: imagens (max 5MB), vídeos (max 500MB), avatares (max 2MB)
- **Volume Docker**: Necessário para persistência. O `VOLUME` deve apontar para `/app/uploads`
- **Exclusão**: O diretório `uploads/` do host NÃO deve ser copiado para a imagem (via `.dockerignore`)
- **Criação de diretórios**: O Dockerfile deve criar `/app/uploads/avatares`, `/app/uploads/imoveis`, `/app/uploads/videos` com permissão do `appuser`
- **Variáveis de ambiente opcionais**: `MEDIA_BASE_URL`

### Vendor (porta 8084)
- **Banco**: PostgreSQL (database `arremateai`). Variáveis: `DB_USERNAME` (default: `arremateai`), `DB_PASSWORD` (default: `arremateai123`)
- **Config**: `application.properties`
- **Flyway**: NÃO usa (ddl-auto=none). O schema deve ser gerenciado externamente
- **Upload**: Documentos até 10MB (`spring.servlet.multipart.max-file-size=10MB`)
- **Diretório de upload**: `./uploads/documentos`
- **Volume Docker**: Necessário para persistência. O `VOLUME` deve apontar para `/app/uploads`
- **Exclusão**: O diretório `uploads/` do host NÃO deve ser copiado para a imagem (via `.dockerignore`)
- **Criação de diretórios**: O Dockerfile deve criar `/app/uploads/documentos` com permissão do `appuser`
- **Email SMTP**: Gmail configurado via variáveis `EMAIL_USERNAME`, `EMAIL_PASSWORD`
- **Admin**: `ADMIN_EMAIL` para notificações administrativas
- **Health check**: `management.health.mail.enabled=false` — o health check NÃO valida SMTP

### Notification (porta 8086)
- **Banco**: PostgreSQL (database `arremateai`). Variáveis: `DB_USERNAME` (default: `arremateai`), `DB_PASSWORD` (default: `arremateai123`)
- **Config**: `application.properties`
- **Flyway**: SIM — tabela `flyway_schema_history_notification`, `baseline-on-migrate=true`
- **Upload**: NÃO possui sistema de uploads
- **Segurança inter-serviço**: Requer `INTERNAL_API_KEY` para comunicação entre microsserviços
- **Health check**: `management.health.mail.enabled=false` — o health check NÃO valida SMTP

### Orchestrator (porta 8087)
- **Banco**: NÃO usa banco de dados. Sem variáveis `DB_*`
- **Config**: `application.yml` (ATENÇÃO: diferente dos demais que usam `.properties`)
- **Flyway**: NÃO
- **Upload**: NÃO possui sistema de uploads
- **Circuit Breaker**: Resilience4j integrado para comunicação com outros serviços
- **Dependências de serviço**: Comunica com property-catalog (8082), media (8083), vendor (8084), user-profile (8085), notification (8086)
- **Variáveis obrigatórias**: `INTERNAL_API_KEY` (para notification), `ADMIN_USER_ID`
- **URLs dos serviços**: Configuráveis via variáveis de ambiente no `docker-compose.yml` futuro (E2-H4)
- **Swagger**: Expõe documentação em `/api/orchestrator/swagger-ui.html`

## Padrão do Dockerfile (Referência)
```dockerfile
# ============================================================
# Stage 1: Build
# ============================================================
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /build

# Cache Maven dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Build application
COPY src/ src/
RUN mvn package -DskipTests -B

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache curl \
    && addgroup -g 1001 appgroup \
    && adduser -u 1001 -G appgroup -D appuser

WORKDIR /app

COPY --from=build /build/target/<artifact>.jar app.jar

# [Se aplicável: criar diretórios de upload e definir VOLUME]

RUN chown -R appuser:appgroup /app
USER appuser

EXPOSE <porta>

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=40s \
    CMD curl -f http://localhost:<porta>/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
```

## Padrão do .dockerignore
```
target/
.git/
.gitignore
.idea/
*.iml
.vscode/
.mvn/
mvnw
mvnw.cmd
*.md
.env
.env.*
docker-compose*.yml
Dockerfile
.dockerignore
uploads/
.DS_Store
Thumbs.db
docs/
```

## Variáveis de Ambiente por Serviço

### Media
| Variável | Obrigatória | Default | Descrição |
|---|---|---|---|
| `MEDIA_BASE_URL` | Não | `http://localhost:8083` | URL base para gerar links de mídia |

### Vendor
| Variável | Obrigatória | Default | Descrição |
|---|---|---|---|
| `DB_USERNAME` | Não | `arremateai` | Usuário do PostgreSQL |
| `DB_PASSWORD` | Não | `arremateai123` | Senha do PostgreSQL |
| `EMAIL_USERNAME` | Não | `noreply@arremateai.com` | Remetente SMTP |
| `EMAIL_PASSWORD` | Não | (vazio) | Senha SMTP |
| `ADMIN_EMAIL` | Não | `admin@arremateai.com` | Email do administrador |
| `VENDOR_BASE_URL` | Não | `http://localhost:8084` | URL base para gerar links |

### Notification
| Variável | Obrigatória | Default | Descrição |
|---|---|---|---|
| `DB_USERNAME` | Não | `arremateai` | Usuário do PostgreSQL |
| `DB_PASSWORD` | Não | `arremateai123` | Senha do PostgreSQL |
| `INTERNAL_API_KEY` | Sim | (vazio) | Chave para autenticação inter-serviço |

### Orchestrator
| Variável | Obrigatória | Default | Descrição |
|---|---|---|---|
| `INTERNAL_API_KEY` | Sim | (sem default) | Chave para autenticação com Notification |
| `ADMIN_USER_ID` | Sim | (vazio) | UUID do usuário administrador |

## Estimativa de Complexidade
- **Complexidade**: Baixa — segue padrão já estabelecido na E2-H1
- **Variações**: Media e Vendor exigem criação de diretórios de upload e VOLUME
- **Story Points**: 3

## Definição de Pronto
- [ ] Dockerfile criado na raiz dos 4 repositórios (media, vendor, notification, orchestrator)
- [ ] .dockerignore criado na raiz dos 4 repositórios
- [ ] `docker build` passa sem erros nos 4 serviços
- [ ] Containers rodam com usuário não-root (`appuser`)
- [ ] HEALTHCHECK configurado nos 4 Dockerfiles (interval=30s, timeout=10s, retries=3, start-period=40s)
- [ ] Media inicia sem banco de dados
- [ ] Orchestrator inicia sem banco de dados (com INTERNAL_API_KEY e ADMIN_USER_ID)
- [ ] Notification falha com erro claro se banco indisponível
- [ ] Vendor falha com erro claro se banco indisponível
- [ ] Diretórios `uploads/` do host NÃO são copiados para imagens de Media e Vendor
- [ ] Diretórios de upload pré-criados com permissão do `appuser` (Media e Vendor)
- [ ] Imagens finais < 300MB cada
- [ ] Nenhum secret hardcoded nos Dockerfiles

## Notas
- O padrão de Dockerfile e .dockerignore é idêntico ao da E2-H1, com adição de diretórios de upload para Media e Vendor
- O Orchestrator usa `application.yml` (não `.properties`) — mesma situação do Gateway na E2-H1
- Os serviços Media e Orchestrator são os mais simples por não dependerem de banco
- A comunicação inter-serviço via `INTERNAL_API_KEY` (Notification e Orchestrator) será configurada no `docker-compose.yml` (E2-H4)
- As URLs dos serviços dependentes do Orchestrator (`services.*.url`) serão sobrescritas via `docker-compose.yml` com nomes de serviço Docker
- Esta história é pré-requisito para E2-H4 (docker-compose.yml com todos os 8 serviços)
