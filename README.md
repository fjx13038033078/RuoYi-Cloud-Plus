# 执法视频 AI 检测与视频切割系统 — 后端（ruoyi-camera）

本仓库是「执法记录仪智能分析系统」的后端服务，在微服务框架基础上扩展了 **执法记录仪视频智能分析模块（ruoyi-camera）**，承担整个系统的「业务编排 + 持久化 + 消息中枢」职责。它向上对接前端与定时调度（SnailJob），向下通过 RabbitMQ 与 Python AI 服务（FastAPI）解耦协作，支撑两条核心业务链路：**AI 违规检测** 与 **视频人体切割**。

> 配套仓库：Python AI 服务 `cameraAi`（FastAPI）、前端 `plus-ui`（Vue3）。

---

## 一、两条核心业务链路

| 链路 | 触发方式 | dataSource | 落库表 | 用途 |
|------|----------|------------|--------|------|
| **AI 违规检测** | SnailJob `cameraManagementJobExecutor` 定时扫描 | `scan` | `camera_management` | 自动识别视频中的安全违规行为并匹配规章制度 |
| **视频人体切割** | SnailJob `videoClipScanJobExecutor` 定时扫描 | `clip` | `video_clip`（原视频入 `camera_management`） | 用 YOLO 检测有人时段，FFmpeg 切出有效片段 |

两条链路在代码层面完全独立，分别使用各自的执行器、Dubbo 服务与 MQ 队列。

## 二、AI 违规检测链路

```
SnailJob(cameraManagementJobExecutor)
    │ Dubbo: scanInsertFromFolder(folder)   ← 任务参数可配置目录，递归扫描子目录
    ▼
ruoyi-camera
    │ ① 递归扫描 NAS/本地目录，过滤视频、排除已入库文件
    │ ② 上传 MinIO + 写入 camera_management(dataSource=scan)
    │ ③ 事务提交后发送 MQ：video.upload.queue（含 24h 预签名 URL）
    ▼ RabbitMQ
cameraAi (FastAPI)
    │ YOLO 辅助 → 视觉模型(hrylora) 识别违规 → RAG 匹配规章制度 → OpenCV 截取违规帧
    ▼ MQ：video.result.queue
ruoyi-camera (VideoResultConsumer)
    │ 更新 camera_management：ai_check_status / has_violation / ai_check_result
    │ / events_json(含相关规章制度) / screenshot_url 等
    ▼
plus-ui 检测记录页：AI 报告 / 违规标记 / 关键帧截图 / 规章制度 / 人工复判
```

## 三、视频人体切割链路

```
SnailJob(videoClipScanJobExecutor)
    │ Dubbo: scanAndSubmitClipTasks(folder)
    ▼
ruoyi-camera
    │ ① 扫描入库（scanInsertFromFolder(folder, false)，不触发 AI MQ）
    │ ② 原视频上传 MinIO + 入 camera_management(dataSource=clip)
    │ ③ 逐条发送 MQ：video.clip.queue
    ▼ RabbitMQ
cameraAi (FastAPI)
    │ 下载原视频 → YOLO 人体片段检测 → FFmpeg(-c copy) 切割 → 切片上传 MinIO(clips/)
    ▼ MQ：video.clip.result.queue
ruoyi-camera (VideoClipResultConsumer)
    │ 写入 video_clip 表：clipIndex / startSecond / endSecond / 原视频名 / 切片总数 等
    ▼
plus-ui 视频切割页：切片列表 / 播放 / 下载 / 刷新预签名 URL
```

## 四、RabbitMQ 队列总览

| 用途 | Exchange | Queue | 方向 |
|------|----------|-------|------|
| AI 检测任务 | `video.upload.exchange` | `video.upload.queue` | Java → Python |
| AI 检测结果 | `video.result.exchange` | `video.result.queue` | Python → Java |
| 切割任务 | `video.clip.exchange` | `video.clip.queue` | Java → Python |
| 切割结果 | `video.clip.result.exchange` | `video.clip.result.queue` | Python → Java |

## 五、模块结构

```
ruoyi-camera/
├── consumer/                       # MQ 消费者
│   ├── VideoResultConsumer         # 消费 AI 检测结果，更新 camera_management
│   └── VideoClipResultConsumer     # 消费切割结果，写入 video_clip
├── config/VideoMqConfig            # RabbitMQ 队列/交换机声明
├── controller/                     # REST 接口（列表、详情、播放URL、上传演示、人工复判等）
├── domain/                         # 实体 / VO / MQ 消息
│   ├── CameraManagement(+Vo)       # 视频信息实体（含 eventsJson 等 AI 字段）
│   ├── VideoUploadMessage          # AI 检测任务消息
│   ├── VideoAnalysisResult         # AI 检测结果消息（含 eventsJson）
│   └── VideoClipTaskMessage / VideoClipResult  # 切割任务/结果消息
├── dubbo/                          # Dubbo 远程服务（供 ruoyi-job 调用）
│   ├── RemoteCameraServiceImpl             # scanInsertFromFolder
│   └── RemoteVideoClipScanServiceImpl      # scanAndSubmitClipTasks
└── service/impl/
    ├── VideoScanUploadServiceImpl  # 扫描+上传+入库（triggerAiAnalysis 控制是否发 AI MQ）
    ├── VideoAiResultServiceImpl    # AI 结果落库
    ├── VideoClipScanServiceImpl    # 切割扫描编排
    └── VideoMessageServiceImpl     # MQ 发送
```

## 六、定时任务（SnailJob）

| 执行器名 | 作用 | 任务参数 |
|----------|------|----------|
| `cameraManagementJobExecutor` | AI 检测扫描 | 扫描根目录（留空用默认 `D:\执法记录仪`，递归子目录） |
| `videoClipScanJobExecutor` | 视频切割扫描 | 扫描根目录（留空用 Nacos `camera.clip-scan.target-folder`） |

> 目录支持在 SnailJob 后台「任务参数」中配置；底层用 `Files.walk` 递归，只需填顶层目录。

## 七、关键数据库字段（camera_management）

| 字段 | 说明 |
|------|------|
| `ai_check_status` | AI 检测状态（0未检测/1检测中/2完成/3失败） |
| `ai_check_result` | AI 分析描述（`{"description":"..."}`） |
| `events_json` | 违规事件列表 JSON，**含每条事件对应的相关规章制度** |
| `has_violation` / `violation_type` | 是否违规 / 违规类型 |
| `violation_start_second` / `violation_end_second` | 违规起止时间点（秒） |
| `screenshot_url` | 违规关键帧截图 URL |
| `data_source` | 数据来源（`scan`=AI检测 / `clip`=切割扫描） |

> 相关增量 SQL 位于 `script/sql/`（如 `add_camera_events_json.sql`）。

## 八、依赖环境

- JDK 17 / 21、Spring Boot 3.x、Apache Dubbo 3.x、Nacos、SnailJob
- RabbitMQ（与 Python 端配置一致）、MinIO（对象存储）、MySQL
- 配套 Python AI 服务 `cameraAi` 与前端 `plus-ui`

> 技术底座基于 Dromara RuoYi-Cloud-Plus 微服务框架，框架自身的通用能力与文档详见其官方仓库，此处不再赘述。
