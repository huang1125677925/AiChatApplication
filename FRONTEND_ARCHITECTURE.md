# Vue 前端架构与接口说明（App 端对齐参考）

本文档基于 `vue-frontend` 源码整理，涵盖导航栏、运行时架构、路由与页面、认证及接口依赖，供原生 App 或其他客户端复刻同等能力时使用。

---

## 1. 项目与技术栈

| 层级 | 技术 |
|------|------|
| 框架 | Vue 3（`<script setup>`）+ TypeScript |
| 路由 | Vue Router 4，`createWebHistory`，大量路由懒加载 `import()` |
| UI | Element Plus + `@element-plus/icons-vue` |
| 图表 | ECharts 6（各业务页内按需使用） |
| HTTP | 主业务：`src/services/axiosConfig.ts` 封装的 axios；部分模块用原生 `fetch` 或独立 `axios` 实例 |
| 构建 | Vite 7，`@` → `src/` |

**入口**：`src/main.ts` 注册 Element Plus、全局图标、`router`，并调用 `initAuth()`。

---

## 2. Host 与 API 基地址

前端代码里 Django 接口使用**相对路径** `/django/api/...`，实际请求发向「当前页面所在源 + 该路径」。开发态由 Vite 把 `/django` 代理到后端主机；App 或独立域名部署时必须配置**完整 Host（协议 + 域名/IP + 端口）**。

### 2.1 开发环境（`vite.config.ts`）

| 配置项 | 值 | 说明 |
|--------|-----|------|
| `server.host` | `true` | 开发服务器监听所有网卡（本机、局域网 IP 等均可访问，如 `http://192.168.x.x:5173`） |
| `server.proxy['/django'].target` | `http://47.120.53.64` | 浏览器请求 `http://<dev-host>:<port>/django/...` 时，由 Vite 转发到该后端 |
| 可选本地后端（注释中） | `http://127.0.0.1:8000` | 本地起 Django 时，将 `target` 改为该地址即可 |

**开发时等效 API 根**：若本机 Vite 为 `http://localhost:5173`，则业务请求的「逻辑后端根」为 `http://47.120.53.64`（仅 `/django` 前缀被代理）；axios 仍写 `/django/api/...`。

### 2.2 生产 / App 端

| 场景 | 建议配置 |
|------|----------|
| 与 Web 同源部署 | 由 Nginx 等将 `/django` 反代到内网 Django；客户端继续用相对路径 `/django/api/...` |
| 原生 App、跨域 H5 | 使用可配置常量，例如 **`API_BASE = "http://47.120.53.64"`** 或 **`https://<你的生产域名>`**，请求地址为 **`${API_BASE}/django/api/...`**（无 Vite 代理时需带 Host） |
| `/api/sh-a/...` 系列 | 仓库内未在 Vite 配置 `proxy`；需单独配置网关或 `API_BASE`，与 Django 可能不是同一 Host |

**注意**：`47.120.53.64` 为仓库当前 `vite.config.ts` 中的示例/默认代理目标，线上环境以实际部署为准，修改代理后请同步更新本文档或团队配置说明。

---

## 3. 整体运行时架构

```
App.vue
  └── DefaultLayout.vue（顶栏 + 子栏 + 主区 + 移动抽屉）
        └── <router-view />（各页面视图）
```

- **根组件** `App.vue` 只挂载 `DefaultLayout`，没有多套布局。
- **主内容区** 为 `el-main`，`padding: 0`，背景 `#f5f5f5`，内部由各页面自己铺卡片/表格/图表。
- **开发环境 API**：见 **第 2 节 Host 与 API 基地址**；前端代码统一写相对路径 `/django/api/...`，由 Vite 代理到实际后端 Host。

---

## 4. 导航栏与信息架构

### 4.1 顶栏（`src/layouts/DefaultLayout.vue`）

- **左侧**：点击标题「股票分析系统」→ `router.push('/')`。
- **右侧（桌面，宽度 ≥ 768px）**
  - `el-menu` 横向、`router` 模式；四个一级能力使用 **Popover 实现的 Mega Menu**（悬停展开多列链接）。
  - **用户区**：未登录显示「登录 / 注册」；已登录为下拉：个人中心、（管理员）邀请码生成、修改密码（当前为占位提示）、退出登录。

### 4.2 四个一级能力域与 Mega Menu

菜单数据来自 `menuItems` 计算属性；子链接在 `*MegaMenuSections` 中配置。

| 顶栏名称 | 逻辑 path（分组键 / `hasMegaMenu`） | 说明 |
|----------|--------------------------------------|------|
| 股市基本面 | `/market-fundamentals` | 大盘、新闻、互动易等（**非路由表中的真实页面 path**，仅作导航分组） |
| 行业/指数/ETF | `/etf` | 行业热力、申万、ETF 等（**同上，虚构分组前缀**） |
| 股票综合 | `/stock-picker` | 与路由父级 path 一致，但子链接多为 `/stock-*`、`/analysis/*` |
| 量化分析 | `/quant` | 与路由父级 path 一致 |

`hasMegaMenu` 中还包含 `'/analysis'`，但当前 `menuItems` 无 `path: '/analysis'` 项，该分支**未被使用**。

**高亮说明**：Mega 触发器用 `item.path` 与 `$route.path.startsWith` 判断；真实业务 URL 多为 `/analysis/...`，与 `/etf` 等前缀不一致，实际子链高亮更多依赖 `$route.path === link.path`。App 端建议用 **「模块 ID + 子页面 path」** 显式映射。

### 4.3 子栏（仅桌面）

- **面包屑**：`route.matched` 中带 `meta.title` 的记录 → `el-breadcrumb`。
- **风险提示** + **竖向轮播**（投资理念文案，组件内本地数组 `quotes`）。

### 4.4 移动端（宽度 < 768px）

- 顶栏：标题 + 菜单按钮 → **全屏抽屉** `el-drawer`。
- 纵向 `el-menu`：Mega 区为 `el-sub-menu` + `el-menu-item-group`；底部为登录/注册或用户操作。

### 4.5 未出现在顶栏但已注册的路由

以下在 `router/index.ts` 中存在，但**不在**上述四个 Mega Menu 中完整展示（部分靠页面内链接或直达 URL）：

- 首页 `/`
- `/portfolio` 投资组合
- `/forum/*` 论坛
- `/strategy/*`（与 Mega 中部分「RPS」类链接路径可能不同，见路由表）
- `/analysis/*` 下技术分析、基本面、趋势、`stock/:code` 等多数子页
- `/stock-viewer`、`/stock-kline`（Mega「股票综合」未列出）
- `/industries`、`/industries/:industry`（Mega 中有「单一行业分析」入口）

---

## 5. 路由与页面实现

### 5.1 全局路由守卫（`src/router/index.ts`）

- **标题**：`document.title = meta.title + ' - 股票分析系统'`。
- **登录**：默认**所有路由需要登录**，除非 `meta.requiresAuth === false`。未登录 → `/login?redirect=原路径`。
- **管理员**：`meta.requiresAdmin === true`（如 `/settings`）时读取 `localStorage` 的 `user`，要求 `is_admin`。

### 5.2 路由与视图文件对照（摘要）

| 路径模式 | 视图组件（`src/views/...`） |
|----------|----------------------------|
| `/login`, `/register`, `/reset-password` | `auth/*` |
| `/` | `HomeView.vue` |
| `/industries`, `/industries/:industry` | `industry-stock-data/IndustryAnalysis.vue`, `analysis/IndustryDetail.vue` |
| `/stock-viewer`, `/stock-kline` | `analysis/StockPickerView.vue`, `indival_stock_data/StockKLineView.vue` |
| `/backtest`, `/backtest-strategy`, `/backtest-history`, `/strategy-list`, `/backtest-result/:taskId` | `quant/*` |
| `/etf-system` | `etf-investment-system/EtfSystemView.vue` |
| `/analysis/*` | `analysis/`、`markt_data/`、`industry-stock-data/`、`etf/` 下各页（以 router 中 `import()` 为准） |
| `/personal/holdings` | `personal-center/HoldingsView.vue` |
| `/strategy/index-rps`, `/strategy/historical-rps` | `strategy/IndexRpsView.vue`, `strategy/HistoricalRpsView.vue` |
| `/ml/index-prediction-validation` | `ml/MlIndexPredictionValidation.vue`（`requiresAuth: false`） |
| `/forum/posts`, `/forum/posts/:id` | `forum/ForumListView.vue`, `forum/PostDetailView.vue` |
| `/portfolio`, `/settings` | `PortfolioView.vue`, `SettingsView.vue` |
| `/stock-list`, `/stock-realtime/:code?`, `/stock-history/:code?`, 等 | `indival_stock_data/*`（见 `router/index.ts`） |

完整列表以 `src/router/index.ts` 为准。

### 5.3 典型页面实现模式

- **列表 + 筛选**：Element `el-card`、`el-form`、`v-loading`，在 `onMounted` / `watch` 中调用 `src/services/*Api.ts`。
- **图表**：ECharts 挂载 DOM `ref`，`setOption`；部分封装为 `src/components/*.vue`。
- **量化回测**：创建任务 → 轮询 `status` → 拉取 `result` / `observer` / `raw-indicator`（见 `quantBacktestApi.ts`）。
- **论坛**：`forumApi.ts` 列表、详情、发帖、评论、删除。

### 5.4 特殊页面说明

- **`HomeView.vue`**：功能迭代列表，调用 `toolsApi.getGitInfo`（见接口节）。
- **`PortfolioView.vue`**：投资组合 UI 为主，未发现引用 `services` 下 Django API（若对齐 App 需单独确认产品是否接后端）。
- **`SettingsView.vue`**：管理员邀请码管理，使用 `userApi` 的 invitation 接口。

---

## 6. 认证与状态

| 项 | 说明 |
|----|------|
| 存储 | `localStorage`：`token`、`user`（JSON，含 `is_admin` 等） |
| 逻辑 | `src/services/auth.ts`：`login`、`register`、`logout`、`initAuth`、`isAuthenticated()` |
| 请求头 | `axiosConfig`：`Authorization: Bearer <token>`；从 Cookie 读 `csrftoken` → `X-CSRFToken`；`withCredentials: true` |
| 响应 | 业务层约定 `{ code, message, data? }`；`code === 200` 成功；`401` 清理本地并整页跳转 `/login` |

App 若不用浏览器 Cookie，需与后端约定 CSRF / Session 策略或改为纯 JWT。

---

## 7. 网络层与接口依赖

### 7.1 两套 HTTP 根路径

| 类型 | 前缀 | 说明 |
|------|------|------|
| Django 主 API | `/django/api/...` | 使用 `axiosConfig`；开发环境由 Vite `proxy` 转发 |
| 其他 | `/api/...` | `marketApi.ts`、`accountApi.ts`、`stockApi.ts` 等；**默认 Vite 未代理 `/api`**，部署依赖网关 |

### 7.2 Django API 路径清单（按域）

**用户** — `userApi.ts`：`/django/api/user/register/`、`login/`、`invitation/validate/`、`info/`、`logout/`、`reset-password/`、`invitation/`（GET/POST）

**个人持有/关注** — `personalHoldingsApi.ts`：`/django/api/personal/holdings/`（GET/POST）、`/django/api/personal/holdings/{id}/`（DELETE）

**论坛** — `forumApi.ts`：`/django/api/forum/posts/`、`posts/{id}/`、`posts/create/`、`posts/{id}/delete/`、`posts/{id}/comment/`

**新闻** — `newsApi.ts`：`/django/api/news/`、`/{id}`、`/latest`、`/search`；`newsWordcloudApi.ts`：`/django/api/news/wordcloud/`

**个股** — `individualStockApi.ts`、`stockHistoryApi.ts`、`strategyResultApi.ts`、`stockTagApi.ts`：  
`/django/api/individual_stock/stocks/...`、`dc-concepts/`、`balance-sheets/`、`income-statements/`、`cash-flow-statements/`、`daily/correlation/`、`daily/volatility/`、`strategy-results/`、`stock-tags/` 等

**行业 / 板块** — `industryApi.ts`、`industryAnalysisApi.ts`、`industry-heatmap.ts`、`industry-fund-flow.ts`：`/django/api/stock/...`；`industryApi.ts` 另含 `/django/api/index/sw-valuation-analysis/`

**指数** — `indexBasicApi.ts`、`indexDailyApi.ts`、`indexDailybasicApi.ts`、`swIndexClassifyApi.ts`：`/django/api/index/...`

**大盘** — `marketFundFlowApi.ts`、`marketRiseFallRatioApi.ts`、`marketService.ts`、`marketIndexApi.ts`：`/django/api/market/...`

**策略 / 行业分析** — `strategyApi.ts`、`strategyBreadthApi.ts`、`industry-turnover-percentile.ts`、`industryFundFlowCorrelationApi.ts`、`marketBreadthAnalysisApi.ts`、`strategyIndexAnalysisApi.ts`、`mlApi.ts`：`/django/api/strategy/...`

**ETF** — `etfApi.ts`：`/django/api/etf/basic/`、`daily/`、`daily/latest/`、`daily/correlation/`、`daily/volatility/`

**量化** — `quantBacktestApi.ts`、`quantStrategyApi.ts`：  
`/django/api/quant/strategies/`、`backtest/create/`、`backtest/{taskId}/run/`、`status/`、`history/`、`result/`、`observer/`、`raw-indicator/`

**任务型 tasks** — `/django/api/tasks/`：`ccass-hold`、`ccass-hold-detail`、`hm-detail`、`cyq-perf`、`hk-hold`、`stock-hsgt`、`hsgt-top10`、`broker-recommend`、`ah-comparison`、`limit-step`、`dc-daily`、`dc-index`、`irm-qa-sh`、`irm-qa-sz` 等（详见各 `*Api.ts` 文件注释）

**AI** — `aiApi.ts`：`POST /django/api/ai/analyze/`

**工具** — `toolsApi.ts`：`/django/api/tasks/git-info/`

### 7.3 `/api` 路径（非 Django 前缀示例）

- `marketApi.ts`：`/api/sh-a/stock/000001/index_zh_a_hist`
- `accountApi.ts`：`/api/sh-a/stock/account/statistics`
- `stockApi.ts`：`/api/sh-a/stock/{code}/...`、`/api/sh-a/realtime`

App 仅接 Django 时，需确认这些能力是否由后端聚合或单独部署。

---

## 8. App 端实现建议

1. **导航**：用「模块 → 子功能」映射四个 Mega Menu + 隐藏路由（论坛、投资组合、策略、技术分析等），避免依赖 `/etf` 等虚构 path 做路由匹配。
2. **鉴权**：持久化 token；请求头携带 `Authorization: Bearer`；按后端要求处理 CSRF/Cookie。
3. **Host / Base URL**：与第 2 节一致，使用可配置 **`API_BASE`（协议 + Host + 端口）**；Django 接口为 **`${API_BASE}/django/api/...`**，`/api/sh-a/...` 系列单独配置 Host（见第 7 节）。
4. **图表与表格**：对齐同一接口字段；移动端可选用原生图表或 WebView 承载 H5 图表页。
5. **管理员能力**：与 Web 共用邀请码相关接口（`userApi` invitation）。

---

## 9. 关键文件索引

| 文件 | 作用 |
|------|------|
| `src/App.vue` | 根布局入口 |
| `src/layouts/DefaultLayout.vue` | 顶栏、Mega Menu、面包屑、移动端抽屉 |
| `src/router/index.ts` | 全部路由与守卫 |
| `src/services/axiosConfig.ts` | axios 实例、Token、CSRF、统一错误与 `code` 处理 |
| `src/services/auth.ts` | 登录态与本地用户缓存 |
| `src/services/*.ts` | 按领域封装的 API |
| `vite.config.ts` | 开发服务器 `host`、将 `/django` 代理到后端 **Host**（见第 2 节） |

文档生成自仓库内源码；接口以后端实际部署为准，若有变更请以 `src/services` 内路径为准同步更新本文档。
