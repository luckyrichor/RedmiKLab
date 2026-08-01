# RedmiKLab

面向 Redmi K 系列设备的开发与测试项目。第一期为 K60 和 K80 的移动网络夜间诊断 Android App。

## 当前结构

- `app`：Android 应用入口、权限与界面。
- `core:model`：稳定的数据模型与诊断规则。
- `core:storage`：本地持久化与导出。
- `feature:diagnostics`：网络/流量采集与夜间运行编排。
- `feature:reports`：报告生成、导出与双机对比。

## 本地构建

需要 JDK 17、Android SDK Platform 37.0 和 Build Tools 36.0.0。项目使用固定的 Gradle Wrapper。

```zsh
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
./gradlew test :app:assembleDebug
```

Debug APK 输出路径为 `app/build/outputs/apk/debug/app-debug.apk`。
