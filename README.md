# Taiwan Stock Kanban Dashboard

台股看板式追蹤面板 - MVP 版本

## 專案概述

這是一個基於 Spring Boot 的台股追蹤系統，提供看板式介面讓使用者管理股票投資組合。系統整合台灣證券交易所 (TWSE) MCP 資料，提供即時股票資訊與智能看板管理功能。

## 技術堆疊

### 後端
- **框架**: Spring Boot 3.2
- **Java 版本**: Java 17
- **資料庫**: PostgreSQL 15
- **快取**: Redis 7
- **資料庫遷移**: Flyway
- **建置工具**: Maven 3.9+
- **容器化**: Docker & Docker Compose

### 前端
- **框架**: React 18 + TypeScript
- **狀態管理**: Redux Toolkit
- **UI 框架**: Tailwind CSS
- **建置工具**: Vite

### 安全性
- **認證**: JWT (Access Token + Refresh Token)
- **密碼加密**: BCrypt
- **安全框架**: Spring Security 6

## 快速開始

### 前置需求

- Java 17+ (推薦使用 SDKMAN 管理)
- Docker & Docker Compose
- Maven 3.9+
- Node.js 18+ (前端開發)

### 本地開發環境設定

#### 1. 設定 Java 環境
```bash
# 使用 SDKMAN 安裝 Java 17
sdk install java 17.0.10-tem
sdk use java 17.0.10-tem

# 或設定 JAVA_HOME
export JAVA_HOME=/Users/ben/.sdkman/candidates/java/17.0.10-tem
```

#### 2. 啟動資料庫服務
```bash
docker-compose up postgres redis -d

# 確認服務狀態
docker ps
```

#### 3. 執行後端應用程式
```bash
# 前台執行（開發模式）
./mvnw spring-boot:run

# 後台執行
nohup ./mvnw spring-boot:run > logs/app.log 2>&1 &
```

#### 4. 啟動前端應用（可選）
```bash
cd frontend
npm install
npm start
```

#### 5. 檢查服務狀態
```bash
# 後端健康檢查
curl http://localhost:8081/actuator/health

# 前端（如果啟動）
open http://localhost:3000
```

### 停止服務

```bash
# 停止後端
lsof -ti:8081 | xargs kill -9

# 停止資料庫
docker-compose down

# 停止前端
lsof -ti:3000 | xargs kill -9
```

### 使用 Docker 完整部署

```bash
docker-compose up -d
```

## API 端點

### 健康檢查
- `GET /api/health` - 系統健康狀態檢查
- `GET /actuator/health` - Spring Actuator 健康檢查

### 認證 API
- `POST /api/auth/login` - 使用者登入
- `POST /api/auth/register` - 使用者註冊
- `POST /api/auth/refresh` - 刷新 Access Token
- `POST /api/auth/logout` - 使用者登出
- `GET /api/auth/me` - 取得當前使用者資訊

### 看板管理 API
- `GET /api/kanban/cards` - 取得所有卡片
- `POST /api/kanban/cards` - 建立新卡片
- `PUT /api/kanban/cards/{id}` - 更新卡片
- `DELETE /api/kanban/cards/{id}` - 刪除卡片
- `PUT /api/kanban/cards/{id}/status` - 更新卡片狀態

### 股票資料 API
- `GET /api/stocks/{stockCode}` - 取得股票詳細資訊
- `GET /api/stocks/search` - 搜尋股票

## API 回應格式

### 成功回應
```json
{
  "success": true,
  "message": "操作成功",
  "data": { ... },
  "meta": {
    "timestamp": "2025-11-04T07:11:56.894983Z",
    "traceId": "uuid",
    "version": "1.0.0"
  }
}
```

### 錯誤回應
```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "錯誤訊息",
    "hint": "解決提示",
    "traceId": "uuid",
    "timestamp": "2025-11-04T07:11:56.894983Z"
  }
}
```

## 資料庫結構

### 核心表格
- `users` - 使用者資料
- `watchlists` - 觀察清單
- `cards` - 看板卡片
- `stock_snapshots` - 股票快照資料
- `audit_logs` - 稽核軌跡

### 預設資料
系統會自動建立：
- **管理員帳號**:
  - Username: `admin`
  - Email: `admin@kanban.com`
  - Password: `admin123`
  - Role: `ADMIN`
- 預設觀察清單
- 範例股票資料 (台積電、鴻海、聯發科)

### 使用者角色
- `VIEWER` - 檢視者（只能查看）
- `EDITOR` - 編輯者（可以編輯自己的資料）
- `ADMIN` - 管理員（完整權限）

## 開發指南

### 專案結構
```
.
├── src/
│   ├── main/
│   │   ├── java/com/kanban/
│   │   │   ├── client/          # MCP 客戶端
│   │   │   ├── config/          # Spring 配置
│   │   │   ├── controller/      # REST API 控制器
│   │   │   ├── domain/entity/   # JPA 實體
│   │   │   ├── dto/            # 資料傳輸物件
│   │   │   ├── repository/     # 資料存取層
│   │   │   ├── security/       # 安全相關類別
│   │   │   └── service/        # 業務邏輯層
│   │   └── resources/
│   │       ├── application.yml  # 應用配置
│   │       └── db/migration/   # Flyway 資料庫遷移
│   └── test/                   # 測試程式碼
├── frontend/                   # React 前端應用
│   ├── src/
│   │   ├── components/        # React 元件
│   │   ├── services/          # API 服務層
│   │   ├── store/            # Redux 狀態管理
│   │   └── utils/            # 工具函數
│   └── package.json
├── docker-compose.yml         # Docker 編排設定
├── Dockerfile                # Docker 映像設定
└── pom.xml                   # Maven 專案配置
```

### 環境配置
- `dev` - 開發環境 (預設)
- `test` - 測試環境
- `prod` - 生產環境

## 功能狀態

### ✅ 已完成
- [x] 專案基礎架構
- [x] 資料庫 Schema 設計與遷移
- [x] 核心實體模型 (User, Card, Watchlist, Stock)
- [x] REST API 架構與統一回應格式
- [x] Redis 快取配置
- [x] Docker 容器化部署
- [x] JWT 認證系統 (Access + Refresh Token)
- [x] 使用者登入/註冊功能
- [x] 密碼加密 (BCrypt)
- [x] 安全事件日誌追蹤
- [x] CORS 跨域配置
- [x] Flyway 資料庫版本控制
- [x] 看板卡片 CRUD API
- [x] 股票資料快照
- [x] 稽核日誌系統

### 🚧 進行中
- [ ] 前端 React 應用整合
- [ ] TWSE MCP 股票資料即時同步
- [ ] 技術指標計算 (MA, RSI, KD)
- [ ] WebSocket 即時推送

### 📋 待開發
- [ ] 自動規則引擎
- [ ] 進階圖表視覺化
- [ ] 股票警示通知
- [ ] 多觀察清單管理
- [ ] 匯出/匯入功能
- [ ] 使用者偏好設定

## 測試

```bash
# 執行所有測試
./mvnw test

# 執行特定測試
./mvnw test -Dtest=HealthControllerTest
```

## 測試與登入

### API 測試

#### 登入測試
```bash
# 使用 Email 登入
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@kanban.com",
    "password": "admin123"
  }' | jq '.'

# 使用 Username 登入
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin123"
  }' | jq '.'
```

#### 使用 Token 訪問受保護端點
```bash
# 儲存 token（從登入回應中取得）
TOKEN="eyJhbGci..."

# 取得當前使用者資訊
curl -X GET http://localhost:8081/api/auth/me \
  -H "Authorization: Bearer $TOKEN" | jq '.'

# 取得看板卡片
curl -X GET http://localhost:8081/api/kanban/cards \
  -H "Authorization: Bearer $TOKEN" | jq '.'
```

### 預設測試帳號
- **Email**: admin@kanban.com
- **Username**: admin
- **Password**: admin123
- **Role**: ADMIN

## 監控與日誌

### 監控端點
- 健康檢查: http://localhost:8081/api/health
- Actuator 端點: http://localhost:8081/actuator/health
- Metrics: http://localhost:8081/actuator/metrics
- Prometheus: http://localhost:8081/actuator/prometheus

### 日誌位置
- 應用程式日誌: `./logs/app.log`
- Docker 日誌: `docker logs kanban-backend`
- 資料庫日誌: `docker logs kanban-postgres`

### 資料庫連線
```bash
# 使用 Docker 連線到 PostgreSQL
docker exec -it kanban-postgres psql -U kanban_user -d kanban_dev

# 常用查詢
SELECT * FROM users;
SELECT * FROM cards WHERE user_id = 'admin-user-id';
SELECT * FROM audit_logs ORDER BY created_at DESC LIMIT 10;
```

## 環境變數設定

```bash
# 資料庫
DB_HOST=localhost
DB_USERNAME=kanban_user
DB_PASSWORD=kanban_pass

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# JWT
JWT_SECRET=myVerySecretKeyForJWTTokenGenerationThatShouldBeAtLeast256BitsLong
JWT_ACCESS_EXPIRATION=3600000    # 1 hour
JWT_REFRESH_EXPIRATION=2592000000 # 30 days

# 服務端口
SERVER_PORT=8081
```

## 常見問題 (FAQ)

### Q: 如何重設資料庫？
```bash
docker-compose down -v
docker-compose up postgres redis -d
./mvnw spring-boot:run  # Flyway 會自動執行遷移
```

### Q: 登入失敗怎麼辦？
1. 確認密碼是 `admin123`
2. 檢查資料庫中的 password_hash 是否正確
3. 查看應用日誌: `tail -f logs/app.log`

### Q: 如何修改端口？
修改 `src/main/resources/application.yml`:
```yaml
server:
  port: 8081  # 改為您想要的端口
```

### Q: CORS 錯誤？
在 `SecurityConfig.java` 中調整允許的前端來源：
```java
configuration.setAllowedOrigins(Arrays.asList("http://localhost:3000"));
```

## 已知問題

- [ ] 前端 authService 需要處理統一回應格式
- [ ] Token 過期後自動刷新機制需要完善
- [ ] 稽核日誌自動歸檔功能待實作

## 變更日誌

### v1.0.0-SNAPSHOT (2025-11-04)
- ✅ 修正登入 API 回應格式，統一使用 `SuccessResponse`
- ✅ 修正密碼 hash 驗證問題
- ✅ 新增統一錯誤處理機制
- ✅ 完成 JWT 認證系統
- ✅ 新增前端 React 應用骨架

## 貢獻指南

1. Fork 專案
2. 建立功能分支 (`git checkout -b feature/amazing-feature`)
3. 提交變更 (`git commit -m 'Add amazing feature'`)
4. 推送分支 (`git push origin feature/amazing-feature`)
5. 開啟 Pull Request

### 開發規範
- 遵循 Google Java Style Guide
- 所有 API 必須返回統一格式 (`SuccessResponse` 或 `ErrorResponse`)
- 敏感操作必須記錄稽核日誌
- 新功能需要包含單元測試

## 授權

本專案採用 MIT 授權條款。

## 聯絡方式

- 專案維護者: Ben
- Issue Tracker: GitHub Issues
- 技術文件: `./docs/`