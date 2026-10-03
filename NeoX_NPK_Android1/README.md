# NeoX NPK 解包器（Android 工程）

此工程把用户提供的 NeteaseUnpackTools 中的 NPK 解析思路移植为 Android Kotlin 应用。

## 功能
- Android 系统文件选择器选择 NPK
- 选择输出目录（Storage Access Framework）
- 读取文件头中的文件数量与文件表偏移
- 按 28 字节索引项提取数据为 `npkdump-0`、`npkdump-1`……
- 进度条、运行日志、错误提示

## 编译成 APK
1. 在电脑安装 Android Studio（建议稳定版）及 Android SDK 35。
2. 解压本项目 ZIP。
3. Android Studio 选择 **Open**，打开 `NeoX_NPK_Android` 文件夹。
4. 等待 Gradle 同步完成。
5. 菜单选择 **Build > Build APK(s)**。
6. APK 通常会生成在 `app/build/outputs/apk/debug/app-debug.apk`。

## 说明
- 工程使用 AndroidX DocumentFile，请在 `app/build.gradle.kts` 的 dependencies 中添加：

  `implementation("androidx.documentfile:documentfile:1.0.1")`

- 此版本沿用原项目的 NPK 结构假设，不保证适配所有 NeoX NPK 变体。
- 解包只提取数据块，不会自动解压内部压缩数据或解密文件。
- 为保护数据，应用只写入用户明确选择的目录。
