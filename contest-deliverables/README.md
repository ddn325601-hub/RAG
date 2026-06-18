# 智能 OnCall Agent 参赛材料包

本目录用于提交、答辩和演示时快速说明项目能力，覆盖技术方案、接口、运行、验证、截图和演示脚本。

## 当前部署状态

- 本地运行入口：`http://127.0.0.1:9900/`
- 云服务器公网入口：`http://121.40.90.107/`
- 比赛问答接口：`POST http://121.40.90.107/chat`
- RAG 调试接口：`GET http://121.40.90.107/api/rag/search?q=问题&topK=3`
- 知识库管理接口：`GET http://121.40.90.107/api/knowledge/files`

说明：公网 `/chat` 接口需要 `Authorization: Bearer <CONTEST_API_TOKEN>`。Token 属于演示凭证，不建议写入公开仓库；提交材料时可单独提供给评委或现场演示使用。

## 材料清单

- `01-技术文档.md`：项目背景、架构、智能体能力、RAG 与多模态方案。
- `02-接口说明.md`：比赛接口、知识库上传接口、RAG 调试接口、知识库管理接口。
- `03-运行说明.md`：本地运行、ECS 部署、Docker/Milvus、环境变量和常见问题。
- `04-验证报告.md`：本地与公网验证结果、RAG 命中结果、接口鉴权结果。
- `05-演示脚本.md`：3 到 5 分钟演示话术和操作顺序。
- `06-项目截图说明.md`：截图目录与每张图用途。
- `07-优化建议清单.md`：赛前必做优化和可选加分项。
- `08-云部署补充说明.md`：ECS 部署结构、服务管理、提交注意事项。
- `09-最终提交清单.md`：最终提交范围、排除项、验证命令和提交建议。
- `10-演示视频录制脚本.md`：功能演示视频的录制流程、自动化脚本和验收指标。
- `11-完整视频剪辑脚本.md`：PPT 截图、网页录屏、旁白字幕的完整剪映剪辑台本。
- `subtitles/`：完整演示视频的 SRT、LRC、ASS 字幕文件。
- `OnCallAgent_Function_Demo.mp4`：已生成的 1080p 产品功能演示视频，覆盖首页、知识库、资料投喂、RAG 问答、引用来源和接口鉴权。
- `智能OnCallAgent-答辩演示.pptx`：可编辑答辩演示 PPT。
- `智能OnCallAgent-技术论文.docx`：按研电赛技术论文格式要求整理的技术论文附件。
- `智能OnCallAgent-门型展架.jpg`：80*180cm 门型展架 JPG，展示作品背景、创新点、技术路线、验证结果和同类方法对比。
- `智能OnCallAgent-门型展架-可编辑版.pptx`：门型展架可编辑源文件，可在 PowerPoint 中修改文字、图片、表格后另存为 JPG。
- `screenshots/`：项目运行截图。
- `api-examples/`：可直接执行的接口测试脚本。

## 当前已完成能力

- DashScope 文本模型真实问答。
- DashScope OpenAI 兼容模式视觉模型接入。
- Markdown 知识库上传、分片、Embedding、写入 Milvus。
- 前端展示引用来源。
- 前端知识库管理页面。
- ReactAgent 工具调用：内部文档检索、指标、日志、时间工具。
- 比赛接口鉴权、会话 ID、多轮上下文和图片字段。
- 客服、售后、运维类测试知识库与测试集。
- 阿里云 ECS 公网部署，Nginx 80 端口反向代理。

## 最终提交建议

- GitHub 仓库：`https://github.com/ddn325601-hub/RAG`
- 公网演示地址：`http://121.40.90.107/`
- 参赛材料入口：`contest-deliverables/README.md`
- 最终提交清单：`contest-deliverables/09-最终提交清单.md`
- 答辩演示 PPT：`contest-deliverables/智能OnCallAgent-答辩演示.pptx`
- 技术论文附件：`contest-deliverables/智能OnCallAgent-技术论文.docx`
- 门型展架：`contest-deliverables/智能OnCallAgent-门型展架.jpg`
- 门型展架可编辑源文件：`contest-deliverables/智能OnCallAgent-门型展架-可编辑版.pptx`
- 演示视频脚本：`contest-deliverables/10-演示视频录制脚本.md`
- 完整剪辑台本：`contest-deliverables/11-完整视频剪辑脚本.md`
- 字幕文件：`contest-deliverables/subtitles/`
- 产品功能演示视频：`contest-deliverables/OnCallAgent_Function_Demo.mp4`

公开仓库中不包含真实 DashScope API Key、接口 Token、服务器私钥、个人学习资料、编译产物或运行数据。评审需要调用 `/chat` 时，可单独提供临时 `CONTEST_API_TOKEN`。
