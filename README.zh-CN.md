# SmartSync - Syncthing 的 Android 客户端（非官方分支）

[English](README.md) | 简体中文

SmartSync 是 [Syncthing-Fork for Android](https://github.com/researchxxl/syncthing-android)
的第三方分支（上游：[Catfriend1](https://github.com/Catfriend1/syncthing-android)，
基于 [Syncthing 官方 Android 应用](https://github.com/syncthing/syncthing-android)）。

Syncthing 是一款开源的、去中心化的文件同步工具，数据在您自己的设备之间端到端直传，
不经过任何云端服务器。

## 本分支的改动（2.1.7.x）

- **锤子风 UI 重设计**：浅色 / 深色主题、竖纹画布、分段控件
- **桌面小组件**：2×2 / 4×2 两款，同步进度实时刷新
- **自建更新通道**：应用内检查更新，下载完成先校验版本与 SHA256 再安装
- **横屏与挖孔屏适配**：内容铺满全宽，页头正确让出通知栏

## 安装

前往 [Releases](https://github.com/zryadj/SmartSync/releases) 下载
`smartsync-x.y.z.apk`，允许「安装未知应用」后直接安装；
与 Syncthing-Fork 包名一致，可直接覆盖升级并保留数据。

## 从源码构建

`libsyncthingnative.so`（Syncthing 核心的编译产物）**不在本仓库中**。
构建前请将各 ABI（arm64-v8a、armeabi-v7a、x86_64、x86）的 `.so` 文件放入
`app/src/main/jniLibs/<abi>/`——可从官方 Syncthing-Android 的 release APK 中提取，
与上游做法一致。

发布签名的配置通过 `local.properties` 或环境变量读取
（`SYNCTHING_RELEASE_STORE_FILE`、`SIGNING_PASSWORD`、`SYNCTHING_RELEASE_KEY_ALIAS`），
仓库内不包含任何机密信息。

## 许可与致谢

本项目沿用上游的 **MPL-2.0** 许可发布。原应用的全部功劳属于
Syncthing 团队与 Syncthing-Fork 的维护者们。
