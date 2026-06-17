# SuperBizAgent

智能 OnCall Agent 是一个面向客服、售后与运维场景的 RAG 智能体项目。系统基于 Spring Boot、DashScope、Milvus 和 Agent 工具调用能力，支持知识库投喂、引用来源展示、标准问答接口和云端部署。

## 核心能力

- 真实大模型问答：接入 DashScope 文本模型。
- RAG 知识库：支持 Markdown/TXT 上传、分片、Embedding 和 Milvus 向量检索。
- 引用来源：回答后展示命中的知识库文件、标题、片段和相似度分数。
- 知识库管理：前端可查看已投喂资料列表。
- 智能体工具：支持内部文档、日志、指标和时间工具调用。
- 多模态扩展：比赛接口支持图片字段，可接入视觉模型处理截图类工单。
- 云端部署：提供 ECS + Docker + Nginx + systemd 部署脚本和说明。

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 后端 | Java 17, Spring Boot 3.2 |
| 智能体 | Spring AI Alibaba Agent Framework |
| 大模型 | DashScope |
| 向量数据库 | Milvus |
| 文档处理 | Markdown/TXT 分片与 Embedding |
| 前端 | HTML, CSS, JavaScript |
| 部署 | Docker Compose, Nginx, systemd |

## 目录结构

```text
SuperBizAgent/
├─ src/main/java/org/example/        # Java 源码
├─ src/main/resources/static/        # 前端页面
├─ src/main/resources/application*.yml
├─ aiops-docs/                       # 内置运维知识文档
├─ contest-materials/                # 参赛知识库与测试集
├─ contest-deliverables/             # 技术文档、截图、验证报告
├─ deploy/                           # 云端部署脚本和配置
├─ vector-database.yml               # 本地 Milvus 环境
├─ start-local.ps1                   # 本地启动脚本
└─ pom.xml
```

## 云端演示入口

当前项目已部署到阿里云 ECS，可通过公网地址访问：

```text
http://121.40.90.107/
```

评审或演示时建议优先使用云端入口，便于直接体验问答、引用来源和知识库管理能力。比赛统一问答接口为：

```text
POST http://121.40.90.107/chat
```

`/chat` 接口需要携带 `Authorization: Bearer <CONTEST_API_TOKEN>`，Token 不写入公开仓库，可在现场演示或提交系统要求时单独提供。

## 本地快速启动

### 1. 设置模型 Key

```powershell
[Environment]::SetEnvironmentVariable("DASHSCOPE_API_KEY", "your-api-key", "User")
```

重新打开终端后进入项目目录。

### 2. 启动本地环境

```powershell
.\start-local.ps1
```

启动成功后访问：

```text
http://127.0.0.1:9900/
```

### 3. 投喂知识库

```powershell
$kbDir = ".\contest-materials\knowledge-base"
Get-ChildItem -Path $kbDir -Filter *.md | ForEach-Object {
  curl.exe -F "file=@$($_.FullName)" http://127.0.0.1:9900/api/upload
}
```

### 4. 验证 RAG 召回

```powershell
$q = [System.Uri]::EscapeDataString("支付订单超时怎么办")
Invoke-RestMethod "http://127.0.0.1:9900/api/rag/search?q=$q&topK=3"
```

## 主要接口

| 接口 | 说明 |
| --- | --- |
| `POST /chat` | 参赛统一问答接口，支持 Token 鉴权 |
| `POST /api/chat` | 前端普通问答接口 |
| `POST /api/chat_stream` | 前端流式问答接口 |
| `POST /api/upload` | 知识库文件上传 |
| `GET /api/rag/search` | RAG 召回调试 |
| `GET /api/knowledge/files` | 知识库文件列表 |
| `GET /milvus/health` | Milvus 健康检查 |

## 云端部署与验证

部署说明见：

- `deploy/README.md`
- `contest-deliverables/08-云部署补充说明.md`
- `contest-deliverables/09-最终提交清单.md`

生产或公网演示时，请通过环境变量配置：

```text
DASHSCOPE_API_KEY=<your-key>
CONTEST_API_TOKEN=<your-token>
```

不要将真实 API Key、接口 Token、服务器私钥或 `.env` 文件提交到仓库。

## 参赛材料

参赛材料位于 `contest-deliverables/`，包括：

- 技术文档
- 接口说明
- 运行说明
- 验证报告
- 演示脚本
- 项目截图
- 云部署补充说明

## License

Apache License 2.0
