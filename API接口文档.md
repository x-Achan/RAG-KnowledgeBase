# RAG Knowledge Base API 接口文档

本文档根据当前 Spring Boot 源码整理，覆盖认证、知识库、文档、RAG 查询、聊天、反馈、效果评估和 Token 统计接口。

## 1. 基本约定

### 1.1 服务地址

默认本地地址：

```text
http://localhost:8080
```

业务接口统一使用 `/api/v1` 前缀。

### 1.2 数据格式

- 普通请求与响应：`application/json`
- 文件上传：`multipart/form-data`
- 文件下载：`application/octet-stream`
- 流式问答：`text/event-stream`
- 时间字段：ISO 8601，例如 `2026-11-02T10:30:00`

### 1.3 统一响应结构

除文件下载、同步聊天和 SSE 流式聊天外，接口通常返回：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | integer | 业务状态码，成功为 `200` |
| `message` | string | 状态说明 |
| `data` | any | 响应数据，可能为 `null` |

通用错误示例：

```json
{
  "code": 403,
  "message": "无权访问该知识库",
  "data": null
}
```

常见状态码：

| HTTP 状态码 | 场景 |
| --- | --- |
| `200` | 请求成功 |
| `400` | 请求参数或业务数据不合法 |
| `401` | 未登录或登录凭证无效 |
| `403` | 没有知识库读写权限 |
| `404` | 知识库、文档或评估数据不存在 |
| `500` | 系统内部异常 |

> 登录失败时，当前实现返回的 HTTP 状态可能仍为 `200`，应同时检查响应体中的 `code`。

## 2. 认证与权限

除 `/api/v1/auth/**` 外，所有 `/api/**` 接口均需要 Sa-Token 登录凭证。

请求头格式：

```http
Authorization: Bearer <token>
```

知识库权限分为：

| 权限 | 能力 |
| --- | --- |
| `READ` | 查看知识库、查询文档、下载文档、执行问答 |
| `WRITE` | 包含读权限，并可上传、替换、删除文档和重建索引 |
| `ADMIN` | 知识库管理权限；系统管理员可以访问全部知识库 |

公开知识库允许普通登录用户读取；文档管理操作仍需 `WRITE` 或 `ADMIN` 权限。

## 3. 接口总览

### 3.1 认证

| 方法 | 路径 | 说明 | 认证 |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/login` | 登录并获取 Token | 否 |
| POST | `/api/v1/auth/logout` | 退出当前登录 | 建议携带 Token |

### 3.2 知识库与文档

| 方法 | 路径 | 说明 | 最低权限 |
| --- | --- | --- | --- |
| GET | `/api/v1/kb` | 查询当前用户可访问的知识库 | 登录 |
| POST | `/api/v1/kb` | 创建知识库 | 登录 |
| POST | `/api/v1/kb/{kbId}/documents` | 上传文档并提交索引任务 | WRITE |
| GET | `/api/v1/kb/{kbId}/documents` | 查询知识库文档列表 | READ |
| GET | `/api/v1/kb/{kbId}/documents/{docId}/status` | 查询索引状态 | READ |
| GET | `/api/v1/kb/{kbId}/documents/{docId}/download` | 下载原始文档 | READ |
| DELETE | `/api/v1/kb/{kbId}/documents/{docId}` | 删除文档 | WRITE |
| POST | `/api/v1/kb/{kbId}/documents/{docId}/reindex` | 重建文档索引 | WRITE |
| PUT | `/api/v1/kb/{kbId}/documents/{docId}/content` | 替换文档内容并创建新版本 | WRITE |
| POST | `/api/v1/kb/{kbId}/documents/{docId}/reindex-force` | 强制重建索引 | WRITE |

### 3.3 RAG 查询与聊天

| 方法 | 路径 | 说明 | 认证 |
| --- | --- | --- | --- |
| POST | `/api/v1/rag/query` | 基础向量检索问答 | 是 |
| POST | `/api/v1/rag/query2` | 混合检索问答 | 是 |
| POST | `/api/v1/rag/query3` | 增强检索问答 | 是 |
| POST | `/api/v1/rag/query4` | 完整 RAG 管道问答 | 是 |
| GET | `/api/v1/chat/stream` | SSE 流式多轮问答 | 是 |
| POST | `/api/v1/chat` | 同步多轮问答 | 是 |
| GET | `/api/v1/chat/sessions` | 查询当前用户会话列表 | 是 |
| GET | `/api/v1/chat/sessions/{sessionId}/messages` | 查询会话消息 | 是 |

### 3.4 反馈、评估与统计

| 方法 | 路径 | 说明 | 认证 |
| --- | --- | --- | --- |
| POST | `/api/v1/feedback/{messageId}` | 提交点赞或点踩 | 是 |
| POST | `/api/v1/eval/{kbId}/run` | 运行 RAG 效果评估 | 是 |
| GET | `/api/v1/eval/{kbId}/history` | 查询历史评估报告 | 是 |
| GET | `/api/v1/eval/{kbId}/dataset` | 查询评估数据集 | 是 |
| POST | `/api/v1/eval/{kbId}/dataset` | 新增评估问题 | 是 |
| PUT | `/api/v1/eval/{kbId}/dataset/{id}` | 更新评估问题 | 是 |
| DELETE | `/api/v1/eval/{kbId}/dataset/{id}` | 删除评估问题 | 是 |
| GET | `/api/v1/eval/{kbId}/chunks` | 查询用于标注的 Chunk 摘要 | 是 |
| GET | `/api/v1/stats/tokens` | 查询当前用户 Token 消耗与成本 | 是 |

## 4. 认证接口

### 4.1 登录

```http
POST /api/v1/auth/login
Content-Type: application/json
```

请求体：

```json
{
  "username": "admin",
  "password": "demo123"
}
```

当前项目内置演示用户：

| 用户名 | 部门 | 角色 |
| --- | --- | --- |
| `hr001` | HR | MEMBER |
| `tech001` | TECH | MEMBER |
| `admin` | ALL | ADMIN |

以上演示用户的密码均为 `demo123`，仅用于本地演示，生产环境应替换为 SSO、LDAP 或正式用户系统。

成功响应：

```json
{
  "code": 200,
  "message": "success",
  "data": "登录后返回的 token"
}
```

失败响应：

```json
{
  "code": 401,
  "message": "用户名或密码错误",
  "data": null
}
```

### 4.2 退出登录

```http
POST /api/v1/auth/logout
Authorization: Bearer <token>
```

响应 `data` 为 `null`。

## 5. 知识库接口

### 5.1 查询可访问知识库

```http
GET /api/v1/kb
Authorization: Bearer <token>
```

响应数据为 `KnowledgeBaseVO[]`，其中 `permission` 表示当前用户的最高权限。

### 5.2 创建知识库

```http
POST /api/v1/kb
Authorization: Bearer <token>
Content-Type: application/json
```

请求体：

```json
{
  "name": "产品知识库",
  "description": "产品手册、功能说明和常见问题",
  "departmentId": "PROD",
  "isPublic": true
}
```

创建者自动获得该知识库的 `ADMIN` 权限。响应数据为 `KnowledgeBase`。

## 6. 文档管理接口

### 6.1 上传文档

```http
POST /api/v1/kb/{kbId}/documents
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

表单参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `file` | file | 是 | 支持 PDF、DOCX、Markdown 和 TXT，默认最大 50 MB |

成功响应数据：

```json
{
  "docId": 10,
  "fileName": "product-faq.pdf",
  "status": "PENDING",
  "message": "文档已上传，正在后台索引，请通过 /status 接口查询进度"
}
```

### 6.2 查询文档列表

```http
GET /api/v1/kb/{kbId}/documents
Authorization: Bearer <token>
```

响应数据为 `KbDocument[]`。

### 6.3 查询索引状态

```http
GET /api/v1/kb/{kbId}/documents/{docId}/status
Authorization: Bearer <token>
```

响应数据示例：

```json
{
  "docId": 10,
  "fileName": "product-faq.pdf",
  "status": "DONE",
  "errorMsg": null,
  "chunkCount": 36,
  "tokenCount": 8200,
  "indexedAt": "2026-11-02T10:30:00",
  "retryCount": 0
}
```

索引状态为 `PENDING`、`PROCESSING`、`DONE` 或 `FAILED`。

### 6.4 下载文档

```http
GET /api/v1/kb/{kbId}/documents/{docId}/download
Authorization: Bearer <token>
```

成功时返回二进制文件，文件名位于 `Content-Disposition` 响应头中。

### 6.5 删除文档

```http
DELETE /api/v1/kb/{kbId}/documents/{docId}
Authorization: Bearer <token>
```

该操作会软删除文档记录，并删除向量分块和 MinIO 文件。成功响应的 `data` 为 `null`。

### 6.6 重建索引

```http
POST /api/v1/kb/{kbId}/documents/{docId}/reindex
Authorization: Bearer <token>
```

成功响应数据：

```text
重建索引任务已提交，请通过 /status 接口查询进度
```

### 6.7 替换文档内容

```http
PUT /api/v1/kb/{kbId}/documents/{docId}/content
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

表单参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `file` | file | 是 | 新版本文件，文档 ID 保持不变 |

成功后返回更新后的 `KbDocument`，并异步提交新版本索引任务。

### 6.8 强制重建索引

```http
POST /api/v1/kb/{kbId}/documents/{docId}/reindex-force
Authorization: Bearer <token>
```

适用于文件内容未变，但解析、分块或索引策略已调整的场景。成功响应的 `data` 为 `null`。

## 7. RAG 查询接口

四个查询接口使用相同请求体：

```json
{
  "question": "数据库设计应该包含哪些字段？",
  "kbIds": [1, 2]
}
```

### 7.1 基础查询

```http
POST /api/v1/rag/query
```

返回 `ApiResponse<string>`。

### 7.2 混合检索查询

```http
POST /api/v1/rag/query2
```

返回 `ApiResponse<string>`。

### 7.3 增强检索查询

```http
POST /api/v1/rag/query3
```

返回 `ApiResponse<string>`。

### 7.4 完整 RAG 管道查询

```http
POST /api/v1/rag/query4
```

返回 `ApiResponse<RagResponse>`，包含回答、引用来源、耗时和未命中标志：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "answer": "所有业务表应包含主键、创建时间、更新时间和软删除字段。",
    "sources": [
      {
        "chunkId": 101,
        "docId": 12,
        "docName": "tech-spec.txt",
        "pageNum": 1,
        "sectionTitle": "数据库规范",
        "excerpt": "所有业务表必须包含以下字段……",
        "score": 0.92
      }
    ],
    "latencyMs": 1280,
    "notFound": false
  }
}
```

## 8. 聊天接口

### 8.1 SSE 流式问答

```http
GET /api/v1/chat/stream?question=问题&kbIds=1&kbIds=2&sessionId=可选会话ID
Accept: text/event-stream
Authorization: Bearer <token>
```

查询参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `question` | string | 是 | 用户问题 |
| `kbIds` | long[] | 是 | 知识库 ID，可重复传参，例如 `kbIds=1&kbIds=2` |
| `sessionId` | string | 否 | 为空时创建新会话 |

连接超时时间为 60 秒。服务端事件：

| 事件名 | 数据 | 说明 |
| --- | --- | --- |
| `status` | JSON | 当前阶段，如 `RETRIEVING`、`GENERATING` |
| `token` | string | 增量回答文本 |
| `done` | JSON | 完成事件，包含 `sources` 和 `latencyMs` |
| `error` | JSON | 生成过程异常信息 |

`status` 示例：

```text
event: status
data: {"type":"RETRIEVING","message":"正在检索知识库..."}
```

`done` 示例：

```text
event: done
data: {"sources":[],"latencyMs":1280}
```

### 8.2 同步问答

```http
POST /api/v1/chat
Authorization: Bearer <token>
Content-Type: application/json
```

请求体：

```json
{
  "question": "年假是怎么规定的？",
  "kbIds": [1],
  "sessionId": null
}
```

该接口直接返回 `RagResponse`，没有 `ApiResponse` 外层包装。

### 8.3 查询会话列表

```http
GET /api/v1/chat/sessions
Authorization: Bearer <token>
```

响应数据为当前用户的 `ChatSession[]`，按最近活跃时间倒序排列。

### 8.4 查询会话消息

```http
GET /api/v1/chat/sessions/{sessionId}/messages
Authorization: Bearer <token>
```

响应数据为 `ChatMessage[]`，按创建时间升序排列。

## 9. 用户反馈接口

```http
POST /api/v1/feedback/{messageId}?feedback=1&comment=回答准确
Authorization: Bearer <token>
```

| 参数 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `messageId` | path | long | 是 | 回答消息 ID |
| `feedback` | query | integer | 是 | `1` 表示点赞，`-1` 表示点踩 |
| `comment` | query | string | 否 | 反馈说明 |

同一用户对同一消息重复提交时会覆盖原反馈。

## 10. RAG 效果评估接口

### 10.1 运行评估

```http
POST /api/v1/eval/{kbId}/run?version=v1_hybrid_reranker
Authorization: Bearer <token>
```

| 参数 | 位置 | 类型 | 必填 | 默认值 |
| --- | --- | --- | --- | --- |
| `kbId` | path | long | 是 | - |
| `version` | query | string | 否 | `latest` |

返回 `EvalReport`，包含问题数、命中数、Hit Rate、MRR、平均忠实性和评估时间。

### 10.2 查询历史评估报告

```http
GET /api/v1/eval/{kbId}/history
Authorization: Bearer <token>
```

响应数据为 `EvalReport[]`。

### 10.3 查询评估数据集

```http
GET /api/v1/eval/{kbId}/dataset
Authorization: Bearer <token>
```

响应数据为 `EvalDataset[]`。

### 10.4 新增评估问题

```http
POST /api/v1/eval/{kbId}/dataset
Authorization: Bearer <token>
Content-Type: application/json
```

请求体：

```json
{
  "question": "新员工入职流程是什么？",
  "expectedAnswer": "完成材料提交、合同签署和入职培训。",
  "expectedChunkIds": [101, 102]
}
```

### 10.5 更新评估问题

```http
PUT /api/v1/eval/{kbId}/dataset/{id}
Authorization: Bearer <token>
Content-Type: application/json
```

请求体字段与新增接口相同；非 `null` 字段会覆盖原值。

### 10.6 删除评估问题

```http
DELETE /api/v1/eval/{kbId}/dataset/{id}
Authorization: Bearer <token>
```

成功响应的 `data` 为 `null`。

### 10.7 查询 Chunk 摘要

```http
GET /api/v1/eval/{kbId}/chunks
Authorization: Bearer <token>
```

响应数据示例：

```json
[
  {
    "id": 101,
    "docId": 12,
    "chunkIndex": 0,
    "content": "分块内容的前 200 个字符……",
    "tokenCount": 138
  }
]
```

## 11. Token 统计接口

```http
GET /api/v1/stats/tokens
Authorization: Bearer <token>
```

响应数据：

```json
{
  "embeddingTokens": 3200,
  "contextTokens": 8600,
  "generationTokens": 4200,
  "totalTokens": 16000,
  "estimatedCostCny": 0.0175
}
```

成本为基于当前代码中 DashScope 单价计算的估算值，仅供参考。

## 12. 数据模型

### 12.1 KnowledgeBaseVO

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | long | 知识库 ID |
| `name` | string | 名称 |
| `description` | string | 描述 |
| `departmentId` | string | 所属部门 |
| `isPublic` | boolean | 是否公开 |
| `createdBy` | long | 创建者用户 ID |
| `createdAt` | datetime | 创建时间 |
| `permission` | string | 当前用户权限：`READ`、`WRITE` 或 `ADMIN` |

### 12.2 KnowledgeBase

在 `KnowledgeBaseVO` 的基础上还可能包含 `updatedAt` 和 `isDeleted`，但不包含 `permission`。

### 12.3 KbDocument

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | long | 文档 ID |
| `kbId` | long | 所属知识库 ID |
| `fileName` | string | 原始文件名 |
| `fileType` | string | `PDF`、`DOCX`、`MD` 或 `TXT` |
| `fileSize` | long | 文件大小，单位为字节 |
| `minioPath` | string | MinIO 对象路径 |
| `status` | string | `PENDING`、`PROCESSING`、`DONE` 或 `FAILED` |
| `errorMsg` | string/null | 索引失败原因 |
| `chunkCount` | integer | 分块数量 |
| `tokenCount` | integer | Token 数量 |
| `version` | integer | 文档版本 |
| `uploadedBy` | long | 上传者用户 ID |
| `uploadedAt` | datetime | 上传时间 |
| `indexedAt` | datetime/null | 索引完成时间 |
| `isDeleted` | boolean | 是否软删除 |

### 12.4 RagResponse

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `answer` | string | 模型回答 |
| `sources` | Source[] | 引用来源 |
| `latencyMs` | integer | 总耗时，单位毫秒 |
| `notFound` | boolean | 是否未检索到可信内容 |

`Source` 字段：`chunkId`、`docId`、`docName`、`pageNum`、`sectionTitle`、`excerpt`、`score`。

### 12.5 ChatSession

字段包括：`id`、`userId`、`kbIds`、`title`、`messageCount`、`createdAt`、`lastActiveAt`、`isDeleted`。

### 12.6 ChatMessage

字段包括：`id`、`sessionId`、`role`、`content`、`sources`、`latencyMs`、`feedback`、`createdAt`。其中 `sources` 当前以 JSON 字符串保存。

### 12.7 EvalDataset

字段包括：`id`、`kbId`、`question`、`expectedAnswer`、`expectedChunkIds`、`createdBy`、`createdAt`。

### 12.8 EvalReport

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `kbId` | long | 知识库 ID |
| `evalVersion` | string | 评估版本标识 |
| `totalQuestions` | long | 总问题数 |
| `hitCount` | long | 检索命中数 |
| `hitRate` | number | 命中率，取值 0～1 |
| `mrr` | number | Mean Reciprocal Rank，取值 0～1 |
| `avgFaithfulness` | number | 平均忠实性，取值 0～1 |
| `evalAt` | datetime | 评估时间 |

## 13. 调用示例

登录：

```bash
curl -X POST "http://localhost:8080/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"demo123"}'
```

查询知识库：

```bash
curl "http://localhost:8080/api/v1/kb" \
  -H "Authorization: Bearer <token>"
```

上传文档：

```bash
curl -X POST "http://localhost:8080/api/v1/kb/1/documents" \
  -H "Authorization: Bearer <token>" \
  -F "file=@./example.pdf"
```

执行完整 RAG 查询：

```bash
curl -X POST "http://localhost:8080/api/v1/rag/query4" \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"question":"数据库设计应该包含哪些字段？","kbIds":[1]}'
```

## 14. 当前实现注意事项

- 评估数据集管理和评估执行接口的注释称管理员功能，但 Controller 当前未执行显式角色校验。
- 查询会话消息接口当前按 `sessionId` 查询，Controller 未额外校验该会话是否属于当前用户。
- 原生浏览器 `EventSource` 不能自定义 `Authorization` 请求头；前端调用 SSE 时应使用支持请求头的 Fetch 流式方案，或调整认证传递方式。
- 返回 `KbDocument` 时会包含 `minioPath` 等内部字段；对外开放 API 时建议改用专用响应 DTO。
- 请求 DTO 当前缺少 Bean Validation 注解，必填约束主要依赖业务逻辑和数据库约束。
- 文档内容来自当前代码实现；接口调整后应同步更新本文档。
