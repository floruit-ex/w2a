# === 终极离线投喂版 Dockerfile ===
FROM ubuntu:22.04

LABEL maintainer="KotlinBuilder"

# 1. 环境变量
ENV DEBIAN_FRONTEND=noninteractive \
    JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64" \
    ANDROID_HOME="/opt/android-sdk" \
    ANDROID_SDK_ROOT="/opt/android-sdk" \
    GRADLE_HOME="/opt/gradle-8.2" \
    PATH="$PATH:/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:/opt/gradle-8.2/bin"

# 2. 安装基础系统软件 (这里还是要联网，但阿里云源很快)
RUN sed -i 's/archive.ubuntu.com/mirrors.aliyun.com/g' /etc/apt/sources.list && \
    apt-get update && \
    apt-get install -y openjdk-17-jdk unzip git curl locales && \
    localedef -i en_US -c -f UTF-8 -A /usr/share/locale/locale.alias en_US.UTF-8 && \
    rm -rf /var/lib/apt/lists/*

ENV LANG=en_US.UTF-8

# ==========================================
# 3. 安装 Android SDK (使用本地文件)
# ==========================================
WORKDIR /opt/android-sdk

# 【关键修改】直接把本地的 cmdline-tools.zip 复制进去，不再去 Google 下载
COPY cmdline-tools.zip .

# 解压并整理目录结构
RUN unzip -q cmdline-tools.zip && \
    mkdir -p cmdline-tools/latest && \
    mv cmdline-tools/bin cmdline-tools/latest/ && \
    mv cmdline-tools/lib cmdline-tools/latest/ && \
    rm cmdline-tools.zip

# 这一步仍需联网：使用 sdkmanager 下载具体的 SDK Platform
# 因为这些包很多且碎，手动下载很难配置，但有了上面的工具，这里通常比较稳
RUN yes | sdkmanager --licenses && \
    sdkmanager "platform-tools" \
               "platforms;android-33" \
               "platforms;android-34" \
               "build-tools;33.0.1" \
               "build-tools;34.0.0"

# ==========================================
# 4. 安装 Gradle (使用本地文件)
# ==========================================
WORKDIR /opt

# 【关键修改】直接把本地的 gradle zip 复制进去
COPY gradle-8.2-bin.zip .

RUN unzip -q gradle-8.2-bin.zip && \
    rm gradle-8.2-bin.zip

# ==========================================
# 5. 预热依赖 (修正版：杜绝二次下载 Gradle)
# ==========================================
WORKDIR /warmup
COPY warmup/ .

# 【重要修改】
# 1. 之前是用 ./gradlew，它会傻乎乎地去重新下载 Gradle 发行版。
# 2. 现在改用 gradle 命令，直接使用我们在第4步手动安装好的 /opt/gradle-8.2。
# 3. 这样只会下载 jar 包 (Retrofit, Kotlin等)，不会下载 Gradle zip。
RUN gradle assembleDebug --no-daemon && \
    gradle clean && \
    rm -rf /warmup

# 6. 收尾
WORKDIR /app