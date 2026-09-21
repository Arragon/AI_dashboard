# 订阅与额度追踪：功能与技术架构更新

> 文档版本：v0.1  
> 基线日期：2026-09-22  
> 读者：后续实现订阅、额度、提醒与采集的人  
> 配套文档：[项目架构](./architecture.md)、[项目知识](./know-how.md)、[AI 聚合平台技术架构设计书](./AI_Aggregation_Platform_Technical_Architecture.md)、[AI 聚合平台开发路线图](./AI_Aggregation_Platform_Development_Roadmap.md)

本文回答两个问题：CodexBar、OpenUsage、QuotaBar 对本项目有什么可执行的借鉴；订阅与额度追踪应如何在现有 Android 账本上更新，并与已有聚合平台设计对齐。

本文描述的是目标模型与落地顺序。它不表示这些能力已经写进代码。

---

## 0. 结论

三个参照项目都是**本机、只读、多供应商的 AI 额度仪表**。它们证明了一件本项目设计书已经指出、但当前 Android 实现还没有落地的事实：AI 订阅不能被压成「一个价格 + 一个剩余额度 + 一个重置日」。

真正能用的额度追踪至少要同时成立这七件事：

1. **一个账户有多条独立计量**。5 小时滚动窗口、每周窗口、模型单独额度、订阅内含额度、预付费余额、按量花费，是不同的表，不能合成一条百分比。
2. **计量身份和一次观测分开**。身份稳定，观测追加。失败的刷新保留上一次成功读数，并标成过期，不能把过期读数伪装成刚刚更新。
3. **窗口用时长和重置时刻描述**。标签从 `windowSeconds` 和 `resetsAt` 推导。「5 小时」是展示，不是存储。
4. **节奏可以从单次观测算出来**。窗口长度、重置时刻、当前已用比例足够判断「会不会在重置前用完」。历史曲线是增强，不是节奏的前提。
5. **订阅账单、套餐额度、本地估算花费是三本账**。它们可以在同一张账户卡上相邻出现，金额不得跨币种相加，两条可能覆盖同一流量的来源不得合并。
6. **采集是有序策略管线，解析是纯函数**。每个供应商一个描述符，多种取数方式按优先级回退，每次尝试都留下结果。解析器用录制响应钉住。
7. **手机拿不到桌面会话**。CodexBar / QuotaBar / OpenUsage 的核心数据来自 `~/.codex`、`~/.claude`、浏览器 Cookie 和本机 CLI。Android 账本不能复制这条路径。手机先做手工观测、官方 API Key、以及将来从桌面桥或 `codexbar dashboard` JSON 导入。Cookie 抓取留在用户自己的电脑上，并且保持可选。

现有 `Quota` 行仍然是手工 fallback，应保留。它要升级成「计量 + 观测」，而不是被网络采集替换掉。

---

## 1. 本项目基线

### 1.1 已经在跑的系统

当前可运行产品是 Android 本地优先订阅账本，包名 `com.subscriptiontracker`。领域层不依赖 Android 与 Room。持久化是 Room schema 1。提醒走 WorkManager。备份是经过校验的 SAF 替换式恢复。

和额度直接相关的现状：

| 概念 | 现状 | 后果 |
| --- | --- | --- |
| `Subscription` | 名称、供应商字符串、价格、币种、账单周期、状态、试用与到期日 | 供应商是自由文本，没有账户、没有能力声明 |
| `Quota` | 一条可变记录：已用、剩余、上限、百分比、是否无限、警告阈值、`updatedAt` | 一次覆盖一次。没有历史，没有来源，没有新鲜度 |
| 重置 | `RecurringEvent(QUOTA_RESET)`，锚在 `LocalDate` | 能表达「每月某日重置」，不能表达「300 分钟滚动窗口，在某个 Instant 重置」 |
| 提醒 | 按事件偏移的一次性 WorkManager；额度警告阈值存在模型里 | 阈值还不是边沿触发。没有「本周期只提醒一次」，没有节奏提醒，没有「数据过期」提醒 |
| 花费洞察 | 由账单事件投影窗口支出 | 不换汇。也还没有 token / 会话级花费 |
| 采集 | 用户手填 | 与设计书里的 Connector 尚未接通 |

这些约束继续有效，更新不得打破它们：

- 领域代码保持 Android / Room 无关。
- 日期型账单规则继续用 `LocalDate`；通知时刻与时区留在调度适配器。月/年重复必须从原始锚点计算，禁止从上一次被夹短的日期连加。
- 金额禁止跨币种相加或暗示已换汇。
- Room 变更使用显式、非破坏性迁移。
- 备份在 `BackupCodec` 校验成功且用户确认 Replace 之前不得写库。
- WorkManager 仍是 V0.1 日级提醒的调度器。精确闹钟不因额度功能被默认打开。

### 1.2 设计书里已经写对、但还没落到这个 App 的部分

[技术架构设计书](./AI_Aggregation_Platform_Technical_Architecture.md) 已把目标定成 AI 服务资产中枢，而不是聊天壳。其中与本文直接相关、应继续遵守的决定：

- `ARCH-PRINCIPLE-02` Canonical + Raw：规范化字段与原始快照同时保留。
- `ARCH-PRINCIPLE-03` 按能力而不是按「一个平台一个类」建模。
- `ARCH-PRINCIPLE-07` 不确定即可观测：`confidence`、`source_type`、`observed_at` 显式存在。
- `QuotaBucket` + `usage_snapshots` 分离。未知总量时允许只有比例，禁止编造绝对值。
- 提醒是规则求值：低额度、重置前仍有大量剩余、到期前仍有剩余、即将续费、数据过期。同一规则有 `dedupe_key`。
- v1 不做服务端代登录，不保存完整浏览器 Cookie，不以违反第三方条款的方式做大规模抓取。

设计书里的 Flutter + Go + PostgreSQL 仍是中期目标。本文的第一段实现落在现有 Kotlin / Room 账本上，使领域模型向前兼容那份设计，而不是先重写客户端。

---

## 2. 参照项目各自提供什么

三者都是 MIT 许可。可借鉴的是产品语义、数据形状和工程约束。供应商解析器、Cookie 读取和钥匙串访问是平台专用实现，不迁入本仓库。

### 2.1 CodexBar

仓库：<https://github.com/steipete/CodexBar>  
关键文档：[`docs/providers.md`](https://github.com/steipete/CodexBar/blob/main/docs/providers.md)、[`docs/cli.md`](https://github.com/steipete/CodexBar/blob/main/docs/cli.md)、[`docs/provider.md`](https://github.com/steipete/CodexBar/blob/main/docs/provider.md)

CodexBar 是 macOS 14+ 菜单栏应用，另有 Linux 桌面与独立 CLI。它注册了约 77 个供应商 ID。同一家公司会拆成多个表面，因为认证方式和额度形状不同：Codex 与 OpenAI API 分开，OpenCode 与 OpenCode Go 分开，Grok 消费者套餐与 xAI 开发者平台分开。

它对本项目最有用的不是供应商名单，而是这套采集合同：

- **描述符是唯一事实源**。一个供应商一个目录：描述符、有序抓取策略、解析、凭据适配器。菜单栏和 CLI 走同一条管线。
- **策略有种类和回退**。种类包括 `oauth`、`cli`、`web`、`api`、`local`。`auto` 的顺序按供应商写明。每次运行返回 attempts 和 errors。显式指定来源时不偷偷回退。
- **用量快照是窗口，不是一个百分比**。主窗口、次窗口、第三窗口各自带 `usedPercent`、`windowMinutes`、`resetsAt`。积分余额是旁边的 `credits`，不是第四个被捏造的窗口。
- **节奏是一等输出**。`pace` 给出阶段、相对匀速的差值、预期已用、能否撑到重置、预计耗尽秒数。文案由这些字段生成。
- **额度卡和花费账分开**。Usage & Spend 是本地估算成本历史，范围 7/30/90 天和全部（扫描窗口 365 天）。没有 token 成本合同的供应商直接不出现，避免空订阅。每种币种各自汇总。热力图上的空洞是缺口，不是零用量。
- **部分数据保持部分**。扫描失败保留上次成功模型。分页打到上限就标 partial。数值溢出则该次聚合不可用，不用后面的行补成一个假总数。
- **身份按供应商隔离**。邮箱、组织、套餐、登录方式不得串到另一个供应商的卡片上。
- **瞬态失败保留同账户、同凭据范围的上次成功用量**。缓存的测量时间不变。认证失败或账户范围失效时，不拿另一个范围的缓存来填。
- **CLI 把同一套数据暴露成脚本面**。`usage`、`cost`、`dashboard`、`serve`、`guard`、`hooks watch` 都复用描述符管线。`guard` 用剩余比例做自动化门禁，退出码稳定：0 安全，1 低于阈值，69 无法检查。`hooks watch` 是边沿触发，条件一直成立不会每个轮询都再响一次。

CodexBar 还展示了共享宿主能力应有多窄：钥匙串、按域名导入 Cookie、PTY、带域名白名单的 HTTP、WebView、本地日志成本、状态页。供应商不直接碰文件系统和浏览器内部，除非它自己就是宿主实现。

### 2.2 OpenUsage

仓库：<https://github.com/janekbaraniewski/openusage>

OpenUsage 是终端优先的本地仪表，约 36 个供应商。零配置自动发现本机 CLI 和常见 API Key 环境变量。后台 daemon 在界面关闭后仍把数据写入用户自己的 SQLite。

它补上 CodexBar 菜单栏没有强调的使用分析：

- **报表和实时额度是两种时间尺度**。`daily` / `weekly` / `monthly` 覆盖所有能报成本或 token 的供应商。`session` / `blocks` 只覆盖有逐轮数据的本地编码工具。远程 API 平台没有逐轮数据，就不编造 session。
- **5 小时计费块是一等分组**。`blocks` 给出消耗速率和投影。这和「订阅账单月」不是同一个桶。
- **自动发现与显式账户并存**。环境变量优先；也能从 OpenCode 的 `auth.json` 领养 API Key。聊天范围的 OAuth token 会被跳过，因为它们不是轮询探针要的形状。
- **浏览器会话是一种窄机制，不是通用爬虫**。只读取供应商声明的 `(domain, cookie name)`，凭据文件权限 `0600`，过期后磁贴变成 AUTH 并提示重新登录。目前完整落地的主要是 Perplexity 和 OpenCode 控制台。
- **同一流量不要记两次**。OpenCode 走 OpenRouter 时，合并视图必须知道两边可能是同一笔调用。CodexBar 对 OpenCodex 日志和原生 Codex 会话使用了同一条规则：同时存在就分列，合并会双计。

OpenUsage 自己的系统分析也是反面教材，本项目应提前避开：

- 账户配置里用同一个字段表示「CLI 路径、数据库路径、API 根地址」，语义过载。供应商专有配置应放在该供应商自己的结构里。
- 遥测事件在三处各定义一遍 token 字段。token 用量应是一个嵌入结构。
- 展示配置和数据合同堆在同一个 50 字段的 widget 上。Android 的领域模型不得携带颜色、行数、是否显示某块面板。
- 默认 HTTP 客户端没有超时。每个连接器必须使用有超时、可替换的客户端。
- 守护进程里写死 `~/.claude`、`~/.codex` 路径。路径属于供应商，不属于调度器。

### 2.3 QuotaBar

仓库：<https://github.com/QuotaBar/QuotaBar>  
架构说明：<https://github.com/QuotaBar/QuotaBar/blob/main/ARCHITECTURE.md>

QuotaBar 同样是 macOS 14+ 菜单栏应用，约 23 个供应商，SwiftUI，无第三方依赖。它比 CodexBar 小，架构文档把坑写得更具体。这些规则应直接进入本项目的领域不变量。

**窗口是结构，不是一句话。** `UsageWindow` 分开存储 `windowSeconds`、`scope`、`title`。界面把时长画成徽章（`5h`、`7d`），scope 才是说明。账户级窗口不重复写「整个账户」。知道时长的供应商必须填 `windowSeconds`。余额和账单周期没有固定时长，`title` 保留完整标签。窗口标题从接口报告的秒数推导，禁止在解析器里写死「5 小时窗口」——同一字段在别的套餐上是周窗口。

**节奏不需要历史。** 设窗口长度为 `L` 秒，已过 `E` 秒，已用比例为 `U`（0–100）。匀速预期已用是 `100 * E / L`。`U` 大于该值，就同时表示「快于匀速」和「按当前速度会在重置前用完」。这两个名字可以都留着，因为读起来不同，但测试必须钉住它们是同一个谓词。

**过期永远可见。** 刷新失败得到 `.stale(snapshot, error)`，界面出条幅。禁止折叠回 `.loaded`。用户否则会信任一个已经失效会话的数字。

**解析与网络分开。** `fetch` 负责请求，`parse` 是纯函数，用脱敏后的录制响应做测试。

**花费估算有三条容易算错的规则。**

- Claude Code 会把同一条助手回合写进多个回放了它的会话文件。去重键是 `(message.id, requestId)`。
- Codex 的 `input_tokens` 已包含 `cached_input_tokens`。缓存读取不能再加一次。
- 价格表按模型 ID 精确匹配，可去掉日期戳。禁止前缀匹配。`gpt-5.6-sol` 曾被匹配成 `gpt-5`，价格差到约三倍。

**凭据不进配置文件。** 手填 token、Cookie、API Key 只进钥匙串。配置解码按字段宽容：不认识的枚举或供应商 ID 丢掉该项，不能因为一个新字段让整份配置重置。

**告警是边沿，不是电平。** 两档阈值，外加「快用完」「余量很紧」「重置前会用完」。每次越线只提醒一次，每个重置周期也只提醒一次。窗口重置后立刻重拉该供应商；若重置前用过 90% 以上，再发一条「已重置」。

**实验性供应商必须标注。** 按控制台或 CLI 实现了、但还没用真实账号验证的集成，在设置里同样标注。未验证不得呈现为和 Codex、Claude 同级的稳定来源。

**不要替别的应用续登录，除非协议和文件格式被逐字节钉住。** QuotaBar 只在 Kimi Code 这一处代刷 refresh token，因为 access token 15 分钟就过期，且 refresh token 每次轮换。并发续期会把用户从官方 CLI 登出。本项目在没有同等测试之前，不实现任何「替 CLI 刷新 OAuth」的路径。

**后台刷新不弹系统授权框。** 需要交互的凭据读取只发生在用户按下的按钮上。定时刷新若发现需要授权，就停在「需要授权」，下一次定时器不得再弹。

### 2.4 三者怎么分工着借鉴

| 问题 | 主要参照 | 落到本项目的决定 |
| --- | --- | --- |
| 一个供应商多种取数方式、失败回退、来源标签 | CodexBar 描述符与策略管线 | `ProviderDescriptor` + 有序 `FetchStrategy`，结果带 attempts |
| 多窗口、比例、重置时刻、积分与额度分开 | CodexBar `UsageSnapshot` | `QuotaMeter` + `QuotaObservation`，缺的窗口留空 |
| 脚本、仪表盘 JSON、自动化门禁 | CodexBar CLI `dashboard` / `serve` / `guard` | 将来把桌面 CLI 的 JSON 当作导入源，而不是在手机里重写 77 个解析器 |
| 本地会话的日/周/月、5 小时块、按模型与项目拆分 | OpenUsage 报表与 daemon | 花费账与会话账独立于额度表；手机第一期只接收汇总，不扫描桌面日志 |
| 自动发现与窄 Cookie | OpenUsage | 发现逻辑放在桌面桥；Cookie 只允许单域名单名称、用户显式开启 |
| 窗口结构、节奏公式、过期态、解析测试、边沿告警、价格精确匹配 | QuotaBar | 写成领域不变量和单元测试 |
| 凭据存储与配置前向兼容 | QuotaBar + CodexBar | API Key 进 Android Keystore；备份默认不含密钥；未知字段不毁掉整库配置 |
| 菜单栏、刘海、停靠条、tmux | 三者的界面 | 不移植。本项目界面仍是 Quiet Ledger 的概览、订阅、洞察、详情 |

---

## 3. 明确留在参照项目里的部分

以下能力有人用，但不进入本项目当前架构。

- **菜单栏多表面各自绑定不同窗口。** 那是 macOS 状态项、刘海和桌面卡片的产品。本项目一个订阅详情里展示该账户的全部计量，概览只提升「最需要看的一条」。选择规则可以借鉴，呈现形式不借鉴。
- **在 Android 进程里读取 `~/.codex`、`~/.claude`、Chrome Cookie 库或其他应用的钥匙串。** 手机上这些路径不存在。即使用户把文件拷进来，也不在 App 里做通用浏览器 Cookie 导入。
- **服务端保存 Cookie 或代用户登录第三方网页。** 设计书已经排除。桌面桥若使用 Cookie，Cookie 留在那台电脑上，上传的是结构化观测。
- **一次做完 70 个供应商。** CodexBar 的名单是目录，不是里程碑。首批只做手工计量，加上 2–4 个官方 API 形状稳定、密钥由用户自己粘贴的供应商。
- **把本地日志的标价估算写成「这就是账单」。** 参照项目自己也把估算、计量花费和未计价行分开。本项目沿用同一标注。
- **前缀匹配模型价格、把缺口画成 0、跨币种加总、把两条可能重叠的日志合成一个总数。**
- **定时器里弹出凭据授权，或在未加锁的情况下代刷会轮换的 refresh token。**
- **为了看起来完整，给没有周窗口的套餐补一条周窗口，或给预付费余额补一条 5 小时额度。** CodexBar 对 xAI 预付费和 ai& 的处理就是：没有窗口就不合成窗口。

---

## 4. 功能更新

### 4.1 订阅与账户拆开

现在的 `Subscription.provider` 是字符串。更新后：

- **订阅**继续表示用户花的钱：价格、币种、账单周期、自动续费、试用、取消后仍可用至到期、暂停、归档。现有生命周期规则不变。
- **供应商**变成注册表中的稳定 ID，加上显示名。用户仍可建「未列出的供应商」，其 ID 为 `custom`，显示名自定。
- **账户**是某个供应商下的一个登录身份或一把 Key。一个订阅可以暂时对应一个账户。同一供应商的多个账户（个人 Plus 与团队 API）必须能并存，读数不得串。
- 订阅可以没有实时额度。Netflix 这类非 AI 订阅保持今天的账单账本。AI 订阅在同一条订阅上挂多条计量。

概览要回答的是状态，不是供应商字母表。每张需要关注的卡片显示：供应商、套餐或账户标签、最紧的一条计量、它的重置或到期、数据新鲜度、来源。状态至少能算出：

| 状态 | 含义 |
| --- | --- |
| `healthy` | 有新鲜观测，剩余高于警告线，节奏能撑到重置 |
| `low` | 剩余比例或剩余绝对值低于用户阈值 |
| `pace_tight` | 按当前速度会在重置前用完 |
| `reset_soon_unused` | 距重置不久，剩余仍然很多 |
| `expire_soon_unused` | 距额度或信用到期不久，剩余大于 0 |
| `renewal_soon` | 距账单日不久。这是订阅事件，不是额度 |
| `stale` | 上次成功观测还在，最近一次刷新失败或超过新鲜度窗口 |
| `unavailable` | 这个窗口本次没有被来源报告 |
| `unknown` | 从来没有过成功观测 |

`unavailable` 和 `unknown` 必须分开。前者表示「这个供应商这次没给这个窗口」，后者表示「我们还没有任何读数」。

### 4.2 一个账户多条计量

用户添加或连接器写入时，一条订阅可以挂多条计量。每条计量在界面上是一行，而不是把几个百分比收成一个环。

首期计量种类：

| 种类 | 例子 | 界面 |
| --- | --- | --- |
| `included_window` | Claude / Codex 的 5 小时与每周额度 | 比例条、重置倒计时、节奏刻度 |
| `model_window` | 某个模型单独的周额度 | 同上，scope 显示模型名 |
| `credit_balance` | OpenRouter 积分、DeepSeek 赠送余额 | 剩余绝对值和币种或积分单位。没有时长就不画节奏线 |
| `allowance` | 套餐内含的字符数、语音槽位、请求次数 | 已用 / 上限 |
| `payg_spend` | 本账单周期按量花费，对照支出上限 | 花费与上限。这是钱，不和 token 窗口相加 |
| `prepaid_balance` | 预付费现金余额 | 余额，可标出赠送与实付的分项 |
| `unlimited` | 来源明确说不限额 | 不显示百分比 |

同一账户上，订阅内含窗口和预付费余额同时存在时分两行。菜单上「点一下在已用和剩余之间切换」值得做成详情里的显示偏好，存储的仍是原始已用、剩余、上限，切换只影响展示。

重置时刻在详情里可在倒计时和绝对时间之间切换。绝对时间使用该计量声明的时区；未声明时用用户时区显示，存储用 `Instant`。

### 4.3 手工录入仍然是一等能力

每个供应商的最终 fallback 都是手填。手填表单从「一个额度」改成「一条计量的一次观测」：

- 计量名称、种类、单位、scope（可选，例如模型名或 Key 标签）
- 已用、剩余、上限三个里至少填一个；比例可由已用和上限推导
- 只知道百分比时，允许只填比例，上限留空
- 重置：无 / 固定日期时间 / 滚动窗口长度 + 下次重置时刻 / 跟随某条账单事件
- 到期时刻（信用失效）与重置时刻分开
- 警告阈值
- 备注
- 来源固定为 `user_manual`，观测时间是保存时刻

无限额度是显式开关。打开后不计算百分比，也不参与「低额度」规则。

### 4.4 新鲜度、过期与部分成功

每次刷新产生一批观测，按计量身份对齐。

- 成功：写入新观测，新鲜度为 `fresh`。
- 网络、超时、5xx：不删除上一条。最新展示等于上一条成功观测，新鲜度为 `stale`，并保存失败类别：`timeout`、`offline`、`network`、`parse`。
- 认证失败、Key 被吊销、用户关闭了该来源：不拿上一条充数。计量进入 `auth_required` 或 `disabled`。旧观测仍可在历史里看到，卡片上标明不可用。
- 多账户时，失败只影响该账户该凭据范围。
- 一个供应商部分窗口缺失：缺失窗口是 `unavailable`，兄弟窗口照常显示。
- 花费扫描不完整：已算出的金额保留，并标下限或 partial。没有覆盖的日子是缺口。

详情页始终显示「观测时间」和「来源标签」（`manual`、`api`、`import:codexbar` 等）。概览上过期数据带标记，不能只显示一个看起来正常的百分比。

### 4.5 节奏

对同时具备 `windowSeconds`、`resetsAt`、已用比例的窗口，纯函数计算：

```text
remainingSeconds = max(0, resetsAt - now)
elapsedSeconds   = windowSeconds - remainingSeconds
expectedUsed     = 100 * elapsedSeconds / windowSeconds
ahead            = usedPercent > expectedUsed
```

`elapsedSeconds` 被夹在 `0..windowSeconds`。重置时刻已过但还没有新观测时，不外推成负数节奏，状态改为 `stale` 或「已过重置点，等待刷新」。

展示三件事就够：相对匀速差多少、预期此刻用掉多少、能否撑到重置。若不能，给出按当前速度的耗尽时刻。文案由这几个字段生成，便于中英文切换。

没有窗口长度的余额不计算节奏。

历史曲线上的消耗速度是第二条算法，等快照积累后再做。它不替换上面的单点节奏，因为单点节奏在第一次成功观测后就可用。

### 4.6 提醒

在现有账单提醒之外增加额度规则。求值时机：

- 写入新观测之后立即求值。
- 时间型规则由 WorkManager 周期对账扫一遍。现有提醒对账已经会在投递后再次排队，额度规则走同一条「派生下一次、取消过期唯一工作」的路径。
- 手机上默认刷新间隔不短于 15 分钟，且受系统电池策略约束。不对标菜单栏应用的 60 秒轮询。

首期规则：

| 规则 | 触发 | 去重 |
| --- | --- | --- |
| 低额度 | 剩余比例或剩余值低于阈值 | 每个计量每个重置周期内，每次从「未越线」到「越线」一次 |
| 严重低额度 | 第二档更低阈值 | 同上，单独的 dedupe key |
| 节奏告急 | `ahead` 且预计重置前用完 | 每个重置周期一次 |
| 重置前剩余很多 | 距重置 ≤ 用户设定时长且剩余比例 ≥ 用户设定值 | 每个重置周期一次 |
| 到期前仍有剩余 | 距 `expiresAt` ≤ 设定时长且剩余 > 0 | 每个到期点一次 |
| 数据过期 | 成功观测年龄超过设定时长，或刷新连续失败 | 直到出现新的成功观测之前只发一次 |
| 窗口已重置 | 新观测的 `resetsAt` 比上一条更晚，且上一条已用 ≥ 90% | 每个新窗口一次 |
| 即将续费 | 现有账单事件 | 保持现有事件提醒 |

阈值附近抖动不得连发。实现上记录「本周期已经为该规则发送过」，而不是每次快照都比较一次就通知。用户把剩余从 19% 改到 21% 再改回 18%，只在重新越线时再发。

规则编辑时给出预览：按当前观测，这条规则现在会不会触发，若会，预计时刻是什么。预览是纯函数，不调度通知。

### 4.7 花费与用量历史

新增「使用与花费」视图，和订阅账单洞察分开。

两列可以同时出现，禁止相加：

- **来源报告的计量花费**：供应商 API 返回的周期支出、预算、积分消耗。
- **本地估算**：从会话日志按公开标价估算的金额。必须标成估算。未知模型保持未计价，不估算成 0 美元。

分组维度按数据来源有什么就显示什么：日、模型、项目、会话。远程 API 没有会话就不显示会话页。

币种各自一张合计。DeepSeek 同时给出美元和人民币时，展示来源声明的币种，不换算。

热力或日柱上，没有扫到的日子与「扫到了且用量为 0」用不同样式。0 是测量值，缺口是未知。

Android 第一期不做本机日志扫描。该视图先接收：

- 用户手填的一笔花费观测；
- 官方用量 API 的日汇总；
- 导入的 CodexBar `cost` / `dashboard` JSON，或 OpenUsage `daily --json`。

桌面桥若以后扫描日志，去重和缓存 token 规则按第 2.3 节执行，并在桥上跑完再把汇总送给手机。手机不承担数十 GB JSONL 的扫描。

### 4.8 多账户、排序与实验标记

- 同一供应商多个账户，卡片标题用账户标签，邮箱默认只显示必要部分，设置里可隐藏个人身份。
- 供应商和账户的排序是用户偏好。
- 连接器标记 `experimental`，直到有录制响应测试且至少一次真实账户验证被记录在供应商文档里。实验来源在设置和卡片上来源标签可见。
- 关闭某个来源（等价于 CodexBar 的 Cookie Off）后，请求不得发出。

### 4.9 诊断

供应商详情提供只读诊断，便于以后接 API：

- 启用的策略顺序；
- 最近一次每个策略的结果：跳过、成功、失败类别；
- 不包含密钥、Cookie、Authorization 头；
- 复制诊断文本。

这对应 CodexBar 的 verbose attempts，也对应 QuotaBar「失败也要说明是哪一种失败」。

---

## 5. 领域模型更新

新类型放在 `domain/model`，保持 `java.time` 与 `BigDecimal`。Room 实体留在 `data.database`，用映射器转换。

### 5.1 计量身份 `QuotaMeter`

稳定身份，刷新不会新建一条。

```text
QuotaMeter
- id
- subscriptionId
- accountId              可空。手填且尚未建账户时只挂订阅
- stableKey              供应商范围内稳定。例如 "session"、"weekly"、"credit"
- name
- kind                   included_window | model_window | credit_balance
                         | allowance | payg_spend | prepaid_balance | unlimited
- unit                   token | request | credit | usd | cny | character | custom 字符串
- scope                  account | workspace | model | api_key | custom
- scopeLabel             可空。模型名、工作区名、Key 标签
- windowSeconds          可空。滚动窗口长度
- resetPolicy            none | rolling | fixed_instant | billing_cycle | manual | unknown
- resetEventId           可空。仅 billing_cycle / 用户选择跟随时使用
- warningPercent         可空
- severePercent          可空
- createdAt
- archivedAt             可空。来源不再报告该窗口时归档，不物理删除历史
```

`stableKey` 由供应商描述符定义。手填由用户名称生成，并允许改显示名而不换 ID。

### 5.2 观测 `QuotaObservation`

只追加。展示层读取每个计量的最新一条，以及显式的新鲜度。

```text
QuotaObservation
- id
- meterId
- observedAt
- used                   可空，>= 0
- remaining              可空，>= 0
- limit                  可空，> 0
- usedRatio              可空，0..1。来源直接给百分比时存这里
- resetsAt               可空 Instant
- expiresAt              可空 Instant
- unlimited              bool
- sourceType             user_manual | official_api | oauth | cli | local_log
                         | import_file | browser_session_bridge
- sourceLabel            短标签，例如 "api"、"manual"、"codexbar-dashboard"
- freshness              fresh | stale | unavailable
- errorCategory          可空。timeout | offline | network | parse
                         | auth_required | unsupported
- confidence             high | medium | low
- rawJson                可空。规范化之后仍保留的原始片段。不得含密钥
```

百分比的单一算法：

1. 计量 `unlimited` 或观测 `unlimited`：无百分比。
2. 否则若 `used` 与 `limit` 都在：`used / limit`。
3. 否则若 `remaining` 与 `limit` 都在：`(limit - remaining) / limit`。
4. 否则若 `usedRatio` 在：用它。
5. 否则：无百分比。状态可以是 `unknown`，但剩余绝对值仍可显示。

禁止为了填满进度条而用相邻窗口的比例冒充。

现有 `Quota.percentage` 以 0–100 的 `BigDecimal` 存储。新的 `usedRatio` 用 0–1，避免和「剩余百分比」混用。展示层再乘 100。迁移旧数据时把 `percentage / 100` 写入 `usedRatio`。

### 5.3 新鲜度不是观测里的一个含糊布尔

最新展示是一个派生结构，由仓库在读取时组装：

```text
MeterReading
- meter
- latestSuccessful       可空观测
- latestAttemptAt
- freshness              fresh | stale | auth_required | unavailable | unknown
- errorCategory          可空
```

`fresh` 表示最新一次尝试成功，且年龄未超过该计量的新鲜度预算。`stale` 表示展示的数字来自更早的成功观测。认证失败不得返回 `stale`。

这个形状对应 QuotaBar 的 `.stale(snapshot, error:)`，也对应设计书「离线仍可看最后快照」。

### 5.4 节奏 `QuotaPace`

不入库。由 `MeterReading` 和 `Clock` 计算。

```text
QuotaPace
- expectedUsedRatio
- deltaRatio             usedRatio - expectedUsedRatio
- willLastToReset
- eta                    可空 Instant。willLastToReset 为 false 时给出
```

无 `windowSeconds`、无 `resetsAt` 或无已用比例时，函数返回空。调用方不显示节奏线。

### 5.5 花费账

```text
SpendSample
- id
- accountId
- periodStart
- periodEnd
- bucket                day | window | cycle
- currency              可空。未计价 token 样本没有币种
- amount                可空
- inputTokens           可空
- outputTokens          可空
- cacheReadTokens       可空
- cacheWriteTokens      可空
- reasoningTokens       可空
- requestCount          可空
- modelId               可空
- projectLabel          可空
- valuation             reported | estimated | unpriced | unmetered
- coverage              complete | partial | gap
- sourceType
- sourceLabel
- observedAt
```

汇总规则：

- 只在同一 `currency` 且同一 `valuation` 内求和。
- `gap` 不参与平均，也不显示为 0。
- `partial` 的合计标成下限。
- 两个 `sourceLabel` 被描述符声明为可能重叠时，分列，不自动合并。

### 5.6 供应商描述符

```text
ProviderDescriptor
- id                     稳定字符串。custom 除外都是代码注册的
- displayName
- experimental           bool
- capabilities           quota.pull | billing.pull | spend.pull | account.identity
- meters                 该供应商允许发出的 stableKey 列表。未列出的 key 拒绝入库
- strategies             有序 FetchStrategy
- identityFields         本供应商自己的身份字段名。禁止被别的描述符读取后展示
```

```text
FetchStrategy
- id
- kind                   manual | api | oauth | cli | local | import | web
- isAvailable(context) 
- fetch(context) -> FetchOutcome
- shouldFallback(error) 
```

```text
FetchOutcome
- attempts[]             strategyId, status, errorCategory, duration
- observations[]
- spendSamples[]
- identity               可空，且只属于该 provider id
```

策略上下文携带时钟、有超时的 HTTP 端口、凭据端口、导入字节。不携带 Android `Context`。解析函数签名是 `parse(bytes, clock) -> FetchOutcome` 的纯部分，网络在它外面。

### 5.7 与现有类型的关系

| 现有类型 | 更新后 |
| --- | --- |
| `Subscription` | 保留。增加可空 `providerId`、可空 `accountId`。自由文本 `provider` 在迁移期仍保留，作为自定义显示名 |
| `Quota` | 变成兼容读模型。新写入走 `QuotaMeter` + `QuotaObservation`。旧行迁移成一条 `kind` 待定的计量加一条 `user_manual` 观测 |
| `RecurringEvent` | 保留，继续承担账单、试用、到期、涨价、促销结束。额度重置优先用观测的 `resetsAt`。`QUOTA_RESET` 事件只用于用户明确选择「按日历重置」的计量 |
| `ReminderSettings` | 账单提醒继续用它。额度规则另建 `QuotaAlertRule`，避免把滚动窗口塞进按日的提醒偏移 |

`QuotaAlertRule` 是用户可编辑的阈值和提前量。第 4.6 节的边沿状态（本周期是否已发送）存在平台侧的小表里，不属于领域提醒文案。

### 5.8 不变量

测试必须钉住：

1. 已用、剩余为负，或上限 ≤ 0，构造失败。
2. 只给比例时，`usedPercentage` 使用比例，不编造 used 或 limit。
3. 只给剩余和上限时，已用比例是 `(limit - remaining) / limit`。
4. `unlimited` 没有比例，不触发低额度。
5. 节奏谓词 `usedRatio > elapsed / window` 与「重置前用完」等价。窗口边界、刚重置、已过重置点各有用例。
6. 刷新失败且错误是瞬态：`MeterReading.freshness == stale`，数字等于上一条成功观测，`observedAt` 不变。
7. 错误是 `auth_required`：新鲜度不是 `stale`。
8. 描述符未声明的 `stableKey` 被拒绝。
9. 两种币种的 `SpendSample` 汇总结果是两行。
10. `coverage = gap` 的样本不使该日合计变成 0。
11. 同一 `dedupe key` 在同一重置周期内第二次越线不产生新通知；数值回到阈值上方再次跌破才产生。
12. 月重置若用户选择跟随日历事件，仍走现有 `RecurrenceEngine` 锚点规则，不从 1 月 31 日连加到 3 月 28 日。

---

## 6. 采集架构更新

### 6.1 运行位置

```text
手机 App
  手工观测
  用户粘贴的 API Key → 官方只读用量/余额接口
  导入 JSON 快照
  读取已同步下来的观测

桌面桥（以后，可选进程）
  读取本机 CLI 登录文件与会话日志
  可选的窄 Cookie
  调用本机已登录的 CLI
  只上传结构化观测与花费汇总

服务端（设计书中的 Connector Worker，更后）
  官方 API、用户自备 Key
  不接收 Cookie
  不代登录
```

手机是当前唯一的运行面。桌面桥和服务端都不是本阶段的新建工程，但领域模型里的 `sourceType` 现在就留下位置，避免以后把「API 读数」和「手填」写成两种 UI 特例。

### 6.2 策略顺序

每个供应商在描述符里写死 `auto` 顺序，并写进供应商文档。通用默认是：

```text
用户显式指定的来源
  → 官方 API（用户已保存 Key）
  → 导入的最新快照（若新于上次 API 成功）
  → 手工观测
```

显式来源失败时不回退，避免「我关了 API 却仍去打网络」。`auto` 才回退。回退只发生在 `shouldFallback` 为真的错误上：缺凭据、401、404 可以换下一种；解析成功但窗口为空通常是合法结果，不算失败。

每次 `FetchOutcome.attempts` 保留在诊断里。UI 只显示最后采用的 `sourceLabel` 和失败类别。

### 6.3 首批官方 API 的选择标准

一个供应商进入 Android 内置连接器，需要同时满足：

- 用户自己持有 API Key 或 token，应用内不打开网页登录。
- 只读余额或用量端点，HTTPS，主机在描述符白名单内。
- 响应可以脱敏后做成固定测试夹具。
- 端点表达的是余额、额度或花费，而不是一次会扣费的对话补全。探测用 GET 或文档标明的只读调用。禁止用最小 chat completion 去「看看还有没有额度」，那会烧额度，也会把探针和真实用量混在一起。
- 文档写明区域主机（国际 / 中国大陆）由用户选择，默认不猜测。

按参照项目里已经反复出现、且符合上面标准的形状，优先候选是：

| 供应商 | 读什么 | 不读什么 |
| --- | --- | --- |
| DeepSeek | 余额，实付与赠送分项 | 不把余额画成 5 小时窗口 |
| Moonshot / Kimi API | 所选区域的余额 | 与 Kimi Code 的周额度 / 5 小时窗口不是同一个产品，描述符分开 |
| OpenRouter | Key 额度与积分 | 管理 Key 的账户活动是另一项能力，没有管理 Key 就不显示活动 |
| z.ai / 智谱编码套餐 | 套餐额度端点 | 主机必须是配置的 HTTPS，失败即关闭，不降级到别的主机 |

Claude、Codex、Cursor、Gemini 的高价值读数依赖 OAuth 或本机会话。它们在手机上的第一期路径是「导入桌面工具导出的 JSON」或手填，而不是内置 WebView 登录。

### 6.4 把 CodexBar 和 OpenUsage 当作导入源

这是杠杆最大的集成，因为它复用对方已经维护的解析，而不是把 Swift / Go 源码搬进来。

支持的导入文件：

- `codexbar dashboard` 写出的 dashboard JSON。
- `codexbar usage --format json` 与 `codexbar cost --format json`。
- `openusage daily --json` 一类周期报表。

导入器：

1. 识别 schema。不认识的版本拒绝整份文件并说明原因，不写入半份。
2. 映射到 `QuotaObservation` 和 `SpendSample`。缺少的窗口保持缺失。
3. `sourceType = import_file`，`sourceLabel` 带上来源命令和对方的 `source` 字段（`oauth`、`api`、`local`）。
4. 身份字段只进入对应 `providerId` 的账户。
5. 对方把估算标成估算的，这里保持 `valuation = estimated`。
6. 导入预览列出将替换哪些计量的「最新展示」，用户确认后才写。这和现有备份的 Replace 确认是同一交互原则：校验先于写入。

不把对方的专有字段（`openaiDashboard`、`antigravityPlanInfo` 等）逐个做成列。它们进入 `rawJson` 或忽略。稳定合同只认窗口、重置、积分、花费和来源。

以后若桌面桥在本机跑 `codexbar serve`，手机只在用户配置的局域网地址上拉取，并要求用户提供对方文档所要求的 bearer token。环回地址之外的明文 HTTP 不作为默认。手机不实现 CodexBar 的 serve。

### 6.5 凭据

- API Key 与导入用的 bearer token 放入 Android Keystore 支持的加密存储。
- Room、备份 JSON、诊断文本、日志、崩溃附件里不出现密钥原文。
- 备份包增加 `credentials: excluded`。恢复后相关连接器处于 `auth_required`，用户重新粘贴。
- 配置与偏好继续可以进备份。未知枚举在解码时丢弃该项并保留文件其余部分，避免以后新增供应商 ID 让旧版本把整份设置清空。
- 界面上的密钥字段只写不回显全文。

### 6.6 调度与耗电

- 自动刷新是 WorkManager 周期工作，有网络约束，默认 6 小时，用户可调到不短于 15 分钟。
- 打开订阅详情或下拉刷新是立即刷新，仍受每供应商超时限制（默认 30 秒）。
- 一个供应商失败不取消同批其他供应商。
- 连续认证失败后停止该账户的自动重试，直到用户改凭据或按下「重试」。避免把失效 Key 打到被限流。
- 后台工作不申请通知权限以外的新运行时权限。

### 6.7 解析与夹具

每个内置策略：

- `parse` 纯函数，输入是字节与时钟，输出是观测草稿。
- 测试夹具放在 `app/src/test/resources/providers/<id>/`，账户 ID、邮箱、Key 已替换。
- 窗口标题断言针对 `windowSeconds`，不断言写死的英文句子。
- 缺字段、超额数值、空数组、未知枚举都有用例。未知枚举使该字段不可用，兄弟字段仍有效。

共享宿主端口先做三个：`HttpPort`（超时、HTTPS、主机白名单）、`SecretPort`、`Clock`。不要建一个什么都能干的 `ProviderUtils`。

---

## 7. 界面更新

视觉继续遵守根目录 `DESIGN.md` 的 Quiet Ledger：纸色与炭色、单一杜松子绿强调、细分割线、等宽数字。额度条用现有强调色和中性色，不为每个供应商做一套庆祝色。参照项目的彩纸、刘海光晕、每帧动画不引入。

### 7.1 概览

在现有「需要关注」列表中加入额度状态。排序建议：

1. `auth_required`、`stale`
2. `low`、`pace_tight`
3. `expire_soon_unused`、`reset_soon_unused`
4. `renewal_soon`
5. 其余新鲜且健康的订阅不占关注位，仍在订阅列表里

一行里数字优先：剩余比例或剩余绝对值、重置倒计时、来源是否过期。供应商彩色 logo 可以后做。第一期用名称。

### 7.2 订阅详情

配额区从单行摘要改成计量列表。每行：

- 名称与时长徽章（有 `windowSeconds` 才有徽章）
- 比例条。条上的节奏刻度是一条细标记，位置等于 `expectedUsedRatio`
- 主数字，以及已用 / 上限或剩余 / 上限中的一种
- 重置或到期
- 观测时间与来源

无上限的余额不画满条。`unavailable` 行显示「本次未报告」，不显示 0%。

### 7.3 洞察

现有账单投影保留，并标明这是订阅价格的投影，不是 token 账单。

使用与花费页按币种分节，节内再分「来源报告」和「估算」。估算节的标题写明「按标价估算，不是扣款」。

### 7.4 设置

- 默认额度警告阈值与严重阈值。
- 自动刷新间隔。
- 隐藏账户邮箱。
- 已保存的 API Key：添加、替换、删除。删除后立即停止刷新。
- 导入用量快照的入口，放在现有备份附近，但文案写成「导入用量」，避免和「替换全部账本」混淆。用量导入只追加观测，不替换订阅表。

文案继续走 `values` 与 `values-zh-rCN`。领域层抛出的稳定英文校验句仍由 Compose 映射到字符串资源。

---

## 8. 持久化、迁移与备份

Room 从 schema 1 迁到 schema 2，只加表和可空列，不删旧 `quotas` 表，直到读路径完全切走并有迁移测试。

新增表：

- `provider_accounts`
- `quota_meters`
- `quota_observations`
- `quota_alert_rules`
- `quota_alert_firings`（dedupe 状态）
- `spend_samples`

迁移步骤：

1. 为每条旧 `Quota` 建一条 `QuotaMeter`，`stableKey = "legacy"`，`kind` 在 `unlimited` 时为 `unlimited`，否则为 `allowance`。
2. 把该行的 used / remaining / limit / percentage 写成一条 `QuotaObservation`，`sourceType = user_manual`，`observedAt = updatedAt`，`freshness = fresh`。
3. `resetEventId` 仍挂在计量上。
4. 旧表保留到下一版本只读。新的保存写新表。
5. 备份 codec 版本升级。旧备份仍能导入为旧配额，再走同一迁移。新备份含观测历史。密钥永远不在备份里。

观测表会增长。手机上每个计量保留最近 400 条或 400 天，取更少触发者，超出的按时间删除。聚合后的日花费样本另行保留 400 天。删除是按计量的普通清理，不做破坏性 fallback 迁移。

索引：`(meterId, observedAt)`、`(accountId, periodStart)`。

---

## 9. 对聚合平台设计书的修订建议

设计书第 5.2 节和路线图 M1 仍然成立。下面这些修订让它吸收参照项目里已经验证过的坑。建议在下一次改设计书时并入，而不是在 App 里另起一套名词。

| 设计书现状 | 修订 |
| --- | --- |
| `quota_buckets.rolling_window_seconds` 与 `reset_at` | 保留。补充 `scope`、`stable_key`、`kind`。`reset_at` 是该桶当前窗口的时刻，随观测更新；桶上的 `reset_policy` 才是规则 |
| `usage_snapshots` 只有 used / remaining | 增加可空 `used_ratio`、`resets_at`、`expires_at`、`freshness`、`error_category`。比例与绝对值都可空 |
| 快照由连接器写入后即视为当前事实 | 瞬态失败不写一条空快照。读模型返回上一条成功快照加 `stale`。认证失败写一条不含数值的失败记录，读模型不得把上一条显示成新鲜 |
| 提醒规则里的剩余比例 | 写明比例来自第 5.2 节的单一算法。规则对 `unlimited` 与 `unavailable` 不触发低额度 |
| 「消耗速度」放在历史曲线 | 分成单点节奏（无历史）和历史斜率（有快照之后）。M1 验收以前者为准 |
| Connector 的 `quota.pull` | 一个 Provider 多个 Strategy，outcome 带 attempts。能力矩阵之外，增加「允许发出的 meter key」 |
| 浏览器扩展上传结构化字段 | 与 OpenUsage 的窄 Cookie 对齐：扩展或桌面桥在本地持有 Cookie，上传字段白名单与 manifest 的 `sends` / `never_sends` 一致 |
| 数据过期规则 | 增加边沿语义和每重置周期去重，避免阈值抖动 |
| 花费 | 单列 `SpendSample`，`valuation` 与 `coverage` 必填。禁止跨币种合计。可能重叠的来源在描述符里声明 `overlapGroup`，同组不自动相加 |

路线图 M1 的验收清单建议追加：

- 一个账户至少两条窗口计量，刷新后身份不变、观测增加。
- 只报告百分比的来源不产生伪造的上限。
- 瞬态失败后界面仍显示上次数字，并标过期，观测时间不刷新成失败时刻。
- 节奏在仅有一条观测时可计算，并有边界测试。
- 导入一份不含密钥的 dashboard JSON 后，窗口与来源标签正确，未知 schema 整份拒绝。
- 低额度通知在同一重置周期内不重复。

M0 的 Flutter / Go 骨架未被本文提前。额度模型先在现有 App 里用测试钉住，再迁到服务端，避免两套语义。

---

## 10. 分阶段实施

每阶段都要能独立使用，并带测试。不把「接上 Claude OAuth」放进第一阶段。

### 阶段 A：多计量、观测、节奏、边沿提醒

只做手填和迁移。没有网络权限需求。

交付：

- 第 5 节的领域类型与第 5.8 节测试。
- schema 2 迁移与备份往返测试。
- 详情页多行计量、概览关注状态、规则预览。
- 中英文案。

完成标准：旧数据升级后数字与现在一致；新建两条窗口后，重置时刻和节奏正确；把剩余改到阈值下只通知一次。

### 阶段 B：两个只读 API 连接器

建议 DeepSeek 余额与 OpenRouter Key/积分。数量少，是为了把描述符、超时、密钥存储、过期态和夹具测试走通。

完成标准：夹具测试不访问网络；密钥不在备份与日志中；401 进入 `auth_required` 并停止自动重试；超时保留上次成功观测为 `stale`。

### 阶段 C：JSON 导入

支持 CodexBar dashboard / usage JSON 的一个已钉住 schema 版本，以及一种 OpenUsage 周期 JSON。

完成标准：预览后确认才写入；半份损坏文件不写；估算花费标成估算；与手填计量按 `stableKey` 对齐而不是复制出第二条「Weekly」。

### 阶段 D：使用与花费页

在已有 `SpendSample` 上做按币种、按日的只读视图。数据来自导入和 API 日汇总。

完成标准：两种币种两行合计；缺口日与零用量日可区分；订阅价格洞察的数字不因本页改变。

### 阶段 E：桌面桥的合同，仍可不写桥

写明桥上传的 JSON schema，与阶段 C 的导入 schema 相同。桥本身、Cookie、日志扫描都留到有桌面运行面时再做。若阶段 C 已能导入 CodexBar 输出，桥的第一版可以只是定时执行 `codexbar dashboard --output` 再把文件同步到手机，无需重写解析器。

阶段 E 之前不做：服务端 Connector Worker、浏览器扩展、代刷 OAuth、70 个供应商、会话级 MCP/项目拆分。

---

## 11. 许可与复用边界

| 项目 | 许可 | 本仓库允许的用法 |
| --- | --- | --- |
| CodexBar | MIT | 按本文复述架构思想；调用其已安装 CLI 的公开 JSON。复制 Swift 源码进本仓库时必须保留 MIT 声明，并先做一次范围审查 |
| OpenUsage | MIT | 同上。其终端界面与 daemon 实现不嵌入 Android |
| QuotaBar | MIT | 同上。节奏公式和窗口字段是思想，用 Kotlin 重写并加本项目测试 |

默认做法是重写。供应商 HTTP 路径和字段名属于各平台的接口事实，实现时对着官方文档和我们自己的脱敏夹具写解析器。不把参照仓库的夹具原样提交进本仓库，以免带入未脱敏的账户数据。

不采用的实现即使在参照项目里存在，也不构成「所以我们可以在服务端做 Cookie 登录」。设计书的数据政策优先。

---

## 12. 对现有代码的直接改动地图

阶段 A 会碰到这些位置，此处只标地图，避免做到一半才发现边界：

| 位置 | 改动 |
| --- | --- |
| `domain/model/Quota.kt` | 保留计算函数的语义，拆到 `QuotaObservation` 的比例算法。旧类型可暂时变为读取适配 |
| `domain/model/RecurringEvent.kt` | 不把滚动窗口编码成 `RecurrenceRule` |
| `data/database/*` | 新实体、DAO、schema 2、映射器 |
| `data/backup/BackupCodec.kt` | 版本号、拒绝含密钥字段、旧备份兼容 |
| `domain/reminder/*` | 额度规则的纯计划器。WorkManager 仍只接收计划结果 |
| `presentation/CoreViewModel.kt` | 新的计量与观测意图；校验英文消息与 `resolveUserMessage` 成对增加 |
| `ui/screens/CoreApp.kt` | 详情计量列表与概览关注行 |
| `app/src/test` | 比例、节奏、过期、去重、迁移、备份 |

阶段 B 才新增 `data/provider` 或 `platform/provider`。HTTP 与 Keystore 放在平台侧，解析与描述符放在可单测的非 Android 模块边界内。若暂时仍在 `app` 模块，解析代码也不得引用 `android.*`。

---

## 13. 验收总表

阶段 A 结束时：

- 一条 AI 订阅能保存 5 小时窗口和每周窗口，两条重置时刻互不影响。
- 只填百分比的计量不显示伪造的「已用 token 数」。
- 余额类计量没有节奏刻度。
- 过期展示不改写观测时间。
- 低额度、节奏、重置前剩余过多、数据过期，各自在一个周期内只提醒一次。
- 账单提醒和额度提醒可以同时存在，金额仍不跨币种相加。
- `gradlew.bat :app:testDebugUnitTest` 覆盖第 5.8 节。
- 旧用户升级后，原配额数字仍在，来源显示为手工。

阶段 B、C 结束时另加第 10 节的完成标准。真机仍需单独看通知权限、WorkManager 延迟和 SAF 导入。JVM 测试不能代替那三项。

---

## 14. 参考

- CodexBar 供应商与数据来源：<https://github.com/steipete/CodexBar/blob/main/docs/providers.md>
- CodexBar CLI、dashboard、serve、guard、hooks：<https://github.com/steipete/CodexBar/blob/main/docs/cli.md>
- CodexBar 供应商接入结构：<https://github.com/steipete/CodexBar/blob/main/docs/provider.md>
- OpenUsage：<https://github.com/janekbaraniewski/openusage>
- QuotaBar 架构与窗口/节奏/凭据规则：<https://github.com/QuotaBar/QuotaBar/blob/main/ARCHITECTURE.md>
- 本项目当前架构：[`architecture.md`](./architecture.md)
- 本项目额度中枢的既有目标：[`AI_Aggregation_Platform_Technical_Architecture.md`](./AI_Aggregation_Platform_Technical_Architecture.md) 第 5.2、7、9 节，[`AI_Aggregation_Platform_Development_Roadmap.md`](./AI_Aggregation_Platform_Development_Roadmap.md) 的 M1
