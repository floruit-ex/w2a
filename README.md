# Web2App: 将任何网站打包成安卓 App

这是一个开源项目，旨在帮助您轻松地将任何网站打包成一个功能齐全的安卓 App。

它通过 Docker 提供了一个预配置的安卓编译环境，让您无需在本地安装和配置复杂的安卓开发工具。

## ✨ 功能特性

- **多窗口支持**: 完美支持 `window.open`，可弹出多个 webview 窗口。
- **URL 支持**: 支持标准的 `http://` 和 `https://` 协议。
- **高度可定制**: 
    - 修改状态栏颜色
    - 自定义断网提示页面
    - 轻松修改 App 名称和图标
- **离线依赖**: Docker 镜像包含了大部分依赖，构建过程更稳定、更快速。

## 🚀 如何构建 (Debug 版本)

**前提:** 您需要在您的电脑上安装并运行 [Docker Desktop](https://www.docker.com/products/docker-desktop/)。

1.  **克隆或下载项目:**
    ```bash
    git clone [您的项目仓库地址]
    cd web2app
    ```

2.  **自定义您的 App :**
    - **修改网址:** 打开 `android-app/app/src/main/java/com/example/webapp/MainActivity.kt` 文件，将 `https://www.wl.ax/` 替换为您自己的网址。
    - **修改 App 名称:** 打开 `android-app/app/src/main/res/values/strings.xml` 文件，修改 `app_name` 的值。
    - **修改 App 图标:** 
        - 准备您的图标文件（推荐 PNG 格式）。
        - 将其命名为 `ic_launcher.png`。
        - 替换 `android-app/app/src/main/res/mipmap-xxhdpi/` 目录下的同名文件。
        - 如果您有圆形图标，请将其命名为 `ic_launcher_round.png` 并替换同一目录下的对应文件。
    - **修改状态栏颜色:** 打开 `android-app/app/src/main/res/values/colors.xml` 文件，修改 `statusBarColor` 的值。

3.  **运行构建:**
    在项目根目录运行以下命令：
    ```bash
    docker-compose run --rm builder
    ```
    此命令会启动 Docker 容器，并在其中执行 Gradle 构建任务。

4.  **获取 APK:**
    构建成功后，您可以在 `android-app/app/build/outputs/apk/debug/` 目录下找到生成的 `app-debug.apk` 文件。

## 📦 如何打包发布 (Release) 版本

要生成可上传到应用商店的正式版本，您需要使用签名密钥对其进行签名。此过程完全在 Docker 容器中完成，无需在您的电脑上安装 Java 或 Android 工具。

1.  **生成签名密钥 (在 Docker 中):**
    运行以下命令，在 Docker 容器内部使用 `keytool` 生成一个签名密钥。它将被保存在 `android-app/my-release-key.jks`。
    ```bash
    docker-compose run --rm builder bash -c "keytool -genkey -v -keystore /app/my-release-key.jks -alias my-key-alias -keyalg RSA -keysize 2048 -validity 10000"
    ```
    执行此命令时，系统会提示您设置密钥库密码和密钥密码。**请务必记住它们。** 为方便起见，您可以在两个提示中都输入 `android`。

    

2.  **创建密钥配置文件:**
    在 `android-app/` 目录下手动创建一个名为 `keystore.properties` 的文件。将以下内容复制到文件中，并用您在第一步中设置的密码替换占位符。
    ```properties
    storePassword=android
    keyAlias=my-key-alias
    keyPassword=android
    storeFile=/app/my-release-key.jks
    ```
    **说明：** `storeFile` 必须使用容器内的绝对路径 `/app/my-release-key.jks`，因为 Gradle 是在容器中运行的。
    
    
3.  **修改构建命令:**
    打开 `docker-compose.yml` 文件，将 `command` 字段的值修改为执行 `assembleRelease` 任务。
    ```yaml
    # docker-compose.yml
    services:
      builder:
        # ... (其他配置保持不变)
        command: bash -c "cd /app && gradle assembleRelease"
    ```

4.  **运行 Release 构建:**
    现在，运行构建命令：
    ```bash
    docker-compose run --rm builder
    ```

5.  **获取 APK:**
    构建成功后，您可以在 `android-app/app/build/outputs/apk/release/` 目录下找到已签名的 `app-release.apk` 文件。

**重要提示:** 完成 Release 构建后，如果您想再次构建 Debug 版本，请记得将 `docker-compose.yml` 中的 `command` 恢复为 `bash -c "cd /app && gradle assembleDebug"`。

## 🔧 如何适配您自己的项目

如果您想将此模板用于一个新的项目，需要修改应用的包名。

1.  **修改 `build.gradle`:**
    打开 `android-app/app/build.gradle` 文件，修改 `applicationId` 为您自己的包名。
    ```groovy
    // android-app/app/build.gradle
    android {
        namespace 'com.yourcompany.yourapp' // 建议与 applicationId 保持一致
        defaultConfig {
            applicationId "com.yourcompany.yourapp"
            // ...
        }
    }
    ```

2.  **修改目录结构:**
    在 `android-app/app/src/main/java/` 目录下，将 `com/example/webapp` 文件夹路径修改为与您的新包名匹配的结构，例如 `com/yourcompany/yourapp`。

3.  **修改 `MainActivity.kt`:**
    将 `MainActivity.kt` 移动到新的目录下，并修改文件顶部的 `package` 声明。
    ```kotlin
    // android-app/app/src/main/java/com/yourcompany/yourapp/MainActivity.kt
    package com.yourcompany.yourapp
    ```

4.  **修改 `AndroidManifest.xml`:**
    打开 `android-app/app/src/main/AndroidManifest.xml`，更新 `<application>` 标签中的 `android:theme` 属性，使其指向新的主题路径。
    ```xml
    <application
        ...
        android:theme="@style/Theme.YourApp"> <!-- 将 Webapp 修改为您的应用名 -->
        ...
    </application>
    ```

5.  **修改 `themes.xml`:**
    打开 `android-app/app/src/main/res/values/themes.xml`，更新 `<style>` 标签的 `name` 属性。
    ```xml
    <style name="Theme.YourApp" parent="Theme.MaterialComponents.DayNight.NoActionBar">
    ```

完成以上步骤后，重新运行构建即可得到一个全新包名的 App。

## 🤝 贡献

欢迎您为这个项目做出贡献！如果您有任何想法或建议，请随时提交 Pull Request 或创建 Issue。

## 📄 许可证

本项目采用 [MIT 许可证](LICENSE)。
