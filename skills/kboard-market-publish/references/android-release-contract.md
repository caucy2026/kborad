# Android 商城发布接口参考

来源：用户提供的 `kemi-market-publish/references/release-contract.md` 的 Android 与通用部分；不复制其它平台的上传请求体。此文件是参考合同，不是对线上当前实现的证明。

## 发布前核对官方文档

- https://kemi.newlinksz.com/kd/docs/ai-publish
- https://kemi.newlinksz.com/kd/docs/app-upload
- https://kemi.newlinksz.com/kd/docs/store-api
- https://kemi.newlinksz.com/kd/docs/app-self-update/android

## 已提供的接口线索

`UC=https://kemi.newlinksz.com/usercenter`

`KD=https://kemi.newlinksz.com/kd-api`

| 用途 | 原参考给出的接口 |
|---|---|
| 登录 | `POST {UC}/api/auth/login` |
| 分类 | `GET {KD}/api/apps/categories` |
| 应用查询 | `GET {KD}/api/apps/list?os_type=android&keyword={URL编码后的包名}&page=1&pageSize=20` |
| Android 上传 | `apk-token`、`apk-complete`；原参考没有完整给出 Android 请求体，需核对在线文档 |
| 创建 | `POST {KD}/api/apps/create` |
| 更新 | `POST {KD}/api/apps/update` |
| 商城详情 | `GET {KD}/api/store/apps/{app_id}?os=android` |
| 更新检查 | `GET {KD}/api/store/update/check?package_name={URL编码后的包名}&version_code={本地APK整数版本}&os=android` |

鉴权管理接口使用 `Authorization: Bearer {token}`。公开更新检查不能包含管理员凭证。

## 数据约束

- 身份固定为 `(com.newlink.kemi.kboard, android)`；分页查询后精确比对，防止重复记录。
- 比较依据是 APK 的整数 `versionCode`，`versionName` 仅用于展示。下一计划版本不代表高于线上版本，发布前必须实际查询。
- 上传前取最终 APK 文件长度和 SHA256；上传完成核对业务成功、HTTPS URL、大小、哈希及 Android 解析元数据。缺失关键值不能用本地猜测值掩盖服务器解析失败。
- 更新已有应用使用原 app_id，保留不属于本次修改的元数据。按线上合同提交完整记录，避免清空图标/描述/截图。
- `file_size` 使用精确字节数的十进制字符串，`file_size_bytes` 使用同一字节数的整数；不可用 MB 展示文本替代。
- `apk_sha256` 是 APK SHA256，不是签名证书指纹。两个值分别记录。
- `list_in_store` 和主动提示等状态不能因示例默认值被擅自改变。本项目每次发布的 `force_update` 以用户本次指示为准：未要求强制更新时选“可取消”，明确要求时选“强制更新”；不能仅沿用旧记录或示例默认值。上传、审核、公开上架的边界需以线上合同确认。

## 发布后验证

1. 开发者记录：唯一 app_id，正确包名/系统/版本/状态/大小/HTTPS URL/哈希。
2. 商城公开详情：正确元数据且下载按钮可用；不以开发者列表代替公开详情。
3. CDN：成功响应和正确文件大小；下载实际 APK 后重新计算大小和 SHA256，验证平台签名，再用该下载包完成设备升级测试。
4. 更新接口：旧版本返回有效更新，本次版本无更新；不存在的包和服务端错误不能被误判为可升级。HTTP 成功不等于业务成功。
5. KBoard：设置 APP 前台启动后异步自动检查，有更高版本才主动提示；忽略同一版本不重复打扰，无更新或后台失败不弹窗。关于页“当前版本”行短按可按当前检查结果跳转商城或提示已是最新版，检查失败可重试；长按约 8 秒是独立工程入口。点击更新提示只跳转 KEMI 商城中的正确应用，由用户升级。

## 当前仍需核实的合同

- Android `apk-token` / `apk-complete` 的完整 URL、请求和响应、认证权限。
- 支持详情页跳转的最低商城版本，以及 H730 预置商城是否支持该协议。
- API 级上架审核/草稿/灰度/撤回请求体仍需以执行时官方文档核实；app_id/versionCode 也必须执行时重新查询。

## 2026-09-24 商城管理界面实测

- 在既有 KBoard 应用记录的“提交新版本”页面，上传 APK 后等待服务端解析；页面显示包名、版本、SHA-256 和 CDN 地址，仍需人工核对大小及其它元数据。
- “预发布”产生“更新草稿／审核中”，当时线上版本不变；拥有待审可见权限且已登录的 PAD 商城账号可在应用详情看到“审核中”版本并在线更新。该可见性不是匿名公开发布，也不能保证所有账号或未来商城实现相同。
- “撤销提交”删除待审草稿、线上版本保持不变；“保存并发布”直接更新线上版本。实测先撤销较高版本码的测试草稿，再重新上传原正式包，服务端解析哈希一致后发布成功。下一次操作仍要确认当前页面提示和状态。

无上述合同不构造生产请求，也不把“已提取 skill”表述为客户端功能或商城发布已完成。

## 已核实的 Android 自检及商城跳转

2026-09-12 已只读取得官方正文：`https://kemi.newlinksz.com/kd-api/api/public/docs/app-self-update/android`。页面返回 HTML 框架不算读到文档正文。执行时仍需核对最新版。

- 更新接口公开、无需登录。验证 HTTP、业务 `status=200`、`data.has_update`、返回包名和更高的 `version_code`；本地版本来自已安装 APK 标准字段。
- 响应包含 `app_id`、`version_name`、`version_code`、`local_version_code`、`force_update`、`list_in_store`、`deeplink` 等。用户已确认不需要独立主动弹窗开关，按有效的新版本响应主动提示，不以 `force_update` 为前提。
- 商城包名为 `com.newlink.featuredapps`，详情 URI 为 `kemiappstore://app/{app_id}`。使用 `ACTION_VIEW` 并显式限制目标包；校验链接与正整数 app_id 一致，不执行任意外部 URI 或 Intent。
- 仅 `list_in_store=true` 时下发详情链接。本产品采用商城升级必须在商城展示，不能配置为“仅自升级（不展示）”。
- 预置商城可能只是无法处理链接的壳包；不能仅检查包名存在。应验证链接能否解析并处理启动失败；如采用包可见性查询，需配置精确的 Manifest 查询范围。
- 官方通用文档建议商城不可用时回退为直接下载安装；**本项目按用户要求不采用此回退**，仅提示商城不可用并保留键盘可用状态。不增加 `REQUEST_INSTALL_PACKAGES` 或用于升级安装的 FileProvider。
- 通用文档的强制更新阻断建议不自动套用于键盘：用户仅授权提示和跳转，没有授权禁用输入或强制安装。
