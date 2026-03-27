# 📸 ArremateAI - Media Service

Microsserviço especializado em upload, processamento e gerenciamento de arquivos de mídia (imagens e vídeos dos imóveis).

## 📋 Descrição

O Media Service é responsável por toda a gestão de arquivos multimídia da plataforma:

- **Upload de imagens** (JPEG, PNG, WEBP)
- **Upload de vídeos** (MP4, AVI, MOV)
- **Processamento de imagens** (resize, compressão, thumbnails)
- **Processamento de vídeos** (compressão, thumbnails, preview)
- **Armazenamento S3** (AWS)
- **CDN CloudFront** para entrega otimizada
- **Validação de arquivos** (tipo, tamanho, dimensões)
- **Organização por contexto** (perfil, imóvel, documento)

## 🛠️ Tecnologias

- **Java 17** (LTS)
- **Spring Boot 3.2.2**
- **Spring Web** - REST endpoints
- **AWS S3 SDK 2.20.26** - Armazenamento de arquivos
- **Thumbnailator 0.4.20** - Processamento de imagens
- **Apache Tika 2.9.1** - Detecção de tipo MIME
- **Commons IO 2.15.1** - Manipulação de arquivos

## 🏗️ Arquitetura

```
┌──────────────────┐
│  Gateway :8080   │
└────────┬─────────┘
         │
         ▼
┌─────────────────────────────┐
│     Media Service           │
│       (Port 8085)           │
├─────────────────────────────┤
│ Controllers                 │
│  └─ UploadController        │
├─────────────────────────────┤
│ Services                    │
│  └─ StorageService          │
├─────────────────────────────┤
│ Config                      │
│  └─ WebConfig               │
└─────────┬───────────────────┘
          │
          ▼
    ┌──────────┐
    │  AWS S3  │
    └────┬─────┘
         │
         ▼
    ┌──────────────┐
    │ CloudFront   │
    │     CDN      │
    └──────────────┘
```

## 📦 Estrutura do Projeto

```
src/main/java/com/arremateai/media/
├── MediaApplication.java
├── controller/
│   └── UploadController.java         # Endpoints de upload
├── service/
│   └── StorageService.java           # Lógica de armazenamento
├── config/
│   └── WebConfig.java                # Configuração CORS e multipart
├── exception/
│   ├── BusinessException.java
│   └── GlobalExceptionHandler.java
└── dto/
    ├── UploadResponse.java
    └── FileMetadata.java
```

## 🚀 Endpoints Principais

### Upload de Imagens

#### POST `/api/upload/imagem`
Upload de imagem de imóvel.

**Headers:**
```
Content-Type: multipart/form-data
Authorization: Bearer {token}
```

**Request (Form Data):**
```
imagem: [image file]
contexto: IMOVEL
referenciaId: imovel-uuid-123
ordem: 1
isPrincipal: true
```

**Response 200:**
```json
{
  "id": "img-uuid",
  "fileName": "imovel_abc123_1.jpg",
  "originalName": "foto_sala.jpg",
  "contentType": "image/jpeg",
  "size": 2456789,
  "url": "https://d123abc.cloudfront.net/imoveis/abc123/imovel_abc123_1.jpg",
  "thumbnailUrl": "https://d123abc.cloudfront.net/imoveis/abc123/thumb_imovel_abc123_1.jpg",
  "width": 1920,
  "height": 1080,
  "ordem": 1,
  "isPrincipal": true,
  "uploadedAt": "2026-03-27T11:00:00Z"
}
```

#### POST `/api/upload/avatar`
Upload de avatar de usuário.

**Request (Form Data):**
```
avatar: [image file]
```

**Response 200:**
```json
{
  "id": "avatar-uuid",
  "fileName": "avatar_550e8400.jpg",
  "url": "https://d123abc.cloudfront.net/avatars/avatar_550e8400.jpg",
  "thumbnailUrl": "https://d123abc.cloudfront.net/avatars/thumb_avatar_550e8400.jpg",
  "uploadedAt": "2026-03-27T11:00:00Z"
}
```

#### POST `/api/upload/documento`
Upload de documento de vendedor (PDF, JPG, PNG).

**Request (Form Data):**
```
documento: [file]
tipoDocumento: CARTAO_CNPJ
vendedorId: vendedor-uuid
```

**Response 200:**
```json
{
  "id": "doc-uuid",
  "fileName": "doc_vendedor_abc123_cnpj.pdf",
  "contentType": "application/pdf",
  "size": 345678,
  "url": "https://storage.arremateai.com/documentos/vendedor-abc123/doc_vendedor_abc123_cnpj.pdf",
  "uploadedAt": "2026-03-27T11:00:00Z"
}
```

### Upload de Vídeos

#### POST `/api/upload/video`
Upload de vídeo de imóvel.

**Headers:**
```
Content-Type: multipart/form-data
Authorization: Bearer {token}
```

**Request (Form Data):**
```
video: [video file]
contexto: IMOVEL
referenciaId: imovel-uuid-123
titulo: Tour Virtual 3D
```

**Response 200:**
```json
{
  "id": "video-uuid",
  "fileName": "video_abc123_tour.mp4",
  "originalName": "tour_virtual.mp4",
  "contentType": "video/mp4",
  "size": 45678900,
  "url": "https://d123abc.cloudfront.net/videos/abc123/video_abc123_tour.mp4",
  "thumbnailUrl": "https://d123abc.cloudfront.net/videos/abc123/thumb_video_abc123.jpg",
  "duration": 180,
  "resolution": "1920x1080",
  "uploadedAt": "2026-03-27T11:00:00Z",
  "status": "PROCESSING"
}
```

### Consulta de Arquivos

#### GET `/api/upload/files/{fileId}`
Obter metadados de um arquivo.

**Response 200:**
```json
{
  "id": "img-uuid",
  "fileName": "imovel_abc123_1.jpg",
  "contentType": "image/jpeg",
  "size": 2456789,
  "url": "https://d123abc.cloudfront.net/imoveis/abc123/imovel_abc123_1.jpg",
  "uploadedAt": "2026-03-27T11:00:00Z"
}
```

#### DELETE `/api/upload/{fileId}`
Deletar arquivo (soft delete).

**Headers:**
```
Authorization: Bearer {token}
```

**Response 204** (No Content)

### Processamento

#### GET `/api/upload/video/{videoId}/status`
Verificar status de processamento de vídeo.

**Response 200:**
```json
{
  "videoId": "video-uuid",
  "status": "COMPLETED",
  "progress": 100,
  "formats": [
    {
      "quality": "1080p",
      "url": "https://d123abc.cloudfront.net/videos/abc123/video_1080p.mp4"
    },
    {
      "quality": "720p",
      "url": "https://d123abc.cloudfront.net/videos/abc123/video_720p.mp4"
    },
    {
      "quality": "480p",
      "url": "https://d123abc.cloudfront.net/videos/abc123/video_480p.mp4"
    }
  ],
  "completedAt": "2026-03-27T11:05:00Z"
}
```

## 📋 Validações de Upload

### Imagens

| Validação | Limite |
|-----------|--------|
| **Tamanho máximo** | 10 MB |
| **Formatos aceitos** | JPEG, PNG, WEBP |
| **Dimensões mínimas** | 800x600 px |
| **Dimensões máximas** | 8000x6000 px |
| **Aspect ratio** | Qualquer |

### Vídeos

| Validação | Limite |
|-----------|--------|
| **Tamanho máximo** | 100 MB |
| **Formatos aceitos** | MP4, AVI, MOV |
| **Duração máxima** | 10 minutos |
| **Resolução máxima** | 1920x1080 (Full HD) |
| **Codec aceito** | H.264 |

### Documentos

| Validação | Limite |
|-----------|--------|
| **Tamanho máximo** | 5 MB |
| **Formatos aceitos** | PDF, JPG, PNG |
| **Páginas máximas** | 50 (PDF) |

## 🎨 Processamento de Imagens

### Thumbnails
- **Dimensões**: 300x200 px (mantém aspect ratio)
- **Qualidade**: 80%
- **Formato**: JPEG
- **Compression**: Optimized

### Redimensionamento
Imagens maiores que 2000px são automaticamente redimensionadas:
- **Large**: 2000px (lado maior)
- **Medium**: 1200px (lado maior)
- **Small**: 600px (lado maior)
- **Thumbnail**: 300px (lado maior)

```java
public String processarImagem(MultipartFile file) {
    BufferedImage original = ImageIO.read(file.getInputStream());
    
    // Resize mantendo aspect ratio
    BufferedImage resized = Thumbnailator.createThumbnail(
        original, 1200, 1200
    );
    
    // Compressão otimizada
    ImageIO.write(resized, "jpg", outputStream);
}
```

## 🎬 Processamento de Vídeos

### Thumbnails de Vídeo
- Captura do frame aos 5 segundos
- Dimensões: 640x360 px
- Formato: JPEG

### Múltiplas Resoluções
Vídeos são processados em 3 resoluções:
- **1080p** (Full HD) - Original
- **720p** (HD) - Compressão média
- **480p** (SD) - Compressão alta

### Compressão
- **Codec**: H.264
- **Bitrate 1080p**: 8 Mbps
- **Bitrate 720p**: 5 Mbps
- **Bitrate 480p**: 2.5 Mbps

## ⚙️ Variáveis de Ambiente

```bash
# Server
SERVER_PORT=8085

# AWS S3
AWS_REGION=us-east-1
AWS_ACCESS_KEY_ID=sua_access_key
AWS_SECRET_ACCESS_KEY=sua_secret_key
AWS_S3_BUCKET_IMAGES=arremateai-images
AWS_S3_BUCKET_VIDEOS=arremateai-videos
AWS_S3_BUCKET_DOCUMENTS=arremateai-documents

# CloudFront CDN
AWS_CLOUDFRONT_DOMAIN=d123abc.cloudfront.net
CDN_ENABLED=true

# Upload Limits
MAX_FILE_SIZE=104857600
MAX_REQUEST_SIZE=104857600
MAX_IMAGE_SIZE=10485760
MAX_VIDEO_SIZE=104857600
MAX_DOCUMENT_SIZE=5242880

# Image Processing
IMAGE_MAX_WIDTH=2000
IMAGE_MAX_HEIGHT=2000
IMAGE_QUALITY=0.8
THUMBNAIL_WIDTH=300
THUMBNAIL_HEIGHT=200

# Video Processing
VIDEO_MAX_DURATION=600
VIDEO_MAX_RESOLUTION_WIDTH=1920
VIDEO_MAX_RESOLUTION_HEIGHT=1080
ENABLE_MULTI_RESOLUTION=true

# Storage
STORAGE_TYPE=S3
LOCAL_STORAGE_PATH=./uploads
STORAGE_BASE_URL=https://storage.arremateai.com

# Allowed Contexts
ALLOWED_CONTEXTS=IMOVEL,PERFIL,DOCUMENTO,LEILAO
```

## 🏃 Como Executar

```bash
# Clone o repositório
git clone https://github.com/Quintanilha09/arremateai-media.git
cd arremateai-media

# Configure AWS credentials
export AWS_ACCESS_KEY_ID=sua_access_key
export AWS_SECRET_ACCESS_KEY=sua_secret_key

# Execute a aplicação
./mvnw spring-boot:run
```

### Docker

```bash
docker build -t arremateai-media:latest .
docker run -d \
  --name media-service \
  -p 8085:8085 \
  -e AWS_ACCESS_KEY_ID=sua_access_key \
  -e AWS_SECRET_ACCESS_KEY=sua_secret_key \
  -e AWS_S3_BUCKET_IMAGES=arremateai-images \
  arremateai-media:latest
```

## 📁 Estrutura de Armazenamento S3

```
arremateai-images/
├── imoveis/
│   ├── {imovelId}/
│   │   ├── img_001.jpg
│   │   ├── thumb_img_001.jpg
│   │   └── img_002.jpg
│   └── ...
├── avatars/
│   ├── avatar_{userId}.jpg
│   ├── thumb_avatar_{userId}.jpg
│   └── ...
└── leiloes/
    └── {leilaoId}/
        └── banner.jpg

arremateai-videos/
└── imoveis/
    └── {imovelId}/
        ├── tour_1080p.mp4
        ├── tour_720p.mp4
        ├── tour_480p.mp4
        └── thumb.jpg

arremateai-documents/
└── vendedores/
    └── {vendedorId}/
        ├── cartao_cnpj.pdf
        ├── contrato_social.pdf
        └── documento_responsavel.jpg
```

## 🔐 Segurança

### Upload Restrictions
- Validação de tipo MIME (Apache Tika)
- Verificação de magic bytes
- Detecção de arquivo malicioso
- Sanitização de nomes de arquivo

### Access Control
- Pre-signed URLs (15 minutos de validade)
- Upload autenticado via JWT
- Bucket policies restritivas
- CORS configurado

```java
@PreAuthorize("hasRole('USER')")
public UploadResponse uploadImagem(MultipartFile file, String contexto) {
    // Validação de segurança
    if (!isValidMimeType(file)) {
        throw new SecurityException("Tipo de arquivo não permitido");
    }
    // ...
}
```

## 🧪 Testes

```bash
# Unit tests
./mvnw test

# Integration tests (requer AWS credentials de teste)
./mvnw verify

# Coverage
./mvnw jacoco:report
```

## 📊 Monitoramento

### Metrics

```bash
# Total de uploads
curl http://localhost:8085/actuator/metrics/uploads.total

# Tamanho total armazenado
curl http://localhost:8085/actuator/metrics/storage.size.bytes

# Tempo médio de upload
curl http://localhost:8085/actuator/metrics/uploads.duration
```

### Health Check

```bash
curl http://localhost:8085/actuator/health
```

**Response:**
```json
{
  "status": "UP",
  "components": {
    "s3": {
      "status": "UP",
      "details": {
        "bucket": "arremateai-images",
        "region": "us-east-1"
      }
    },
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 500829990912,
        "free": 302837510144
      }
    }
  }
}
```

## 🔧 Troubleshooting

### Upload falha com erro 413 (Payload Too Large)
Aumente os limites em `application.properties`:
```properties
spring.servlet.multipart.max-file-size=100MB
spring.servlet.multipart.max-request-size=100MB
```

### Imagens não aparecem após upload
- Verifique se CloudFront está habilitado
- Confirme políticas de bucket S3 (public read)
- Valide URL retornada no response

### Erro ao conectar no S3
- Verifique AWS credentials
- Confirme region configurada
- Valide permissões IAM

## 📄 Licença

Proprietary - © 2026 ArremateAI
