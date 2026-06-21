# 智能 OnCall Agent 公网访问与 GitHub 发布说明

本文档用于把当前 AI-agent 项目整理成“别人可以访问、自己可以复现、面试可以讲清楚”的项目材料。

## 1. 当前结论

项目已经具备公网访问能力，当前公网入口为：

```text
http://121.40.90.107/
```

当前 GitHub 远程仓库为：

```text
git@github.com:ddn325601-hub/RAG.git
```

也可以在浏览器访问：

```text
https://github.com/ddn325601-hub/RAG
```

截至本次整理时，公网首页可以正常返回 `HTTP 200`，页面标题为“智能OnCall助手”。

## 2. 项目定位

这个项目可以包装成一个 Java 后端 + AI/RAG 能力 + 公网部署的综合项目：

- 后端服务：Spring Boot 3.2 + Java 17。
- AI 能力：接入 DashScope 大模型，完成智能问答。
- RAG 能力：上传知识库文档，向量化后存入 Milvus，并在问答时召回相关片段。
- 接口能力：提供 `/chat`、`/api/chat`、`/api/upload`、`/api/rag/search`、`/api/knowledge/files` 等接口。
- 部署能力：使用 ECS + Docker Compose + Nginx + systemd，让本地项目变成公网可访问网站。
- 安全能力：公网 `/chat` 接口使用 Token 鉴权，真实 Key 和 Token 通过环境变量配置，不写入 Git 仓库。

## 3. 本地启动流程

### 3.1 进入项目目录

```powershell
cd "C:\Users\DDN\Documents\SuperBizAgent-Contest"
```

### 3.2 配置 DashScope Key

本地启动前需要设置真实的 DashScope API Key：

```powershell
[Environment]::SetEnvironmentVariable("DASHSCOPE_API_KEY", "你的真实DashScope Key", "User")
```

重新打开 PowerShell 后检查：

```powershell
[Environment]::GetEnvironmentVariable("DASHSCOPE_API_KEY", "User")
```

注意：真实 Key 只放在系统环境变量里，不写入 `application.yml`、`.env` 或 README。

### 3.3 启动 Milvus 向量数据库

```powershell
docker compose -f .\vector-database.yml up -d
docker compose -f .\vector-database.yml ps
```

看到 Milvus、MinIO、Etcd 容器正常运行后，再启动 Java 服务。

### 3.4 启动 Java 后端

```powershell
.\start-local.ps1
```

启动成功后访问：

```text
http://127.0.0.1:9900/
```

### 3.5 初始化知识库

```powershell
$kbDir = ".\contest-materials\knowledge-base"
Get-ChildItem -Path $kbDir -Filter *.md | ForEach-Object {
  curl.exe -F "file=@$($_.FullName)" http://127.0.0.1:9900/api/upload
}
```

检查知识库文件：

```powershell
Invoke-RestMethod "http://127.0.0.1:9900/api/knowledge/files"
```

检查 RAG 召回：

```powershell
$q = [System.Uri]::EscapeDataString("支付订单超时怎么排查")
Invoke-RestMethod "http://127.0.0.1:9900/api/rag/search?q=$q&topK=3"
```

## 4. 公网部署流程

公网部署推荐使用云服务器，例如阿里云 ECS、腾讯云 CVM、华为云 ECS 等。

### 4.1 云服务器建议配置

- 系统：Ubuntu 22.04。
- CPU：2 核起步，推荐 4 核。
- 内存：4 GB 起步，推荐 8 GB。
- 磁盘：40 GB 起步。
- 安全组：开放 `22`、`80`，后续做 HTTPS 再开放 `443`。

不建议直接开放：

- `9900`：Java 服务端口，应由 Nginx 反向代理。
- `19530`：Milvus 端口。
- `9000/9001`：MinIO 端口。

### 4.2 服务器拉取代码

```bash
git clone https://github.com/ddn325601-hub/RAG.git /opt/SuperBizAgent
cd /opt/SuperBizAgent
```

### 4.3 安装服务器依赖

```bash
bash deploy/install-server.sh
```

该脚本会安装 Docker、Docker Compose plugin、OpenJDK 17、Maven、Nginx 等依赖。

### 4.4 配置真实环境变量

```bash
cp deploy/super-biz-agent.env.example /etc/super-biz-agent.env
nano /etc/super-biz-agent.env
chmod 600 /etc/super-biz-agent.env
```

示例：

```text
DASHSCOPE_API_KEY=你的真实DashScope Key
CONTEST_API_TOKEN=你自己设置的公网接口Token
KAFU_API_TOKEN=你自己设置的公网接口Token
SPRING_PROFILES_ACTIVE=local
JAVA_OPTS=-Xms512m -Xmx2g
```

### 4.5 启动 Milvus

```bash
docker compose -f deploy/vector-database-prod.yml up -d
docker compose -f deploy/vector-database-prod.yml ps
```

### 4.6 构建并注册 Java 服务

```bash
mvn -DskipTests package
cp deploy/super-biz-agent.service /etc/systemd/system/super-biz-agent.service
systemctl daemon-reload
systemctl enable --now super-biz-agent
systemctl status super-biz-agent
```

查看日志：

```bash
journalctl -u super-biz-agent -f
```

### 4.7 配置 Nginx

```bash
cp deploy/nginx-super-biz-agent.conf /etc/nginx/sites-available/super-biz-agent
ln -sf /etc/nginx/sites-available/super-biz-agent /etc/nginx/sites-enabled/super-biz-agent
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl reload nginx
```

之后访问：

```text
http://服务器公网IP/
```

## 5. 公网验证命令

在本机 PowerShell 执行：

```powershell
.\scripts\check-public-deployment.ps1 -BaseUrl "http://121.40.90.107"
```

这个脚本会检查：

- 首页是否返回 200。
- 知识库列表接口是否可访问。
- RAG 召回接口是否可访问。
- `/chat` 无 Token 是否被拒绝。
- 如果当前环境变量里存在 `CONTEST_API_TOKEN`，会额外测试带 Token 的 `/chat`。

## 6. GitHub 发布注意事项

提交前先查看状态：

```powershell
git status --short --branch
```

敏感信息检查：

```powershell
.\scripts\sanitize-repo-check.ps1
```

只提交适合公开的内容：

```powershell
git add README.md docs scripts deploy src pom.xml .gitignore
git commit -m "Add public deployment guide"
git push
```

不要提交：

- 真实 `DASHSCOPE_API_KEY`。
- 真实 `CONTEST_API_TOKEN`。
- 云服务器私钥。
- `/etc/super-biz-agent.env`。
- `.env` 文件。
- `target/`、`logs/`、`uploads/`、`volumes/` 等运行产物。

## 7. 面试时可以这样讲

项目名称：智能 OnCall Agent 运维问答系统。

项目描述：

基于 Spring Boot 和 DashScope 大模型实现智能问答系统，支持知识库上传、文本切片、Embedding 向量化、Milvus 向量检索和 RAG 增强回答。项目提供标准 REST 接口和 Web 页面，并部署到阿里云 ECS，通过 Nginx 对外提供公网访问。

个人工作：

- 设计并实现知识库上传、文档切片、向量检索和问答接口。
- 接入 DashScope 模型和 Embedding 服务，完成 RAG 问答链路。
- 使用 Docker Compose 部署 Milvus、MinIO、Etcd 等依赖服务。
- 使用 systemd 管理 Java 服务进程，使用 Nginx 进行反向代理。
- 为公网 `/chat` 接口增加 Token 鉴权，避免接口裸奔。
- 编写部署文档、接口验证脚本和敏感信息检查脚本，保证项目可复现、可演示、可交付。

测试方向可以重点讲：

- 接口测试：验证 `/chat`、`/api/upload`、`/api/rag/search`、`/api/knowledge/files`。
- 冒烟测试：验证首页、知识库、RAG 召回、鉴权接口是否正常。
- 异常测试：验证无 Token、错误 Token、空问题、异常问题输入。
- 安全测试：验证真实 Key 不入库、不进 Git，公网接口不直接暴露内部端口。
- 部署验证：验证 Nginx、systemd、Docker Compose、云服务器安全组是否配置正确。

## 8. 和校园论坛项目的区别

校园论坛项目更适合作为“传统 Java Web 后端 + pytest 自动化测试项目”。

智能 OnCall Agent 更适合作为“AI/RAG 后端 + 公网部署 + 接口验证项目”。

如果你的目标是测试实习，简历里可以把两个项目组合起来：

- 校园论坛：突出功能测试、接口测试、登录态测试、数据库校验、Allure 报告。
- 智能 OnCall Agent：突出公网部署验证、接口鉴权测试、AI 接口测试、RAG 结果校验、服务可用性检查。

这样会比只写一个普通本地项目更有层次。
