# 媒体、通知与连接端点关联

## 目标

在用户显式授权后，把连接级元数据与媒体播放、完整通知字段、屏幕状态和前台 App 时间线关联，区分前台播放、锁屏后台播放、缓存播放、疑似预加载、广告和统计上报。默认不解密 HTTPS、不要求 Root，不保存网络包负载。

## 权限与开关

- “媒体活动访问”是可选授权入口，使用 Android 通知使用权读取其他 App 的活动媒体会话与通知。
- 未授权时，诊断继续运行，并退化为域名、IP、端口、流量、屏幕和前台状态判断。
- 已授权时，增加媒体状态和完整可见通知字段作为证据；权限在运行中被撤销时记录缺失原因。
- 连接级采集仍是独立可选开关；严格模式必须启用，标准模式可以关闭。

## App 分类

### 网络参与 App

满足任一条件：

- 本地 VPN 捕获到该 App 的连接；
- Android 应用流量统计记录到该 App 的移动数据流量。

保存完整连接、流量、媒体、通知和前后台证据。

### 媒体相关 App

- 没有同期网络流量，但 Android 活动媒体会话显示正在播放、缓冲、暂停或最近停止。
- 包括播放本地缓存视频、音乐、有声内容或后台音频。

保存完整媒体事件和媒体通知，并明确标注“未发现同期网络流量”。

### 场景背景 App

- 只在诊断期间作为前台 App 出现，但没有网络和媒体活动。

只保存包名、应用名称和前台时间段，不保存完整通知，也不列为流量使用者。该信息只用于判断其他 App 当时是前台还是后台。

## 通知字段

对于网络参与 App 和媒体相关 App，保存 Android 实际提供的全部可读取字段，包括：

- 包名、应用名称、通知 key、通知 ID、tag；
- 创建、更新、移除时间；
- channel、category、group、排序 key；
- title、text、bigText、subText、summaryText、infoText、ticker；
- conversationTitle、messages 等系统 extras 中可序列化的文本；
- 是否为 ongoing、media style、group summary；
- 与媒体会话的关联标识。

自定义 RemoteViews、应用未暴露字段或系统脱敏内容可能无法读取；报告必须区分“字段为空”和“权限/系统未提供”。

诊断运行期间可以暂存通知事件以等待确认 App 是否参与；Run 结算后删除不属于网络参与 App或媒体相关 App 的暂存记录，导出报告不得包含无关 App 通知。

## 媒体字段

保存 Android 实际提供的：

- 包名、应用名称和媒体会话标识；
- `PLAYING`、`PAUSED`、`BUFFERING`、`STOPPED` 等播放状态；
- 状态变化时间、播放位置、播放速度；
- title、displayTitle、artist、album、duration、mediaId；
- 媒体通知是否存在。

只读取系统媒体会话和通知数据，不录制声音、屏幕或视频内容。

## 域名与端点

- 捕获可见 DNS 查询与响应，建立目标 IP 到域名的时间受限映射。
- 在可行时解析明文可见的 TLS ClientHello SNI；不解密后续 HTTPS 内容。
- QUIC、加密 DNS、ECH 或应用自定义协议无法直接取得域名时，使用 DNS/IP关联，并明确降低证据等级。
- 连接记录增加 hostname、hostnameSource 和 endpointCategory。
- 初始分类至少包含：`VIDEO_CDN`、`ADVERTISING`、`ANALYTICS`、`API`、`PUSH`、`OTHER`、`UNKNOWN`。
- 分类表按域名后缀和可解释规则维护；报告展示命中依据，不把未知 IP 强行分类。

## 行为关联

每个连接窗口综合：

- 屏幕锁定或亮屏；
- 前台 App；
- 连接拥有者；
- 媒体播放状态；
- 通知状态；
- 域名类别；
- 流量大小、持续时间和突发模式。

输出证据级结论：

- `LIKELY_FOREGROUND_PLAYBACK`
- `LIKELY_BACKGROUND_PLAYBACK`
- `LIKELY_CACHED_PLAYBACK`
- `POSSIBLE_PRELOAD`
- `POSSIBLE_ADVERTISING`
- `POSSIBLE_ANALYTICS`
- `UNCLASSIFIED`

每条结论必须附带原因列表和置信等级；没有媒体授权时不得输出依赖媒体状态的高置信结论。

## 推送通知归属

- 服务器推送可能通过小米推送、Google Play 服务等公共进程的长期连接到达，网络流量可能归属于推送服务而不是目标 App。
- 目标 App 发布通知但没有直接连接时，通知本身不能证明目标 App 使用了网络。
- 报告将“通知发布者”和“承载网络连接的推送服务”分别记录；只有存在可解释关联时才标注可能的推送链路。
- 本地定时通知、短信和运营商信令不自动当作普通互联网流量。

## 报告

ZIP 新增：

- `media_events.csv`
- `notification_events.csv`
- `endpoint_observations.csv`
- `activity_context.csv`
- `classifications.csv`

HTML 增加：

- 权限与采集完整性；
- 媒体播放时间线；
- 相关 App 完整通知；
- 域名/端点分类；
- 行为分类、置信等级和原因；
- 计划采样与实际采样延迟。

JSON schemaVersion 递增，并保持旧报告导入兼容。

## 边界

- 不解密 HTTPS；
- 不使用 Root、Frida、Xposed 或证书中间人；
- 不保存网络包负载、请求正文、账号凭证或视频二进制内容；
- 允许保存用户已明确授权的媒体元数据和相关 App 通知完整字段；
- Android 同时只能有一个 VPN，启用连接级采集时必须关闭 Clash Meta 的 VPN 模式。

## 验收

- 未授权通知使用权时诊断不失败，报告说明媒体/通知证据不可用。
- 授权后能保存测试媒体会话的状态、标题和完整通知文本。
- 网络参与 App 和媒体相关 App 的通知可导出；仅前台但无网络/媒体的 App 通知不导出。
- DNS 映射、域名分类和行为分类均有纯单元测试。
- 旧 ZIP 可以继续导入；新 ZIP 中所有文本正确转义。
- K80 真机验证通知权限、媒体状态、锁屏后台播放和连接分类。
