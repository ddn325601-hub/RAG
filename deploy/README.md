# 云服务器部署说明

适用环境：Ubuntu 22.04，建议 4 vCPU、8 GB RAM、40 GB 以上磁盘。公网演示建议开放 22、80、443 端口，其中 443 可在后续配置 HTTPS 时使用。

## 部署目标

- 公网访问 Web 页面：`http://服务器公网IP/`
- 公网访问比赛接口：`POST http://服务器公网IP/chat`
- Java 服务只监听本机 `127.0.0.1:9900`
- Nginx 对外开放 `80`，反向代理到 Java 服务
- Milvus、MinIO、Etcd 只在服务器内部 Docker 网络和本机端口使用

## 1. 安全组

在 ECS 控制台安全组中放行：

| 端口 | 协议 | 来源 | 用途 |
| --- | --- | --- | --- |
| 22 | TCP | 你的公网 IP 或临时 `0.0.0.0/0` | SSH 登录 |
| 80 | TCP | `0.0.0.0/0` | Web 访问 |
| 443 | TCP | `0.0.0.0/0` | 后续 HTTPS |

不建议公网开放：

- `9900`：Java 服务端口，由 Nginx 代理即可。
- `19530`：Milvus 端口。
- `9000/9001`：MinIO 端口。

## 2. 上传代码

方式 A：使用 Git 仓库。

```bash
git clone <your-repository-url> /opt/SuperBizAgent
cd /opt/SuperBizAgent
```

方式 B：使用部署包。

```bash
mkdir -p /opt/SuperBizAgent
tar -xzf SuperBizAgent-Java-deploy.tar.gz -C /opt/SuperBizAgent
cd /opt/SuperBizAgent
```

## 3. 安装系统依赖

```bash
bash deploy/install-server.sh
```

脚本会安装：

- Docker
- Docker Compose plugin
- OpenJDK 17
- Maven
- Nginx
- 常用工具

## 4. 配置敏感信息

```bash
cp deploy/super-biz-agent.env.example /etc/super-biz-agent.env
nano /etc/super-biz-agent.env
chmod 600 /etc/super-biz-agent.env
```

需要配置：

```text
DASHSCOPE_API_KEY=<your-dashscope-api-key>
CONTEST_API_TOKEN=<your-contest-token>
SPRING_PROFILES_ACTIVE=local
JAVA_OPTS=-Xms512m -Xmx2g
```

注意：不要把 `/etc/super-biz-agent.env` 或真实 Key 提交到 Git。

## 5. 启动 Milvus

```bash
docker compose -f deploy/vector-database-prod.yml up -d
docker compose -f deploy/vector-database-prod.yml ps
```

等待 `milvus-standalone`、`milvus-minio`、`milvus-etcd` 变为 healthy。

## 6. 构建并启动 Java 服务

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

## 7. 配置 Nginx

```bash
cp deploy/nginx-super-biz-agent.conf /etc/nginx/sites-available/super-biz-agent
ln -sf /etc/nginx/sites-available/super-biz-agent /etc/nginx/sites-enabled/super-biz-agent
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl reload nginx
```

访问：

```text
http://服务器公网IP/
```

## 8. 初始化知识库

首次部署后需要上传知识库：

```bash
for f in contest-materials/knowledge-base/*.md; do
  curl -F "file=@$f" http://127.0.0.1:9900/api/upload
done
```

检查：

```bash
curl http://127.0.0.1:9900/api/knowledge/files
curl "http://127.0.0.1:9900/api/rag/search?q=ServiceUnavailable&topK=3"
```

## 9. 对外测试

```bash
curl -X POST "http://服务器公网IP/chat" \
  -H "Authorization: Bearer <your-contest-token>" \
  -H "Content-Type: application/json" \
  -d '{"question":"How should we troubleshoot a payment order timeout?","stream":false}'
```

## 10. 常见问题

### Java 服务启动失败

```bash
journalctl -u super-biz-agent -n 200 --no-pager
```

重点检查：

- `/etc/super-biz-agent.env` 是否配置 `DASHSCOPE_API_KEY`
- Milvus 是否启动并 healthy
- 9900 端口是否被占用

### 外网打不开

检查：

- ECS 安全组是否放行 80。
- Nginx 是否启动。
- `curl http://127.0.0.1:9900/` 是否正常。
- `curl http://127.0.0.1/` 是否正常。
