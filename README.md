# 执法视频 AI 检测与视频切割系统 — 后端（ruoyi-camera）

本仓库是「执法记录仪智能分析系统」的后端服务，在微服务框架基础上扩展了 **执法记录仪视频智能分析模块（ruoyi-camera）**，承担整个系统的「业务编排 + 持久化 + 消息中枢」职责。它向上对接前端与定时调度（SnailJob），向下通过 RabbitMQ 与 Python AI 服务（FastAPI）解耦协作，支撑两条核心业务链路：**AI 违规检测（以视频切分为预处理，逐片检测后聚合）** 与 **纯视频人体切割**。

> 配套仓库：Python AI 服务 `cameraAi`（FastAPI）、前端 `plus-ui`（Vue3）。

---

## 一、两条核心业务链路

**视频切分是 AI 检测的前置预处理**：AI 检测链路扫描入库后先切出「有人」短片段，再逐片送大模型检测，最后聚合写回原视频记录，避免对 20 分钟长视频直接跑大模型。

| 链路 | 触发方式 | dataSource | 落库表 | 用途 |
|------|----------|------------|--------|------|
| **AI 违规检测（含切分预处理）** | SnailJob `cameraManagementJobExecutor` 定时扫描 | `scan` | 切片级 `video_clip`，聚合后 `camera_management` | 切分有人片段 → 逐片识别安全违规并匹配规章制度 → 聚合 |
| **纯视频人体切割** | SnailJob `videoClipScanJobExecutor` 定时扫描 | `clip` | `video_clip`（原视频入 `camera_management`） | 仅用 YOLO 检测有人时段、FFmpeg 切片，**不触发 AI** |

两条链路共用切割能力，由 `camera_management.data_source` 区分：`scan` 链路切完自动逐片发 AI 任务，`clip` 链路切完即止。

## 二、AI 违规检测链路（切分预处理 + 逐片检测 + 聚合）

```
SnailJob(cameraManagementJobExecutor)
    │ Dubbo: scanInsertFromFolder(folder)   ← 任务参数可配置目录，递归扫描子目录
    ▼
ruoyi-camera
    │ ① 递归扫描 NAS/本地目录，过滤视频、排除已入库文件
    │ ② 上传 MinIO + 写入 camera_management(dataSource=scan, ai_check_status=1 检测中)
    │ ③ 事务提交后发送切割任务：video.clip.queue（含 7 天预签名 URL）
    ▼ RabbitMQ
cameraAi (FastAPI)
    │ 下载原视频 → YOLO 人体片段检测 → FFmpeg 切割 → 切片上传 MinIO(clips/)
    ▼ MQ：video.clip.result.queue
ruoyi-camera (VideoClipResultConsumer)
    │ ④ 切片写入 video_clip（ai_check_status=1）
    │ ⑤ 0 切片（无人）→ 原视频直接置「完成、无违规」
    │ ⑥ N 切片 → 事务提交后逐片发 AI 任务：video.upload.queue
    │            （消息含 clipId + clipStartSecond + 切片 7 天预签名 URL）
    ▼ RabbitMQ
cameraAi (FastAPI)
    │ YOLO 辅助 → 视觉模型(hrylora) 识别违规 → RAG 匹配规章制度 → OpenCV 截取违规帧
    │ → 事件时间按 clipStartSecond 偏移回原视频时间轴
    ▼ MQ：video.result.queue（含 clipId）
ruoyi-camera (VideoResultConsumer → VideoAiResultServiceImpl)
    │ ⑦ 切片级结果写 video_clip（ai_check_status / ai_events_json / ai_screenshot_url 等）
    │ ⑧ 该视频全部切片完成 → 聚合写回 camera_management：
    │    事件按时间排序合并 / 违规取首个违规切片 / 耗时累加 / 全部失败置 3
    ▼
plus-ui 检测记录页：AI 报告 / 违规标记 / 关键帧截图 / 规章制度 / 人工复判
```

## 三、纯视频人体切割链路（不触发 AI）

```
SnailJob(videoClipScanJobExecutor)
    │ Dubbo: scanAndSubmitClipTasks(folder)
    ▼
ruoyi-camera
    │ ① 扫描入库（scanInsertFromFolder(folder, false)）
    │ ② 原视频上传 MinIO + 入 camera_management(dataSource=clip)
    │ ③ 逐条发送 MQ：video.clip.queue
    ▼ RabbitMQ
cameraAi (FastAPI)
    │ 下载原视频 → YOLO 人体片段检测 → FFmpeg(-c copy) 切割 → 切片上传 MinIO(clips/)
    ▼ MQ：video.clip.result.queue
ruoyi-camera (VideoClipResultConsumer)
    │ 写入 video_clip 表：clipIndex / startSecond / endSecond / 原视频名 / 切片总数 等
    │ （dataSource=clip，不派发 AI 任务）
    ▼
plus-ui 视频切割页：切片列表 / 播放 / 下载 / 刷新预签名 URL
```

## 四、RabbitMQ 队列总览

| 用途 | Exchange | Queue | 方向 |
|------|----------|-------|------|
| AI 检测任务（切片级，含 `clipId`/`clipStartSecond`） | `video.upload.exchange` | `video.upload.queue` | Java → Python |
| AI 检测结果（含 `clipId`） | `video.result.exchange` | `video.result.queue` | Python → Java |
| 切割任务 | `video.clip.exchange` | `video.clip.queue` | Java → Python |
| 切割结果 | `video.clip.result.exchange` | `video.clip.result.queue` | Python → Java |

## 五、模块结构

```
ruoyi-camera/
├── consumer/                       # MQ 消费者
│   ├── VideoResultConsumer         # 消费 AI 检测结果（切片级落库 + 聚合写回）
│   └── VideoClipResultConsumer     # 消费切割结果，写入 video_clip；scan 链路逐片派发 AI 任务
├── config/VideoMqConfig            # RabbitMQ 队列/交换机声明
├── controller/                     # REST 接口（列表、详情、播放URL、上传演示、人工复判等）
├── domain/                         # 实体 / VO / MQ 消息
│   ├── CameraManagement(+Vo)       # 视频信息实体（含 eventsJson 等 AI 字段）
│   ├── VideoClip                   # 切片实体（含切片级 AI 字段 ai_check_status 等）
│   ├── VideoUploadMessage          # AI 检测任务消息（含 clipId / clipStartSecond）
│   ├── VideoAnalysisResult         # AI 检测结果消息（含 clipId / eventsJson）
│   └── VideoClipTaskMessage / VideoClipResult  # 切割任务/结果消息
├── dubbo/                          # Dubbo 远程服务（供 ruoyi-job 调用）
│   ├── RemoteCameraServiceImpl             # scanInsertFromFolder
│   └── RemoteVideoClipScanServiceImpl      # scanAndSubmitClipTasks
└── service/impl/
    ├── VideoScanUploadServiceImpl  # 扫描+上传+入库（triggerAiAnalysis=true 时发切割预处理任务）
    ├── VideoAiResultServiceImpl    # AI 结果落库：切片级更新 + 全部完成后聚合写回 camera_management
    ├── VideoClipScanServiceImpl    # 纯切割扫描编排
    └── VideoMessageServiceImpl     # MQ 发送（整段直发 + 切片级 sendClipDetectionMessage）
```

## 六、定时任务（SnailJob）

| 执行器名 | 作用 | 任务参数 |
|----------|------|----------|
| `cameraManagementJobExecutor` | AI 检测扫描（**主任务**：扫描 → 切分 → 逐片 AI → 聚合，全流程串联） | 扫描根目录（留空用默认 `D:\执法记录仪`，递归子目录） |
| `videoClipScanJobExecutor` | 纯视频切割扫描（不触发 AI，可选） | 扫描根目录（留空用 Nacos `camera.clip-scan.target-folder`） |

> 目录支持在 SnailJob 后台「任务参数」中配置；底层用 `Files.walk` 递归，只需填顶层目录。

## 七、关键数据库字段

**camera_management（原视频，聚合结果）：**

| 字段 | 说明 |
|------|------|
| `ai_check_status` | AI 检测状态（0未检测/1检测中/2完成/3失败）；scan 链路入库即置 1，全部切片完成后聚合置 2/3 |
| `ai_check_result` | AI 分析描述（各切片描述带「【切片N 时间区间】」标注拼接） |
| `events_json` | 违规事件列表 JSON（各切片事件合并、按时间排序，**含每条事件对应的相关规章制度**） |
| `has_violation` / `violation_type` | 是否违规 / 违规类型（取时间轴上首个违规切片） |
| `violation_start_second` / `violation_end_second` | 违规起止时间点（秒，原视频时间轴） |
| `screenshot_url` | 违规关键帧截图 URL |
| `data_source` | 数据来源（`scan`=AI检测 / `clip`=纯切割扫描） |

**video_clip（切片，切片级 AI 结果）：**

| 字段 | 说明 |
|------|------|
| `clip_status` | 切片状态（0处理中/1完成/2失败） |
| `ai_check_status` | 切片 AI 状态（0待检测/1检测中/2完成/3失败；纯切割链路恒为 0） |
| `ai_has_violation` / `ai_violation_type` / `ai_description` | 切片级违规结论 |
| `ai_events_json` / `ai_screenshot_url` | 切片事件 JSON（时间已偏移到原视频时间轴）/ 截图 |
| `ai_violation_start_second` / `ai_violation_end_second` / `ai_process_time` | 违规起止秒 / 耗时 |

> 相关增量 SQL 位于 `script/sql/`（如 `add_camera_events_json.sql`、`add_video_clip_ai_fields.sql`）。

## 八、依赖环境

- JDK 17 / 21、Spring Boot 3.x、Apache Dubbo 3.x、Nacos、SnailJob
- RabbitMQ（与 Python 端配置一致）、MinIO（对象存储）、MySQL
- 配套 Python AI 服务 `cameraAi` 与前端 `plus-ui`

> 技术底座基于 Dromara RuoYi-Cloud-Plus 微服务框架，框架自身的通用能力与文档详见其官方仓库，此处不再赘述。
