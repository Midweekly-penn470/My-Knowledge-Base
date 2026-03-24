# Server Deployment Standard

## 目标

这份标准定义“项目如何在服务器上以可维护、可更新、可回滚的方式交付”。

适用范围：

- 单台 Linux 服务器
- 正式部署使用 `Docker` 或 `systemd`
- 对外 Web/API 默认由宿主机 `Nginx` 暴露

## 正式部署允许项

- `Docker`
- `systemd`

## 明确禁止项

- `nohup` 作为正式部署方案
- 直接手工运行主进程当作长期运行方式
- 用 `latest` 作为唯一镜像标签
- 未登记端口直接上线
- 数据库和 Redis 默认直接暴露公网
- 依赖隐藏目录、隐藏环境变量、临时脚本、临时账号

## 高可用基线

这里的“高可用”指单机交付基线，不是多机容灾。

每个项目上线前至少满足：

- 有明确健康检查方式
- 有明确自动重启策略
- 有明确日志入口
- 有明确更新步骤
- 有明确回滚步骤
- 有明确版本标识
- 有明确备份要求
- 有明确观察窗口和回滚触发条件

## 必交付文件

每个可上线项目至少要有：

- `deploy/`
- `deployment.md`
- `deployment-checklist.md`
- `README.md` 中的本地运行说明
- `.env.example`
- `docs/project/technical-decision.md`
- `docs/project/quality-report.md`
- `docs/project/predeploy-report.md`
- `docs/project/release-record.md`

如果是 `systemd` 部署，还必须有：

- `deploy/<project-name>.service`
- `deploy/env/app.env`

如果是 `Docker` 部署，还必须有：

- `deploy/docker-compose.yml`
- `deploy/.env`

如果项目对外提供 Web/API，还必须有：

- `deploy/nginx/<project-name>.conf` 或等价的 `Nginx` 配置文件

## 目录标准

默认部署目录：

```text
/srv/apps/<project-name>/
├── app/
│   ├── current/
│   └── releases/
├── deploy/
│   ├── env/
│   └── nginx/
├── logs/
├── backup/
└── README.md
```

推荐规则：

- `app/current` 指向当前运行版本
- `app/releases` 保留历史版本
- `logs` 保留运行日志或作为统一挂载点
- `backup` 用于备份包、数据库导出、回滚材料

## 端口标准

默认范围：

- frontend/site: `3001-3099`
- backend api: `8081-8099`
- worker/tool: `9001-9099`
- admin/management: `10001-10099`

规则：

- 对外 Web 服务尽量只暴露 `80/443`
- 应用内部端口优先监听 `127.0.0.1`
- 新项目在生成部署文件前必须先完成端口预留
- 上线后必须更新 `server-port-registry.md`

## 用户与权限标准

- 默认使用专用服务用户，禁止把 `root` 作为常规模板默认值
- `systemd` service 需要明确 `User`、`WorkingDirectory`、`ExecStart`
- 敏感配置走 `EnvironmentFile` 或 `.env`，不要写死在 service / compose 文件中

## 日志与状态标准

必须明确以下命令：

- 查看状态
- 查看最近日志
- 跟踪日志
- 重启
- 停止
- 启动

推荐：

- `systemd` 项目以 `journalctl` 为统一日志入口
- `Docker` 项目以 `docker compose logs` 为统一日志入口
- 应用级文件日志可选，但不能替代统一入口

## 健康检查标准

每个项目上线前必须明确至少一种健康检查方式：

- HTTP health endpoint，例如 `/health`, `/actuator/health`
- 进程存活 + 关键端口监听
- 核心接口 smoke test

如果没有健康检查方式，视为未达到上线标准。

## 更新标准

更新文档必须写清：

- 本次发布版本号或镜像 tag
- 发布前备份步骤
- 发布步骤
- 发布后验证步骤
- 失败后的回滚步骤

要求：

- `Docker` 更新必须能切回旧 tag
- `systemd` 更新必须保留旧 release 或旧构建产物
- 如果有数据库迁移，必须补充迁移顺序和回退说明

## 回滚标准

回滚方案不能只写“重新部署”。

至少包含：

- 回滚触发条件
- 回滚所需文件或版本号
- 回滚命令
- 回滚后验证命令

## 备份标准

发布前至少确认以下一项：

- 构建产物备份
- 数据库导出或快照
- 关键配置文件备份

如果发布会改动数据库结构或关键配置，必须明确备份位置和恢复方法。

## Nginx 标准

- 宿主机 `Nginx` 为默认入口
- `server_name`、`listen`、`proxy_pass` 必须明确
- `proxy_pass` 默认指向本机内部端口
- 改动配置前执行 `nginx -t`
- 生效方式默认 `systemctl reload nginx`

## 上线前 Gate

上线前至少全部通过以下检查：

- 部署方式明确
- 端口已预留且无冲突
- 关键部署文件齐全
- 环境变量齐全
- 日志与状态命令明确
- 更新与回滚步骤明确
- 依赖服务已声明
- 质量报告和 predeploy 报告已生成

## 上线后要求

发布完成后必须：

- 执行 smoke test
- 观察 15 到 30 分钟
- 记录异常和结论
- 更新 release record
- 更新 service / port registry

## 例外处理

如果项目确实需要：

- 公网暴露数据库端口
- 额外公网端口
- 多个运行时共存
- Docker 内 Nginx 和宿主机 Nginx 双层代理

则必须在 `technical-decision.md` 和 `predeploy-report.md` 中写明原因、替代方案和风险。
