# 绩效通（Jixiaotong）— 员工培训与智能绩效考核系统

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Vue](https://img.shields.io/badge/Vue-3-42b883.svg)](https://vuejs.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue.svg)](https://www.mysql.com/)
[![Nacos](https://img.shields.io/badge/Nacos-2.x-indigo.svg)](https://nacos.io/)
[![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)](LICENSE)

[English](#english) | [中文](#chinese)

---

<a name="english"></a>
## 🇬🇧 English

### Project Overview
**Jixiaotong (绩效通)** is a microservices-based web platform for enterprise employee training and intelligent performance assessment. It digitizes the full lifecycle of **Learn → Examine → Evaluate → Manage**, covering SOP material learning, online exams, objective auto-grading, AI-assisted subjective review, manager appeals, analytics dashboards, and historical archive search.

The system uses a **separated frontend/backend** architecture: a Vue 3 SPA talks to a Spring Cloud Gateway, which routes to the main performance service. Subjective answers are pre-scored asynchronously by a dedicated **AI microservice** (Spring AI + Ollama).

### Key Features
*   **Role-Based Access Control**: Three roles — Administrator, Department Manager, and Employee — with JWT + Redis session validation at the gateway.
*   **Training & Learning Center**: Managers publish DOC / LINK / VIDEO materials; employees complete learning under progress rules (e.g. video ≥ 95%, document dwell ≥ 60s).
*   **Online Assessment**: Template-based paper generation by job level, timed exams, draft save, objective auto-grading, and anti-cheat (screen-switch / blur / route-leave → warn then invalidate).
*   **AI Pre-review & Human Appeal**: OpenFeign calls `oa-ai-service` for qualitative scoring comments; employees may appeal (max twice; second appeal requires a different manager); graceful degradation when AI is offline.
*   **Analytics & Archives**: Manager dashboards (completion rate, score distribution via ECharts); Elasticsearch full-text search over graded/cheated archives.
*   **Admin Console**: User/department management, question bank (manual + Excel import), company-wide performance export, operation logs.
*   **API Gateway**: Unified entry (`:8088`) with JWT auth, CORS, login rate limiting, and service discovery via Nacos.

### Technology Stack

| Component | Specification |
|-----------|---------------|
| **Frontend** | Vue 3, Vite 5, Element Plus, ECharts, Axios, Vue Router |
| **API Gateway** | Spring Cloud Gateway (`gateway-service`, port **8088**) |
| **Main Backend** | Spring Boot **3.2.0** + Spring Cloud **2023.0.0** (`performance-service`, port **8080**) |
| **AI Service** | Spring Boot **3.4.x** + Spring AI (`oa-ai-service`, port **8081**) |
| **Language** | Java **21** |
| **ORM** | MyBatis-Plus |
| **Database** | MySQL 8.0+ (`performance_db`) |
| **Middleware** | Nacos 2.x, Redis 6+, Elasticsearch 8.x (optional for archive search) |
| **AI Runtime** | Ollama (optional, e.g. `qwen2.5:3b` on `:11434`) |
| **Build** | Maven 3.8+ / npm |

### Repository Structure
```text
PNS/
├── jixiaotong-frontend/     # Vue3 SPA (dev port 5173, proxies /api → 8088)
├── gateway-service/         # Spring Cloud Gateway (JWT, rate limit, routing)
├── jixiaotong-backend/      # performance-service (core business APIs)
│   ├── sql/schema.sql       # Schema + demo seed data
│   └── nacos/*.yaml         # Nacos config templates
├── oa-ai-service/           # Subjective AI evaluation microservice
└── doc/                     # Requirements, design, tests, deployment manuals
```

Main backend package layout (`jixiaotong-backend`):
```text
com.jixiaotong.performance
├── config / security / interceptor
├── controller          # REST API
├── service / impl      # Business logic
├── mapper              # MyBatis-Plus
├── entity / dto
├── feign               # Client to oa-ai-service
├── es / listener       # Elasticsearch archive sync
└── common / util / exception
```

### Architecture (Local Dev)
```text
Browser → http://localhost:5173
            │  /api
            ▼
     gateway-service :8088  ──lb──►  performance-service :8080
                                            │ Feign
                                            ▼
                                      oa-ai-service :8081  →  Ollama :11434
Depends on: MySQL :3306 | Redis :6379 | Nacos :8848 | ES :9200 (optional)
```

### Installation & Deployment

#### 1. Prerequisites
*   **JDK 21**
*   **Maven 3.8+**
*   **Node.js 18/20 LTS** + npm
*   **MySQL 8.0+**
*   **Redis**, **Nacos 2.x** (standalone)
*   **Elasticsearch** (required for archive search demos)
*   **Ollama** (optional; required for live AI comments)

#### 2. Setup Guide

**Step 1: Obtain the source**
```bash
git clone <repository-url>
cd PNS
```

**Step 2: Middleware & database**
1. Start Nacos in standalone mode (`startup.cmd -m standalone` / `startup.sh -m standalone`).
2. Publish Nacos configs from templates:
   *   `jixiaotong-backend/nacos/performance-service.yaml`
   *   `gateway-service/nacos/gateway-service.yaml`
3. Start Redis (and Elasticsearch / Ollama if needed).
4. Create database `performance_db` (utf8mb4) and execute:
   *   `jixiaotong-backend/sql/schema.sql`
5. Align MySQL credentials in Nacos YAML / `application.yml` with your local instance.
   *   **Do not** set Nacos client `username`/`password` if local Nacos auth is disabled.

**Step 3: Start services (order matters)**
```text
MySQL → Nacos → Redis → (ES) → (Ollama) → oa-ai-service → performance-service → gateway-service → frontend
```

```bash
# AI service
cd oa-ai-service
mvn spring-boot:run

# Main backend
cd jixiaotong-backend
mvn "-DskipTests" spring-boot:run

# Gateway
cd gateway-service
mvn "-DskipTests" spring-boot:run

# Frontend
cd jixiaotong-frontend
npm install
npm run dev
```

**Step 4: Access**
*   Web UI: http://localhost:5173/login
*   Gateway: http://localhost:8088
*   Nacos console: http://localhost:8848/nacos (`nacos` / `nacos`)

#### Demo accounts
Password for all seeded users: **`123456`** (stored as BCrypt).

| Username   | Role     | Notes                |
|------------|----------|----------------------|
| `admin`    | ADMIN    | System administrator |
| `manager`  | MANAGER  | R&D department       |
| `manager2` | MANAGER  | Product department   |
| `employee` | EMPLOYEE | Primary employee     |

### Documentation
Detailed Chinese docs live under `doc/`.

For troubleshooting and full Windows walkthrough, see **`doc/项目运行部署手册.md`**.

---

<a name="chinese"></a>
## 🇨🇳 中文说明

### 项目简介
**绩效通（Jixiaotong）** 是一套面向企业员工培训与智能绩效考核的微服务 Web 系统，覆盖 **学 → 考 → 评 → 管** 全流程：SOP 资料学习、在线组卷考试、客观题自动批改、主观题 AI 预审、员工申诉与经理复核、统计看板及历史档案检索。

系统采用前后端分离架构：Vue 3 单页应用经统一网关访问主业务服务；主观题由独立 **AI 微服务**（Spring AI + Ollama）异步评估，服务不可用时支持降级，不阻塞交卷。

### 核心功能
*   **三角色权限控制**：管理员 / 部门经理 / 普通员工；网关侧 JWT + Redis 双校验，剥离伪造用户头。
*   **培训学习中心**：经理发布 DOC / LINK / VIDEO；员工按规则完成学习（视频进度 ≥ 95%，文档/链接停留 ≥ 60 秒）。
*   **在线考核**：按职级自动组卷、限时作答、草稿暂存、客观题自动批改；切屏/失焦/离页防作弊（首次警告，再次记作弊零分）。
*   **AI 预审与申诉复核**：OpenFeign 调用 `oa-ai-service`；每卷最多申诉 2 次，二次须换经理；AI 宕机可降级提示。
*   **统计与归档**：经理端 ECharts 看板（完成率、分数分布等）；Elasticsearch 对已归档/作弊卷全文检索。
*   **管理端能力**：用户与组织、题库（含 Excel 导入）、公司绩效导出、操作日志。
*   **统一 API 网关**：入口端口 **8088**，提供鉴权、CORS、登录限流与 Nacos 服务发现路由。

### 技术架构

| 组件 | 规格说明 |
|------|----------|
| **前端** | Vue 3、Vite 5、Element Plus、ECharts、Axios、Vue Router |
| **API 网关** | Spring Cloud Gateway（`gateway-service`，端口 **8088**） |
| **主业务后端** | Spring Boot **3.2.0** + Spring Cloud **2023.0.0**（`performance-service`，端口 **8080**） |
| **AI 服务** | Spring Boot **3.4.x** + Spring AI（`oa-ai-service`，端口 **8081**） |
| **开发语言** | Java **21** |
| **持久层** | MyBatis-Plus |
| **数据库** | MySQL 8.0+（库名 `performance_db`） |
| **中间件** | Nacos 2.x、Redis 6+、Elasticsearch 8.x（档案检索相关场景） |
| **AI 运行时** | Ollama（可选，如 `qwen2.5:3b`，端口 11434） |
| **构建工具** | Maven 3.8+ / npm |

### 仓库结构说明
```text
PNS/
├── jixiaotong-frontend/     # Vue3 前端（开发端口 5173，/api 代理至 8088）
├── gateway-service/         # 统一网关（JWT、限流、路由）
├── jixiaotong-backend/      # 主业务服务 performance-service
│   ├── sql/schema.sql       # 建表与演示数据
│   └── nacos/*.yaml         # Nacos 配置模板
├── oa-ai-service/           # 主观题 AI 评估微服务
└── doc/                     # 需求、设计、测试、部署等文档
```

主业务包结构（`jixiaotong-backend`）：
```text
com.jixiaotong.performance
├── config / security / interceptor
├── controller          # 接口层
├── service / impl      # 业务逻辑
├── mapper              # 数据访问（MyBatis-Plus）
├── entity / dto
├── feign               # 调用 oa-ai-service
├── es / listener       # ES 归档同步
└── common / util / exception
```

### 本地架构示意
```text
浏览器 → http://localhost:5173
            │  /api
            ▼
     gateway-service :8088  ──lb──►  performance-service :8080
                                            │ Feign
                                            ▼
                                      oa-ai-service :8081  →  Ollama :11434
依赖：MySQL :3306 | Redis :6379 | Nacos :8848 | ES :9200（可选）
```

### 部署指南

#### 1. 环境要求
*   **JDK 21**
*   **Maven 3.8+**
*   **Node.js 18/20 LTS** 与 npm
*   **MySQL 8.0+**
*   **Redis**、**Nacos 2.x**（单机模式）
*   **Elasticsearch**（演示档案检索时需要）
*   **Ollama**（可选；需要真实 AI 评语时启动）

#### 2. 安装步骤

**第一步：获取代码**
```bash
git clone <repository-url>
cd PNS
```

**第二步：中间件与数据库**
1. 以 standalone 模式启动 Nacos。
2. 在 Nacos 控制台发布配置（使用仓库内模板）：
   *   `jixiaotong-backend/nacos/performance-service.yaml`
   *   `gateway-service/nacos/gateway-service.yaml`
3. 启动 Redis（以及按需启动 Elasticsearch / Ollama）。
4. 创建库 `performance_db`（utf8mb4），执行：
   *   `jixiaotong-backend/sql/schema.sql`
5. 将 Nacos YAML / `application.yml` 中的数据库账号密码改成本机实际值。
   *   本地 Nacos **未开鉴权**时，客户端配置中不要填写 `username`/`password`，否则易报 `User nacos not found`。

**第三步：按顺序启动服务**
```text
MySQL → Nacos → Redis →（ES）→（Ollama）→ oa-ai-service → performance-service → gateway-service → 前端
```

```bash
# AI 服务
cd oa-ai-service
mvn spring-boot:run

# 主后端
cd jixiaotong-backend
mvn "-DskipTests" spring-boot:run

# 网关
cd gateway-service
mvn "-DskipTests" spring-boot:run

# 前端
cd jixiaotong-frontend
npm install
npm run dev
```

**第四步：访问地址**
*   前端入口：http://localhost:5173/login
*   网关：http://localhost:8088
*   Nacos 控制台：http://localhost:8848/nacos（默认 `nacos` / `nacos`）

#### 演示账号
初始密码均为 **`123456`**（库中为 BCrypt 哈希）。

| 工号       | 角色     | 说明           |
|------------|----------|----------------|
| `admin`    | ADMIN    | 系统管理员     |
| `manager`  | MANAGER  | 研发部经理     |
| `manager2` | MANAGER  | 产品部经理     |
| `employee` | EMPLOYEE | 主测员工账号   |

### 文档索引
完整说明见 `doc/` 目录。

排障与 Windows 本地逐步操作，请优先阅读 **`doc/项目运行部署手册.md`**。

---

### License
本项目用于**课程实训**，采用 [MIT License](LICENSE) 开源许可。  
Copyright (c) 2026 绩效通（Jixiaotong）课程实训项目组。第三方依赖仍遵循其各自许可证。
