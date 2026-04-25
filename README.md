# HuanHuaJian (幻化剑)

一款功能丰富的 Android 备忘录应用，支持文本笔记、待办清单、音频记录、图片插入、标签管理和提醒功能。

## 功能特性

- **多种笔记类型**: 支持普通文本笔记和待办清单
- **富媒体支持**: 可插入图片和录制音频
- **标签管理**: 为笔记添加标签，方便分类和检索
- **提醒功能**: 支持单次、每日、工作日、每周、每月、每年提醒
- **归档与回收站**: 笔记可归档或删除，删除后进入回收站
- **数据备份**: 支持导出/导入备份（ZIP 格式）
- **自动备份**: 可设置定时自动备份到指定目录
- **桌面小部件**: 支持在桌面显示笔记列表
- **夜间模式**: 支持跟随系统/强制深色/强制浅色模式
- **多语言支持**: 支持 30+ 种语言

## 技术栈

- **语言**: Kotlin
- **架构**: MVVM
- **数据库**: Room (SQLite)
- **依赖注入**: 无（使用原生 ViewModel）
- **异步处理**: Kotlin Coroutines
- **后台任务**: WorkManager
- **图片加载**: Glide
- **UI 组件**: Material Design Components

## 项目结构

```
app/src/main/java/com/mymemo/app/
├── core/               # 核心层（模型、服务、任务、接收器）
├── data/               # 数据层（Room 数据库）
├── ui/                 # UI 层（基础组件）
├── activities/         # Activity 页面
├── fragments/          # Fragment 页面
├── viewmodels/         # ViewModel
├── recyclerview/       # RecyclerView 组件
├── view/               # 自定义视图
├── audio/              # 音频模块
├── image/              # 图片模块
├── preferences/        # 偏好设置
├── miscellaneous/      # 工具类
├── legacy/             # 遗留代码
└── widget/             # 桌面小部件
```

## 构建要求

- **minSdk**: 21 (Android 5.0)
- **targetSdk**: 34 (Android 14)
- **compileSdk**: 35
- **Gradle**: 8.13
- **Kotlin**: 1.8+

## 构建方式

```bash
# Windows
gradlew assembleRelease

# Linux/Mac
./gradlew assembleRelease
```

## 发布版本

Release APK 位于: `app/release/app-release.apk`

## 开源协议

本项目采用开源协议，详见 LICENSE.md

## 开发者

- GitHub: [Sliver-47](https://github.com/Sliver-47)
- 项目地址: https://github.com/Sliver-47/HuanHuaJian

---

*项目整理时间: 2026-04-25*
