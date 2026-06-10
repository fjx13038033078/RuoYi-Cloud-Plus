# 执法记录仪 AI 视频检测系统 — 部署方案（基站预处理 + 服务器识别）

> **适用环境**  
> - **视频采集基站**：Win11 PC + 多块大容量硬盘，矿工每日上传执法记录仪视频  
> - **AI 推理服务器**：4×A100，与基站内网互通  
>
> **技术栈**：Vue3（plus-ui）+ Spring Cloud（RuoYi-Cloud-Plus / ruoyi-camera）+ FastAPI（cameraAi）  
>
> **文档版本**：2026-06（v2 — 基站切分/存储 + 服务器识别）  
> **说明**：本文档描述目标部署架构及相对**当前代码**所需的改造点，**不包含代码实现**。

---

## 一、部署思路与目标

### 1.1 核心原则

| 原则 | 说明 |
|------|------|
| **算力下沉到合适节点** | 视频切分（YOLO 小模型 + FFmpeg）在**基站**完成；视觉大模型 + RAG 在**服务器**完成 |
| **存储分层** | **原视频 + 切片** 对象存储在**基站 MinIO**（挂大硬盘）；服务器 MinIO **可选**，仅存截图等小对象或不再存原视频 |
| **识别方式** | 服务器通过 MQ 消息中的 **预签名 URL** 从基站 MinIO **拉流/拉取** 进行分析（现有 cameraAi 已支持 URL 驱动） |
| **结果集中** | MySQL、业务 API、前端均在**服务器**；管理人员只访问服务器 Web |

### 1.2 要解决什么问题

- 原视频约 **20 分钟/条**、日增量大，若在服务器完成「扫描 → 整段上传 → 切分 → 识别」，磁盘与 CPU/GPU 压力均集中在 A100 服务器。
- 切分本质是 **AI 识别的预处理**，应在靠近数据源（基站）完成，仅将**有效片段**交给服务器做违规识别。

### 1.3 目标链路（部署视角）

```
矿工上传原视频
    ↓
【基站】落盘 → YOLO 人体切分 → FFmpeg 切片 → 上传基站 MinIO（clips/）
    ↓
【基站/服务器】登记元数据 → 为每个切片生成预签名 URL → 投递 AI 检测 MQ
    ↓
【服务器】cameraAi 拉流（基站 MinIO URL）→ 视觉模型 + RAG → 结果 MQ
    ↓
【服务器】ruoyi-camera 落库 → plus-ui 展示
```

---

## 二、总体架构

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  执法记录仪视频采集基站（Win11，多块 HDD）                                      │
│                                                                              │
│  D:\执法记录仪\  ← 矿工/同步工具每日落盘（队别/日期/文件名规范）                 │
│                                                                              │
│  ┌─────────────┐  ┌──────────────────────────────────────────────────┐    │
│  │ MinIO       │  │ cameraAi — 切割模式（CPU）                          │    │
│  │ :9000       │  │  · clip_mq_consumer（消费 video.clip.queue）       │    │
│  │ 数据目录→ HDD│  │  · YOLO + FFmpeg                                  │    │
│  └─────────────┘  │  · 切片上传至本地 MinIO（clips/）                    │    │
│                   └──────────────────────────────────────────────────┘    │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │ 编排（二选一，见 §5.3）                                               │    │
│  │  · 方案 A：ruoyi-job 执行器 + ruoyi-camera（扫描/发 MQ，连服务器中间件）│    │
│  │  · 方案 B：基站定时脚本 / 扩展服务（扫目录 → 发切割/登记 API）          │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  不部署：MySQL、Nacos、大模型、AI 检测 consumer、plus-ui                      │
└───────────────────────────────┬──────────────────────────────────────────────┘
                                │ 内网
                                │  · RabbitMQ 5672（基站 → 服务器）
                                │  · 基站 MinIO 9000（服务器拉流 GET）
                                ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│  4×A100 AI 推理服务器（Linux 推荐）                                           │
│                                                                              │
│  基础设施：MySQL / Redis / Nacos / RabbitMQ / SnailJob Server                 │
│  Java：ruoyi-gateway / auth / system / resource / job / ruoyi-camera          │
│  AI：cameraAi — 识别模式（GPU）+ vLLM 视觉/思考模型                            │
│  前端：Nginx + plus-ui                                                        │
│  MinIO（可选）：仅违规截图等轻量对象；原视频/切片可不落服务器盘                  │
└───────────────────────────────┬──────────────────────────────────────────────┘
                                ▼
                    浏览器 http://<服务器IP>  →  检测记录 / 切割列表 / 复判
```

---

## 三、节点职责对照

| 能力 | 基站 | 服务器 |
|------|:----:|:------:|
| 原视频落盘（D:\） | ✅ | ❌ |
| 视频人体切分（YOLO + FFmpeg） | ✅ | ❌（迁出） |
| 原视频/切片对象存储（MinIO） | ✅ 主存储 | ⚪ 可选（截图） |
| RabbitMQ | ❌ | ✅ |
| MySQL / 业务 API / 前端 | ❌ | ✅ |
| AI 违规检测（大模型 + RAG） | ❌ | ✅ |
| SnailJob 调度中心 | ❌ | ✅ |
| SnailJob 执行器（扫描/发任务） | ✅ 推荐 | ⚪ 可保留管理端 |

---

## 四、端到端数据流（目标态）

### 4.1 阶段一：基站 — 切分与对象存储

1. 新视频出现在 `D:\执法记录仪\...`（递归子目录）。
2. **扫描**发现新文件（SnailJob 触发 ruoyi-camera，或基站本地调度）。
3. **可选**：原视频上传至基站 MinIO 的 `raw/` 前缀（便于归档；也可切分后直接读本地文件，见代码改造）。
4. 向 RabbitMQ 发送 **`video.clip.queue`** 任务（含原视频预签名 URL 或本地路径策略，见 §八）。
5. **基站 cameraAi** `clip_mq_consumer` 消费任务：
   - 下载/读取原视频；
   - YOLO 检测有人时段 → FFmpeg 切分；
   - 切片上传至 **基站 MinIO** `clips/`；
   - 发送 **`video.clip.result.queue`** 至服务器。
6. **服务器** `VideoClipResultConsumer` 将切片元数据写入 **`video_clip`** 表。

### 4.2 阶段二：服务器 — 拉流识别与落库

7. **（待开发）** 切割落库后，按每个切片生成 **基站 MinIO 预签名 URL**，投递 **`video.upload.queue`**（AI 检测任务）。
8. **服务器 cameraAi** `mq_consumer` 消费任务，使用 `presigned_url`：
   - OpenCV / YOLO 辅助读流；
   - 视觉模型（vLLM）按 URL 分析；
   - RAG 匹配规章；
   - 违规截图上传（建议 **服务器 MinIO** 或仍写基站 MinIO，需统一配置）。
9. 发送 **`video.result.queue`**。
10. **服务器** `VideoResultConsumer` → `VideoAiResultServiceImpl` 更新 **`camera_management`**（及关联展示字段）。
11. **plus-ui** 检测记录页展示报告、关键帧、规章制度；视频切割页展示切片列表。

### 4.3 与当前实现的差异（重要）

| 环节 | 当前代码 | 目标部署 |
|------|----------|----------|
| 切分执行位置 | 服务器 cameraAi `clip_mq_consumer` | **基站** cameraAi |
| AI 检测输入 | 多为**整段原视频**（`cameraManagementJobExecutor`） | **切片** presigned URL |
| 原视频 MinIO | 默认与 Java 同机配置 | **基站 MinIO** |
| 切分 → 识别 | **未自动串联** | 切割完成后**自动发 AI MQ** |
| AI 结果归属 | 按 `video_id`（原视频） | 需支持 **按切片** 或汇总到原视频（§八） |

---

## 五、基站部署说明

### 5.1 硬件与系统

| 项目 | 建议 |
|------|------|
| 系统 | Windows 11 |
| 磁盘 | 系统盘 + 一块或多块数据盘；MinIO 数据目录指向大容量盘 |
| CPU / 内存 | 切分为 CPU 任务，建议 ≥ 8 核 / ≥ 16GB（视并发而定） |
| GPU | **不需要** |
| 网络 | 静态 IP；与服务器千兆/万兆互通 |

### 5.2 目录规划

```
D:\执法记录仪\                    ← SnailJob / 扫描根目录（示例）
├── 出矿1队\张三6.09\*.mp4
└── ...

D:\minio-data\                    ← MinIO 后端存储（可映射多盘）
```

MinIO Bucket 建议：`zhifajiluyi`（与线上一致），对象前缀示例：

- `raw/yyyy/MM/dd/` — 原视频（若归档）
- `clips/yyyy/MM/dd/` — 切分后片段

### 5.3 基站必须部署的服务

#### （1）MinIO

- 安装方式：Windows 可执行程序或 Docker Desktop。
- 监听：`0.0.0.0:9000`（API）、`:9001`（Console）。
- **安全**：防火墙仅允许 **服务器 IP** 访问 9000/9001；强密码；**固定 IP**。
- 创建 Bucket、访问密钥，与 Java / Python 配置一致。

#### （2）cameraAi（切割专用实例）

- 部署路径示例：`D:\apps\cameraAi`
- Python 3.10+、FFmpeg（加入 PATH）、YOLO 权重 `yolo/yolo11n.pt`
- **仅启用切割相关能力**（目标态，需改 `main.py` 支持模式开关）：
  - ✅ `clip_mq_consumer`
  - ❌ `mq_consumer`（AI 检测，应在服务器）
- **`.env` 要点**：

```env
# 连接服务器 RabbitMQ
RABBITMQ_HOST=192.168.26.20
RABBITMQ_PORT=5672

# 基站本地 MinIO（切片上传目标）
MINIO_ENDPOINT=127.0.0.1:9000
# 或 192.168.26.10:9000（生成给服务器拉的 URL 时须用对端可达地址，见 §7.2）

MINIO_ACCESS_KEY=...
MINIO_SECRET_KEY=...
MINIO_BUCKET=zhifajiluyi
CLIP_MINIO_PREFIX=clips

# 切分用 CPU
YOLO_DEVICE=cpu
FFMPEG_BINARY=C:\ffmpeg\bin\ffmpeg.exe
```

- 启动示例：`uvicorn app.main:app --host 0.0.0.0 --port 8000`（HTTP 可选，便于健康检查）

#### （3）任务编排（扫描 + 发切割 MQ）

与现有 SnailJob / `VideoClipScanServiceImpl` 对齐，**推荐方案 A**：

| 组件 | 部署位置 | 说明 |
|------|----------|------|
| SnailJob **Server** | 服务器 | 调度中心 |
| SnailJob **执行器** + **ruoyi-job** | **基站** | 跑 `videoClipScanJobExecutor` |
| **ruoyi-camera** | **基站** | 扫描 `D:\执法记录仪`，发 `video.clip.queue` |

基站 Java 进程通过 Nacos 发现服务器上的 MySQL、RabbitMQ、Redis（或本地仅配连接串指向服务器）。  
**不必**在基站部署 gateway、auth、前端。

**方案 B（轻量）**：暂不部署 Java，用 Windows 计划任务 + 脚本调用服务器 REST/Dubbo 网关暴露的「提交切割任务」接口（需新增 API，见 §八）。

### 5.4 基站不建议部署

- MySQL、Redis、Nacos Server（可只作客户端连远程）
- vLLM / 视觉大模型 / RAG Embedding 服务
- cameraAi **AI 检测** consumer
- plus-ui、ruoyi-gateway

### 5.5 基站运维要点

- Win11 **勿睡眠**（夜间切分任务）
- 磁盘空间监控（MinIO + 落盘目录）
- MinIO / cameraAi 进程开机自启（NSSM、任务计划程序）
- 矿工上传规范：文件完整后再进入扫描目录（避免半截文件被扫）

---

## 六、服务器部署说明

### 6.1 部署清单

| 层级 | 组件 | 说明 |
|------|------|------|
| 基础设施 | MySQL、Redis、Nacos、RabbitMQ、SnailJob Server | 与现网一致，见 `script/docker/docker-compose.yml` |
| 对象存储 | MinIO（**可选**） | 建议仅存 **违规截图**；原视频/切片在基站 MinIO |
| Java 微服务 | gateway、auth、system、resource、**job**、**camera** | **不**在服务器跑切割 consumer |
| AI | vLLM（22000/22001）、**cameraAi 识别实例** | 仅 `mq_consumer` + RAG；`yolo_device=cuda` |
| 前端 | plus-ui + Nginx | 统一入口 |

### 6.2 cameraAi（识别专用实例）

```env
RABBITMQ_HOST=127.0.0.1

# 视觉/思考模型 — 本机 vLLM
VISION_MODEL_URL=http://127.0.0.1:22001/v1
THINKING_MODEL_URL=http://127.0.0.1:22000/v1

# 截图上传 — 建议服务器 MinIO（小文件）
MINIO_ENDPOINT=127.0.0.1:9000
MINIO_BUCKET=zhifajiluyi

YOLO_DEVICE=cuda:0
MQ_PREFETCH_COUNT=1
```

- **不启动** `clip_mq_consumer`（由基站实例承担）。
- 分析时使用 MQ 中的 `presigned_url`，该 URL 必须指向 **基站 MinIO** 上的**切片对象**（服务器需能 HTTP GET 基站 `9000`）。

### 6.3 Java / OSS 双端点配置

在 RuoYi 后台 **系统管理 → OSS 配置** 建议两条：

| configKey | endpoint 示例 | 用途 |
|-----------|---------------|------|
| `minio-base` | `192.168.26.10:9000` | 原视频/切片；生成给 AI **拉流** 的预签名 URL |
| `minio` | `192.168.26.20:9000` | 违规截图等（可选） |

框架支持 `OssFactory.instance("minio-base")`（见 `OssFactory.java`）。

### 6.4 SnailJob 配置（服务器）

| 执行器 | 建议部署节点 | Cron 示例 | 任务参数 |
|--------|--------------|-----------|----------|
| `videoClipScanJobExecutor` | **基站** ruoyi-job | `0 0 20 * * ?` | `D:\执法记录仪` |
| `cameraManagementJobExecutor` | **停用或改造** | — | 目标态不再对整段原视频做 AI，改由切分后自动触发 |

调度中心在服务器 SnailJob 控制台创建任务，**执行器注册在基站** 即可远程触发。

### 6.5 数据库与前端

- 导入 `script/sql/` 下业务增量脚本（`add_video_clip.sql`、`add_camera_events_json.sql` 等）。
- 前端构建 `npm run build:prod`，Nginx 反代 `/prod-api/` → gateway `:8080`。

---

## 七、网络、端口与 URL 策略

### 7.1 IP 示例

| 节点 | IP |
|------|-----|
| 基站 | `192.168.26.10` |
| 服务器 | `192.168.26.20` |

### 7.2 关键连通性

| 源 | 目标 | 端口 | 用途 |
|----|------|------|------|
| 基站 ruoyi-job / cameraAi | 服务器 | 5672 | RabbitMQ |
| 基站 Java | 服务器 | 8848、3306、6379 | Nacos、MySQL、Redis |
| **服务器** cameraAi / **vLLM** | **基站** | **9000** | **按 presigned URL 拉取切片/视频** |
| 管理人员 PC | 服务器 | 80/443 | Web |

> **注意**：视觉模型 API 会把 `video_url` 交给 vLLM，由 **vLLM 进程** 发起 HTTP 请求；必须保证 **推理机 → 基站 MinIO** 可达，且预签名 URL 中的 host 为 **192.168.26.10**，不能是 `127.0.0.1`（若在基站生成 URL 给服务器用）。

### 7.3 预签名 URL

- 当前有效期：**7 天**（Java / Python 已配置 `Duration.ofDays(7)`）。
- 切片对象在基站 MinIO 上不变，过期后需用 **`minio-base`** 对应 `OssClient` 重新签名（播放/重分析）。

---

## 八、相对当前代码需要的改造（清单）

> 以下为实施本部署方案时**预期需要修改**的模块，便于排期；**本文档不实施代码变更**。

### 8.1 总览

| 优先级 | 改造主题 |
|--------|----------|
| P0 | 切分逻辑迁移到基站；服务器 cameraAi 关闭 clip consumer |
| P0 | 双 MinIO（`minio-base` / `minio`）与预签名 URL 生成来源 |
| P0 | **切分完成 → 自动投递 AI 检测 MQ**（流水线串联） |
| P1 | AI 任务粒度从「原视频」改为「切片」及结果落库模型 |
| P1 | 扫描/上传策略：原视频不必整段上传到服务器 |
| P2 | 前端展示切片级/汇总级检测报告 |

---

### 8.2 Java（RuoYi-Cloud-Plus / ruoyi-camera）

| 文件 / 模块 | 当前行为 | 建议改造 |
|-------------|----------|----------|
| `VideoScanUploadServiceImpl` | 扫描本地路径 → `OssFactory.instance()` 上传 → 可选发 AI MQ | 上传目标改为 `OssFactory.instance("minio-base")`；**切分链路**可 `triggerAiAnalysis=false`；评估是否**跳过原视频 upload**、仅登记路径 + 发切割 MQ |
| `VideoClipScanServiceImpl` | 扫描 → 入库 → 生成 presignedUrl → `video.clip.queue` | `generatePresignedUrl` 使用 **minio-base**；部署在**基站**时扫描 `D:\...` |
| `VideoClipResultConsumer` | 切割结果写入 `video_clip` | **新增**：落库后对每个 `clip` 生成基站 presignedUrl → 调用 `VideoMessageServiceImpl.sendVideoDetectionMessage`（**切分→识别串联**） |
| `VideoMessageServiceImpl` / `VideoUploadMessage` | AI 任务绑定 `videoId` | 扩展：增加 `clipId` / `objectName` / `ossConfigKey`；或一条切片对应一条 `camera_management` 子记录 |
| `VideoAiResultServiceImpl` | 按 `videoId` 更新 `camera_management` | 支持按 **clipId** 更新 `video_clip` 的 AI 字段，或写入关联表；汇总到原视频供列表展示 |
| `CameraManagementServiceImpl.getPlayUrl` | `OssFactory.instance()` 默认配置 | 按 `sys_oss.service` 或业务字段选择 **minio-base** / **minio** |
| `VideoClipServiceImpl.refreshClipUrl` | 默认 OssClient | 切片 URL 刷新统一走 **minio-base** |
| `CameraManagementJobExecutor` | 扫描并发 **整段 AI 检测** | 目标态 **停用** 或改为仅登记；AI 由切片任务触发 |
| `VideoClipScanJobExecutor` | 执行器默认在服务器 | 文档约定：执行器注册在**基站**；任务参数为 `D:\执法记录仪` |
| `RemoteFileService.saveOssRecord` | 写 `sys_oss` | 记录 `service=minio-base`，便于后续签名 |
| `sys_oss_config`（数据） | 单 MinIO | 新增 **minio-base** 条目，endpoint 指向基站 |

**可选新增**：

- REST：`POST /camera/clip/submitFromBase` — 基站脚本登记已上传 MinIO 的切片并触发 AI（方案 B）。
- MinIO 事件/webhook 或定时 `listObjects` 任务 — 替代纯文件扫描（矿工直传 MinIO 时）。

---

### 8.3 Python（cameraAi）

| 文件 / 模块 | 当前行为 | 建议改造 |
|-------------|----------|----------|
| `app/main.py` | 同时启动 `mq_consumer` + `clip_mq_consumer` | 增加 **运行模式**：`CLIP_ONLY`（基站）/ `DETECT_ONLY`（服务器）/ `ALL`（开发） |
| `clip_mq_consumer.py` | 部署在服务器，MinIO 为 config | 部署在**基站**；`MINIO_ENDPOINT` 指向**基站**；上传 clips 后 URL 需对**服务器可读** |
| `minio_client.py` `upload_video_clip` | 预签名 URL 用于落库 | 返回 URL 的 host 应为 **基站对外 IP**（非 127.0.0.1），供服务器拉流 |
| `mq_consumer.py` / `stream_analyzer.py` | 使用 `task.presigned_url` 分析 | **服务器**保留；一般**无需改逻辑**，确保 URL 指向基站切片 |
| `result_publisher.py` | 截图 upload 到 `minio_*` | 明确截图走 **服务器 MinIO**（与切片分离），需双 endpoint 配置项 |
| `config.py` | 单 MinIO | 可选：`MINIO_BASE_*` 与 `MINIO_SERVER_*` 分离 |

---

### 8.4 前端（plus-ui）

| 页面 / 模块 | 建议改造 |
|-------------|----------|
| `fileRecord/index.vue` | 支持展示「原视频 + 多切片检测结果」或切片维度违规；`refresh` 截图 URL 时走正确 OSS |
| `videoClip/index.vue` | 播放/下载刷新 URL 需后端按 **minio-base** 签名（已有 `refreshClipUrl`，后端改即可） |
| API 类型 | `VideoClipVO` / `CameraManagementVO` 增加 AI 状态、违规字段（若切片级落库） |

---

### 8.5 配置与运维（非代码但必改）

| 项 | 说明 |
|----|------|
| Nacos | 基站 ruoyi-camera 指向服务器中间件地址 |
| RabbitMQ | 同一集群；基站生产者/消费者、服务器消费者 |
| SnailJob | 切分扫描任务绑定**基站执行器** |
| 防火墙 | 服务器 → 基站 9000 必通 |
| 监控 | 基站：磁盘、MinIO、clip consumer 堆积；服务器：AI 队列长度、GPU |

---

## 九、推荐实施步骤

### 阶段 0：网络与存储

1. 基站、服务器静态 IP，互 ping。
2. 基站部署 MinIO，创建 bucket，服务器 curl 测试 `9000` 可达。
3. 服务器部署 RabbitMQ、MySQL、Nacos（原有流程）。

### 阶段 1：切分迁到基站（P0）

1. 基站部署 cameraAi + FFmpeg，`CLIP_ONLY`（改造后）。
2. 基站部署 ruoyi-job + ruoyi-camera（或临时仍在服务器扫描 SMB，过渡用）。
3. OSS 增加 `minio-base`，切分上传与 presignedUrl 走基站。
4. 验证：`video.clip.result.queue` 在服务器落库 `video_clip`。

### 阶段 2：流水线串联（P0）

1. 改造 `VideoClipResultConsumer`（或独立 Service）：每切片发 `video.upload.queue`。
2. 服务器 `DETECT_ONLY` cameraAi 消费，从基站 URL 拉流识别。
3. 验证：`camera_management` / `video_clip` 有 AI 结果，前端可查看。

### 阶段 3：下线服务器切分与原视频 AI（P1）

1. 服务器 `main.py` 关闭 clip consumer。
2. 停用 `cameraManagementJobExecutor` 整段扫描 AI。
3. 可选：取消原视频上传服务器 MinIO，仅保留基站对象。

### 阶段 4：体验与优化（P2）

1. 切片级报告、汇总统计。
2. 基站切分并发、失败重试、DLQ 监控。
3. 预签名 URL 刷新接口统一（截图、切片、播放）。

---

## 十、部署检查清单

### 基站

- [ ] `D:\执法记录仪` 目录规范与磁盘空间
- [ ] MinIO 运行，bucket 与密钥配置
- [ ] cameraAi clip 模式运行，能连服务器 RabbitMQ
- [ ] FFmpeg、YOLO 权重可用，`YOLO_DEVICE=cpu`
- [ ] （方案 A）ruoyi-job 执行器在 SnailJob 控制台在线
- [ ] 生成的 presigned URL host 为基站对服务器可达 IP

### 服务器

- [ ] 中间件与 Java 微服务正常
- [ ] cameraAi 识别模式，**未**启动 clip consumer
- [ ] vLLM 可从基站 MinIO URL 拉取测试文件
- [ ] `video_clip` / AI 结果落库正常
- [ ] plus-ui 可访问

### 联调

- [ ] 人工放入一条测试视频 → 切分 → `video_clip` 有记录 → AI 完成 → 前端有报告
- [ ] 无违规/有违规/截图加载 均验证

---

## 十一、风险与说明

| 风险 | 说明 | 缓解 |
|------|------|------|
| 拉流带宽 | 识别时仍从基站拉切片数据到服务器 | 只传切片不传 20 分钟整段，流量显著下降 |
| vLLM 拉流失败 | URL host/防火墙/证书问题 | 统一 `minio-base` 外网 IP；服务器 curl 预签名 URL |
| 双端 cameraAi 版本 | 基站/服务器代码不一致 | 同仓库同 tag，仅配置不同 |
| 结果归属 | 现 AI 结果只写 `camera_management` | 必须做 §8.2 切片级或汇总改造 |
| 基站 Windows 稳定性 | 睡眠、更新重启 | 7×24 策略、进程守护 |

---

## 十二、附录：与 v1 部署方案（SMB + 服务器全量）对比

| 对比项 | v1（服务器 SMB 扫描 + 服务器 MinIO） | v2（本文：基站切分 + 基站 MinIO） |
|--------|--------------------------------------|-------------------------------------|
| 切分算力 | 服务器 | **基站 CPU** |
| 原视频对象存储 | 服务器 MinIO | **基站 MinIO** |
| 服务器磁盘压力 | 高 | **低** |
| 基站部署复杂度 | 低（仅 SMB） | **中**（MinIO + cameraAi + 可选 Java） |
| 代码改造量 | 小 | **中**（§八） |
| 适用场景 | 快速试点 | **生产 / 大批量** |

---

> 相关文档：`架构图-Mermaid版.md`、`执法视频AI检测与视频切割-系统开发逻辑.md`  
> 若实施 §八 改造，建议同步更新架构图与 README 中的部署描述。
