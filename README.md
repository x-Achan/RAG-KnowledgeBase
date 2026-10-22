# RAG Knowledge Base

基于 Spring Boot、Spring AI 和 PostgreSQL/pgvector 构建的企业知识库项目。项目计划实现文档上传、解析切分、向量化、混合检索、RAG 问答和知识库权限控制。

> 项目目前处于开发早期阶段，已完成基础工程、数据库设计、核心实体和部分基础配置，业务接口仍在持续开发中。

## 已有功能

- PostgreSQL + pgvector 数据库结构
- 知识库、文档、文档分块和索引任务实体
- MinIO 客户端配置
- Redis 序列化配置
- 文档索引异步线程池
- 索引任务重试基础配置
- PDF、Word 和 Markdown 文档解析依赖
- Sa-Token 权限认证依赖和用户上下文
- RAG 检索效果评估表及示例数据
- 用于开发测试的示例文档

## 计划功能

- [ ] 知识库管理接口
- [ ] 文档上传与 MinIO 存储
- [ ] PDF、DOCX、Markdown 和 TXT 内容解析
- [ ] 文档切分与 Token 统计
- [ ] Embedding 生成与向量入库
- [ ] 向量检索和全文检索
- [ ] Reranker 重排序
- [ ] 基于检索上下文的问答
- [ ] 用户、部门及知识库权限控制
- [ ] 检索质量评估
- [ ] Prometheus 监控指标

## 技术栈

| 技术 | 用途 |
| --- | --- |
| Java 21 | 开发语言 |
| Spring Boot 3.5.11 | 应用框架 |
| Spring AI 1.1.2 | Chat Model 与 Embedding Model 接入 |
| Spring Data JPA | 数据访问与实体映射 |
| PostgreSQL + pgvector | 业务数据与向量存储 |
| Redis | 缓存及登录状态持久化 |
| MinIO | 原始文档对象存储 |
| Sa-Token | 登录认证与权限控制 |
| PDFBox / Apache POI / Flexmark | PDF、DOCX 和 Markdown 解析 |
| Maven | 项目构建与依赖管理 |

## 环境要求

- JDK 21
- Maven 3.9+
- PostgreSQL 15+，并安装 pgvector 扩展
- Redis 6+
- MinIO
- OpenAI 兼容的 Chat 和 Embedding API

## 快速开始

### 1. 克隆项目

```bash
git clone https://github.com/x-Achan/RAG-Knowledge-Database.git
cd RAG-Knowledge-Database
```

### 2. 创建数据库

先在 PostgreSQL 中创建数据库：

```sql
CREATE DATABASE ragkb;
```

然后连接到 `ragkb`，依次执行：

```text
src/main/resources/db/schema.sql
src/main/resources/db/data.sql
```

`schema.sql` 会执行 `CREATE EXTENSION IF NOT EXISTS vector`，因此当前数据库用户需要具备创建扩展的权限。

### 3. 创建本地配置

项目不会提交包含本地凭据的 `src/main/resources/application.yaml`。请在该路径自行创建文件，并按实际环境修改：

```yaml
spring:
  application:
    name: ccy-rag-knowledge-base

  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/ragkb}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:}
    driver-class-name: org.postgresql.Driver

  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true

  ai:
    openai:
      base-url: ${OPENAI_BASE_URL:https://api.openai.com}
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: ${OPENAI_CHAT_MODEL:gpt-4o-mini}
          temperature: 0.2
          max-tokens: 2000

minio:
  endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
  access-key: ${MINIO_ACCESS_KEY:minioadmin}
  secret-key: ${MINIO_SECRET_KEY:minioadmin}
  bucket: ${MINIO_BUCKET:rag-documents}
```

生产环境中请通过环境变量或密钥管理服务提供密码和 API Key，不要把真实凭据提交到 Git。

### 4. 启动项目

```bash
mvn spring-boot:run
```

也可以在 IntelliJ IDEA 中运行：

```text
src/main/java/com/ccy/ragkb/RagKbApplication.java
```

## 项目结构

```text
src/main/java/com/ccy/ragkb
├── config/          # Redis、MinIO 和异步线程池配置
├── entity/          # 知识库、文档、分块和索引任务实体
├── security/        # 当前用户上下文
└── RagKbApplication.java

src/main/resources
├── db/
│   ├── schema.sql   # 数据库结构
│   └── data.sql     # 开发示例数据
└── test-docs/       # RAG 开发测试文档
```

## 数据库设计

当前数据库包含以下主要数据表：

- `kb_knowledge_base`：知识库信息
- `kb_permission`：用户或部门的知识库权限
- `kb_document`：上传文档及索引状态
- `kb_doc_chunk`：文档分块、文本和向量
- `kb_index_task`：异步索引任务
- `kb_chat_session`：问答会话
- `kb_chat_message`：问答消息
- `kb_answer_feedback`：回答反馈
- `kb_eval_dataset`：RAG 评估数据集
- `kb_eval_result`：RAG 评估结果

## 配置安全

以下内容不应提交到 GitHub：

- `application.yaml` 中的数据库密码
- OpenAI 或其他模型服务的 API Key
- MinIO Access Key 和 Secret Key
- Redis 密码
- 私钥、证书及生产环境配置

如果凭据曾经被提交，即使之后删除文件，凭据仍可能存在于 Git 历史中。此时应立即撤销并重新生成相关凭据。

## License

当前项目尚未添加开源许可证。正式发布前，请根据使用需求选择并添加许可证，例如 MIT、Apache License 2.0 或 GPLv3。

