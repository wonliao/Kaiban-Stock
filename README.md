# Taiwan Stock Kanban Dashboard

台股看板式追蹤面板 - MVP 版本

## 專案概述

這是一個基於 Spring Boot 的台股追蹤系統，提供看板式介面讓使用者管理股票投資組合。

## 技術堆疊

- **後端**: Spring Boot 3.2, Java 21
- **資料庫**: PostgreSQL 15
- **快取**: Redis 7
- **建置工具**: Maven
- **容器化**: Docker & Docker Compose

## 快速開始

### 前置需求

- Java 21
- Docker & Docker Compose
- Maven 3.9+

### 本地開發環境設定

1. 啟動資料庫服務
```bash
docker-compose up postgres redis -d
```

2. 執行應用程式
```bash
./mvnw spring-boot:run
```

3. 檢查服務狀態
```bash
curl http://localhost:8080/api/health
```

### 使用 Docker 完整部署

```bash
docker-compose up -d
```

## API 端點

### 健康檢查
- `GET /api/health` - 系統健康狀態檢查

## 資料庫結構

### 核心表格
- `users` - 使用者資料
- `watchlists` - 觀察清單
- `cards` - 看板卡片
- `stock_snapshots` - 股票快照資料
- `audit_logs` - 稽核軌跡

### 預設資料
系統會自動建立：
- 管理員帳號: `admin` / `admin123`
- 預設觀察清單
- 範例股票資料 (台積電、鴻海、聯發科)

## 開發指南

### 專案結構
```
src/main/java/com/kanban/
├── config/          # 配置類別
├── controller/      # REST 控制器
├── domain/entity/   # JPA 實體
├── dto/            # 資料傳輸物件
├── repository/     # 資料存取層
└── service/        # 業務邏輯層
```

### 環境配置
- `dev` - 開發環境 (預設)
- `test` - 測試環境
- `prod` - 生產環境

## MVP 功能範圍

✅ **已完成**
- [x] 專案基礎架構
- [x] 資料庫 Schema 設計
- [x] 核心實體模型
- [x] 基本 REST API 架構
- [x] Redis 快取配置
- [x] Docker 容器化

🚧 **進行中**
- [ ] 使用者認證系統
- [ ] 股票資料整合
- [ ] 看板管理功能

📋 **待開發**
- [ ] 前端 React 應用
- [ ] 自動規則引擎
- [ ] 圖表視覺化

## 測試

```bash
# 執行所有測試
./mvnw test

# 執行特定測試
./mvnw test -Dtest=HealthControllerTest
```

## 監控

- 健康檢查: http://localhost:8080/api/health
- Actuator 端點: http://localhost:8080/api/actuator
- 應用程式日誌: `./logs/application.log`

## 貢獻指南

1. Fork 專案
2. 建立功能分支 (`git checkout -b feature/amazing-feature`)
3. 提交變更 (`git commit -m 'Add amazing feature'`)
4. 推送分支 (`git push origin feature/amazing-feature`)
5. 開啟 Pull Request

## 授權

本專案採用 MIT 授權條款。