# 定时任务助手（TaskHelper）

定时打开指定 App → 停留 N 分钟 → 返回桌面 → 关闭该 App，并记录执行日志。无启用任务时自动进入「休眠态」，仅保留轻量定时器，最大限度省电。

## 功能
- 创建 / 编辑 / 删除定时任务（应用、时间、停留时长 1–60 分钟、重复规则）
- 停留时长滑块 + 1 / 5 / 10 / 30 分钟快捷档
- 到点自动打开目标应用，停留结束后返回桌面并关闭
- 执行日志与成功率统计
- 极致省电「休眠模式」：无启用任务时释放后台服务
- 开机自启后自动重新排程

## 工程结构
```
app/src/main/
  AndroidManifest.xml
  java/com/taskhelper/
    MainActivity.kt            任务 / 日志 / 我的 三页
    TasksFragment.kt          列表 + 休眠态 / 运行态切换
    LogsFragment.kt           执行日志
    SettingsFragment.kt       权限与省电开关
    AddEditActivity.kt        新建 / 编辑任务（滑块、时间、重复）
    AppPickerActivity.kt      选择已安装应用
    AlarmScheduler.kt         AlarmManager 精确闹钟
    AlarmReceiver.kt          闹钟触发入口
    BootReceiver.kt           开机重新排程
    MonitorService.kt         前台服务：打开→计时→返回→关闭→记日志
    CloseAppAccessibilityService.kt  无障碍关闭最近任务
    data/AppDatabase.kt       Room：任务表 + 日志表
```

## 如何生成 APK

### 方式 A：GitHub Actions（推荐，无需本地装环境）
1. 将本目录推送到 GitHub 仓库
2. 仓库已包含 `gradlew` / `gradlew.bat` / `gradle/wrapper/`（已锁定 Gradle 8.6），以及 `.github/workflows/build-apk.yml`
3. 在 Actions 中运行 `Build Debug APK`，产物 `app-debug.apk` 可下载安装

> 工程已自带 Gradle Wrapper，CI 开箱即跑，无需你本地先装 Gradle。

### 方式 B：Android Studio
1. 用 Android Studio 打开本目录（会直接使用工程自带的 `gradlew`）
2. 等待 Gradle 同步完成
3. Build → Build Bundle(s) / APK(s) → Build APK(s)
4. 在 `app/build/outputs/apk/debug/` 取得 `app-debug.apk`

## 发布构建说明
- `release` 构建已开启 **R8 混淆 + 资源压缩**（`minifyEnabled true` / `shrinkResources true`），规则见 `app/proguard-rules.pro`（保留 Room 实体/DAO/数据库及系统组件，保证混淆后功能正常）。
- 数据库 `AppDatabase` 已配置**迁移策略**：显式注册 `MIGRATION_1_2`，并启用 `fallbackToDestructiveMigration()` 兜底；后续修改 `@Entity` 字段时，务必新增对应的 `MIGRATION_n_(n+1)`，否则走兜底重建（旧数据清空）。
- 本地出 release 包：`./gradlew assembleRelease`（需配置签名）。

## 真机必做授权（决定功能能否跑通）
本功能依赖系统级能力，需用户在设备上手动开启：
1. **精确闹钟**（Android 12+）：设置 → 应用 → 定时任务助手 → 闹钟与提醒 → 允许精确闹钟（保存任务时会自动跳转引导）
2. **无障碍服务**：设置 → 无障碍 → 定时任务助手 → 开启（用于自动关闭目标 App）
3. **电池优化白名单**：设置 → 电池 → 不受限制
4. **自启动 / 后台弹出界面**：各厂商 ROM 路径不同（华为/小米/OPPO/vivo 等），需手动授予，否则到点可能无法拉起目标 App

## 已知限制
- 第三方 App 的「后台关闭」受厂商 ROM 限制，部分机型无法被完全杀掉，需配合无障碍服务。
- 精确闹钟在系统极度省电 / 重启未授权时可能失效，建议开启电池白名单。
- 本工程为可编译起点，UI 细节与多机型适配建议在 Android Studio 中进一步打磨。
