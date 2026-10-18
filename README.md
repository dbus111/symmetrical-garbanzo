# 番茄园

一个从零实现的本地 Android 番茄钟。界面使用 Kotlin、Jetpack Compose 和 Material 3；记录保存在设备 SQLite 中，不需要账号或网络。

## 功能

- 默认专注 25 分钟、短休 5 分钟；每完成 4 次专注，安排 15 分钟长休。三个时长都可以在“设置”中修改。
- 支持开始、暂停、继续、重置和跳过。重置当前阶段；跳过专注不会得到番茄。
- 运行中的截止时间持久化。锁屏、后台、进程重启和设备重启后按实际时间恢复；阶段完成时进入下一阶段的待开始状态。
- 完成专注后保存一条本地记录并获得一颗番茄。统计页显示今天、本周、最近 7 天和最近 4 周的数据。
- 阶段结束发送通知。Android 13 及以上需要通知权限；Android 12 及以上建议开启精确闹钟权限。未开启精确闹钟时会使用系统非精确闹钟，后台通知可能延迟，重新打开应用仍会正确结算。

## 运行和构建

需要 JDK 17、Android SDK Platform 35、Build Tools 35.0.0。项目包含标准 Gradle Wrapper。使用 Android Studio 打开本目录，等待同步后运行 `app` 即可。最低系统版本为 Android 8.0（API 26）。

在 Windows PowerShell 中，运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\build-debug.ps1
```

脚本会临时映射一个英文盘符，以绕开 Windows 用户目录中文路径导致的 Android 构建工具问题；构建后自动移除映射。本机用于构建的 JDK、SDK 和 Gradle 缓存留在项目目录内，但不会提交到仓库。克隆仓库后，安装 JDK 17 和 Android SDK 35，并设置 `JAVA_HOME`、`ANDROID_HOME`。在普通英文路径下也可以直接运行：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。连接 Android 设备后可用 `adb install -r app/build/outputs/apk/debug/app-debug.apk` 安装，或者在 Android Studio 中运行 `app`。GitHub [Releases](https://github.com/dbus111/symmetrical-garbanzo/releases) 提供预览安装包；它使用调试签名，供功能体验。

## 实现说明

计时器保存当前阶段、剩余时长和墙上时钟及开机后单调时钟的截止时间。同一次开机优先使用单调时钟，避免手动改系统时间影响倒计时；重启后用墙上时钟恢复。系统闹钟负责在后台唤醒结算，应用每次打开也会核对截止时间。专注记录以唯一会话 ID 入库，避免重复发放番茄。

本地单元测试覆盖专注结算、四次专注后的长休、暂停继续、重启恢复、跳过、自定义时长和系统时间变化。测试命令为 `:app:testDebugUnitTest`。
