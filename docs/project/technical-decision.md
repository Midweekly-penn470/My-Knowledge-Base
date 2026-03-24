# Technical Decision

日期：2026-03-18

## 1. 项目类型

- 结论：采用 `Type C`
- 原因：项目包含多用户、知识库管理、文件上传、异步任务、外部 AI 平台集成和共享访问，已经明显超过工具脚本或单页 Demo 范围。

## 2. 技术路线

### 前端

- React + Vite
- 当前阶段保留 skeleton，后续再补业务页与生产构建

### 后端

- Spring Boot 3.3
- Spring MVC
- 模块化单体，中文解释：单服务部署、内部按业务模块拆分
- 正式运行时目标：`Java 21`
- 当前本地验证：`JDK 17`

### 数据与基础设施

- PostgreSQL：业务数据
- Redis：缓存、限流和后续任务辅助
- MinIO：文档对象存储
- Dify self-hosted：知识库与问答能力
- OCR 独立容器：扫描件识别与后续文档预处理

## 3. Dify 决策

- 结论：采用 `self-hosted`
- 原因：
  - 更符合“可部署、可回滚、可审查”的项目目标
  - 环境边界可控，便于本地、测试、正式环境对齐
  - 更适合简历和项目叙事，不依赖第三方 SaaS 演示环境

## 4. 部署拓扑

- 结论：采用“同机单服务器 + 分容器隔离”
- 说明：
  - 本项目 compose：`server + postgres + redis + minio`
  - Dify：同机独立栈
  - OCR：同机独立容器或独立 worker
  - 公网入口只保留 `Nginx 80/443`

## 5. 本地开发模式

- 结论：采用 `Hybrid Dev`
- 说明：
  - 前端本地启动
  - 后端本地启动
  - PostgreSQL / Redis / MinIO / Dify 用 Docker
  - OCR 在本地容器或独立进程接入

## 6. 端口规划

- Frontend: `3001`
- Backend API: `8081`
- PostgreSQL: `5432`
- Redis: `6379`
- MinIO API: `9000`
- MinIO Console: `9001`
- Dify API: `8088`，内部访问
- OCR Service: `8090`，内部访问

说明：最终项目按部署标准统一使用 `3001/8081`，不再沿用草稿中的 `3000/8080`。

## 7. 当前未决项

- OCR 最终是 `PaddleOCR` 还是 `OCRmyPDF + OCR Engine` 组合
- 异步任务首版是否继续使用数据库任务表，还是补 `Redis Stream`
- Dify document ingestion 的失败重试策略细节
