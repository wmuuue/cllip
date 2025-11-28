# 剪贴板笔记 (Clipboard Notes)

一个功能强大的Android笔记应用，支持自动剪贴板监听、音频录制和局域网设备同步。

## 功能特性

### 核心功能
- **🎈 浮动窗口** - 始终显示在屏幕上的可拖动图标（占屏幕1/40大小）
- **📋 剪贴板监听** - 后台自动捕获复制的内容并保存到笔记
- **✏️ 笔记管理** - 查看、编辑、删除和清空笔记
- **🎤 音频录制** - 支持录音、播放和保存到笔记中
- **🎨 颜色自定义** - 分别设置剪贴板文字和用户输入文字的颜色
- **📡 局域网发现** - 自动发现同一网络下的其他设备
- **📤 笔记同步** - 将笔记发送到其他设备
- **💾 数据持久化** - 使用SQLite数据库存储所有数据

### 智能功能
- **智能剪贴板控制** - 在查看笔记时自动暂停监听，避免循环复制
- **一键复制** - 点击笔记条目即可复制到剪贴板
- **设备记忆** - 记住已配对的设备，下次直接发送
- **接收确认** - 接收笔记前需要用户确认

## 技术栈

- **语言**: Kotlin 1.9.20
- **构建工具**: Gradle 8.2
- **最低SDK**: Android 7.0 (API 24)
- **目标SDK**: Android 14 (API 34)
- **数据库**: Room (SQLite)
- **架构**: MVVM + Kotlin Coroutines
- **UI**: Material Design Components

## 项目结构

```
ClipboardNotes/
├── app/
│   ├── src/main/
│   │   ├── java/com/clipnotes/app/
│   │   │   ├── data/              # 数据层
│   │   │   │   ├── AppDatabase.kt
│   │   │   │   ├── NoteEntity.kt
│   │   │   │   ├── NoteDao.kt
│   │   │   │   └── ...
│   │   │   ├── service/           # 服务层
│   │   │   │   ├── ClipboardMonitorService.kt
│   │   │   │   ├── FloatingWindowService.kt
│   │   │   │   └── NetworkDiscoveryService.kt
│   │   │   ├── ui/                # UI层
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── SettingsActivity.kt
│   │   │   │   └── NotesAdapter.kt
│   │   │   └── utils/             # 工具类
│   │   │       ├── PreferenceManager.kt
│   │   │       └── AudioRecorderManager.kt
│   │   ├── res/                   # 资源文件
│   │   │   ├── layout/
│   │   │   ├── drawable/
│   │   │   ├── values/
│   │   │   └── menu/
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── .github/workflows/build.yml    # GitHub Actions配置
```

## 构建说明

### 方法1: GitHub Actions自动构建（推荐）

1. 将代码推送到GitHub仓库
2. GitHub Actions会自动构建APK
3. 在Actions标签页下载构建的APK文件

```bash
git add .
git commit -m "Initial commit"
git push origin main
```

### 方法2: 本地构建

需要安装：
- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17

```bash
# 克隆仓库
git clone <your-repo-url>
cd ClipboardNotes

# 构建APK
./gradlew assembleRelease

# APK位置
# app/build/outputs/apk/release/app-release-unsigned.apk
```

## 安装和使用

### 安装要求
- Android 7.0 (API 24) 或更高版本
- 需要以下权限：
  - 悬浮窗权限
  - 录音权限
  - 网络访问权限

### 首次使用
1. 安装APK并打开应用
2. 授予悬浮窗权限（显示浮动图标）
3. 应用会自动进入后台，浮动图标显示在屏幕上
4. 复制任何文本，会自动保存到笔记
5. 点击浮动图标查看所有笔记

### 功能使用

#### 添加笔记
- 点击右下角的"+"按钮手动添加笔记
- 复制文本会自动添加到笔记

#### 录制音频
- 点击左下角的麦克风按钮开始录音
- 再次点击停止录音并保存

#### 发送笔记到其他设备
1. 确保两台设备在同一WiFi网络
2. 两台设备都安装并运行此应用
3. 点击菜单 → 发送
4. 选择目标设备
5. 对方设备确认接收

#### 自定义颜色
1. 点击菜单 → 设置
2. 分别设置剪贴板文字和用户输入文字的颜色

## 权限说明

| 权限 | 用途 |
|------|------|
| SYSTEM_ALERT_WINDOW | 显示浮动窗口图标 |
| FOREGROUND_SERVICE | 后台运行剪贴板监听服务 |
| RECORD_AUDIO | 录制音频笔记 |
| INTERNET | 局域网设备发现和笔记同步 |
| ACCESS_WIFI_STATE | 检测网络状态 |

## 注意事项

1. **电池优化**: 建议在系统设置中关闭此应用的电池优化，以确保后台服务持续运行
2. **权限**: 首次使用需要授予悬浮窗权限和录音权限
3. **网络**: 设备同步功能需要两台设备在同一局域网
4. **数据**: 所有数据存储在本地SQLite数据库，卸载应用会丢失数据

## 常见问题

**Q: 浮动窗口不显示？**
A: 检查是否授予了悬浮窗权限，设置 → 应用 → 特殊权限 → 显示在其他应用上层

**Q: 剪贴板监听不工作？**
A: 确保应用没有被系统强制关闭，关闭电池优化

**Q: 找不到其他设备？**
A: 确保两台设备在同一WiFi网络，并且都安装并运行了此应用

**Q: 音频录制失败？**
A: 检查是否授予了录音权限

## 开发者信息

- **Gradle版本**: 8.2
- **Kotlin版本**: 1.9.20
- **Android Gradle Plugin**: 8.2.0
- **最小SDK**: 24 (Android 7.0)
- **目标SDK**: 34 (Android 14)

## License

MIT License

## 更新日志

### v1.0.0 (2024-11-24)
- 初始版本发布
- 支持剪贴板自动监听
- 支持音频录制和播放
- 支持局域网设备同步
- 支持文字颜色自定义
