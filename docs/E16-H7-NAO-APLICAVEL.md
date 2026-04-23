# E16-H7 — Não aplicável ao arremateai-media

## Contexto

A história **E16-H7** ("Flyway migrations nos serviços pendentes") requer:
1. Adicionar `flyway-core` ao `pom.xml`.
2. Criar `V1__init.sql` em `src/main/resources/db/migration/`.
3. Mudar `spring.jpa.hibernate.ddl-auto` para `validate`.

## Por que não se aplica a este serviço

O microsserviço `arremateai-media` **não possui camada de persistência relacional**:

- **`pom.xml`**: não há `spring-boot-starter-data-jpa`, `postgresql` nem nenhum driver JDBC.
- **`application.yml`**: não há bloco `spring.datasource` nem `spring.jpa`.
- **Entidades JPA**: zero arquivos anotados com `@Entity` em `src/main/java`.
- **Responsabilidade**: armazenamento de arquivos de mídia (upload/download), totalmente baseado em sistema de arquivos local (`./uploads`), sem estado relacional a versionar.

Portanto não há schema para gerenciar via Flyway e não faz sentido adicionar a dependência.

## Evidência

| Verificação | Resultado |
| --- | --- |
| Busca por `@Entity` em `src/main/java` | 0 matches |
| `spring-boot-starter-data-jpa` em `pom.xml` | ausente |
| Bloco `spring.datasource` em `application.yml` | ausente |

## Conclusão

Este serviço está marcado como **N/A** no relatório consolidado da E16-H7.
Caso futuramente seja introduzida persistência relacional, a história correspondente deverá incluir a adição do Flyway junto com o JPA.
