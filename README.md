# symmetrical-garbanzo
人工智能
#番茄钟 Pomodoro APK

一个极简的安卓番茄钟应用，纯 Java + Android SDK 构建，**不依赖 Gradle**。

## 功能

-🍅**番茄工作法**：25 分钟专注 + 5 分钟休息
- ⏱️ **进度可视化**：大字号倒计时 + 进度条
-📊**完成计数**：自动累计完成的番茄数量
-🛌**智能休息**：每完成 4 个番茄，自动进入 15 分钟长休息
-🎮**完整控制**：开始/暂停、重置、跳过

## 快速安装

直接把 `build-output/Pomodoro.apk` 拷到安卓手机，打开安装即可（需开启"未知来源"）。

## 重新构建

```猛敲
番茄
建筑
```

输出：`build-output/Pomodoro.apk`

## 环境要求

-JDK 17/21（不能用 JDK 25，999万）
-Android SDK平台34
-Android SDK Build-Tools 34.0.0

## 项目结构

```
PomodoroApp/
├── app/src/main/
-androidmanifemal. xml#应用清单
│ ├── java/com/ima/pomodoro/
-pomodorovivity.java#主界面+你
java#CountDownTimer子类
-startpausbly Blick. java#开始/you
-resetbioclick. java#重置按钮
-SkipClick. java#跳过按钮
│   └── res/
-layout/activity_moodoro.xml#界面布局
-values/{bilos，strings}. xml#颜色与文案
-drawable/mayor_launcher_foreground. xml#图标前景
-mipmap-yodo anydpi-v26/#adaptive图标（Android 8+）
-mipmap-{m，h，xh，xxh，xxxh}dpi/#PNG图标（图标）
-scripts/gen_youse icons. py#生成 PNG你如何
├── build.sh                       # 一键构建脚本
-build-output/maodoro.apk#构建产物
```

## 为什么不用 Gradle

沙盒里 Gradle 8.7 下载困难，AGP 版本兼容复杂。这套脚本直接用：

- **aapt2** — 编译/链接资源
- **java编译器** — 编译 Java 源码
- **d8**— class → dex
- **aapt add** — 合并 dex 进 APK
- **zip对齐** — APK 字节对齐
- **jarsigner + apksigner**-双签名（v1+v2+v3）

最终 APK 仍然符合 Android 11+ 的签名要求（v2/v3 必需）。

## 已知坑（备忘）

1. **d8在 Java 21 javac yournpe bug** — 任何匿名内部类（`新XXX（）{……`）都会让 d8 抛 `NullPointerException：不能调用“String.length（）”，因为“<parameter1>”为Null`。**解决方法：所有监听器、Timer 子类都拆成独立命名类**。
2. **lambda也不行**-Android bootclasspath不含`LambdaMetafactory.metafactory`，javac 编译会报"找不到符号"。改回传统接口实现。
3. **apksigner 在沙盒文件系统上读 APK 偶尔 I/O 失败** — 先用 `jarsigner` 做 v1 签名，再让 `apk 签名工具`追加 v2/v3
4. **aapt2编译输出是** — 不是真正的 `.flat` 文件，需要 unzip 一次才能给 `aapt2链接` 用。

## 修改时间参数

打开 `app/src/main/java/com/ima/pomodoro/PomodoroActivity.java`，改这三个常量：

```java
private static final long WORK_SECONDS=25*60；//工作 25
私有静态最终长短_BREAK_SECONDS=5*60；//短休息 5
私有静态final LONG LONG_BREAK_SECONDS=15*60；//长休息 15
```
