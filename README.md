# 弦准 · Android Guitar Tuner

原生 Android 六弦/七弦吉他调音器，暂用名“弦准”。Kotlin + Jetpack Compose，Android 8.0 及以上。

## 开发环境

- Android Studio，或可用的 Android SDK 命令行环境。
- JDK 21：构建脚本可检测 Mac 上的 Homebrew `openjdk@21`；其他安装位置请设置 `JAVA_HOME`。Gradle 8.13 不支持 JDK 25，不要直接使用带 JDK 25 的 Studio 默认构建配置。
- Android SDK：标准位置 `$HOME/Library/Android/sdk`，需要 platform-tools、platforms;android-36、build-tools;35.0.0。
- Gradle 8.13：由项目 Wrapper 下载，不用全局安装。

```bash
git clone https://github.com/Djentleman114514/Guitar-Tuner.git
cd Guitar-Tuner
```

在 Android Studio 打开项目，设置 Gradle JDK 为 21、SDK 目录为本机安装位置。SDK 不在默认位置时设置 `ANDROID_HOME`；也可在不提交的 `local.properties` 中配置 `sdk.dir`。首次同步需要下载依赖。

macOS 终端构建：

```bash
./scripts/build.sh testDebugUnitTest lintDebug assembleDebug
```

其他平台可设置好 `JAVA_HOME`、`ANDROID_HOME` 后使用 `./gradlew`，Windows 使用 `gradlew.bat` 执行相同任务。

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。这是开发调试包，正式长期内测前需要固定发布签名，不能把调试签名当成最终商店签名。

项目的固定发布签名保存在维护者本机被 Git 忽略的 `.signing/` 目录，仓库不包含私钥或口令。首次克隆可直接构建调试包；维护者在新机器上应先恢复原签名的安全备份，再用 `./scripts/build.sh assembleRelease` 构建可持续覆盖升级的内测 APK：`app/build/outputs/apk/release/app-release.apk`。没有签名配置时只会生成未签名发布包，不能直接安装。

只有为独立的新应用首次建立签名身份时才运行 `python3 scripts/create-signing-key.py`。不要用新密钥替代本项目的原签名，也不要将签名或口令提交到公共仓库。

调试包与发布包签名不同，应分别使用模拟器和试用手机；如果同一设备已经安装调试包，需要先卸载它，再安装发布包。

## 安装到手机

也可以把已签名的 APK 发送到手机直接安装，无需 USB；后续使用相同签名的新版覆盖升级。以下 USB 流程适用于开发调试。

1. 手机开启开发者选项和 USB 调试，用支持数据传输的 USB 线接入 Mac。
2. 解锁手机，在手机上允许这台 Mac 调试。Mac 的系统连接授权出现时也允许。
3. 执行 `./scripts/install-phone.sh`，自动构建、覆盖安装并打开应用。脚本只选择唯一的物理手机，避免装到错误设备。
4. 点击“开始调音”，同意麦克风权限。选择标准六弦或七弦 Drop A，分别拨响空弦；可点击琴弦固定目标。

## 实现范围

- 标准六弦 E2 A2 D3 G3 B3 E4，或七弦 Drop A：A1 E2 A2 D3 G3 B3 E4；A4 = 440 Hz。
- 七弦目标 A1 = 55 Hz，其余六弦保持标准定弦；可切换模式，自动识别和手动目标均跟随当前定弦。
- 自动选弦、手动固定、实时频率与音分、指针、偏差文字与稳定调准状态。
- AudioRecord 本机采样，YIN 基频检测、短时中值、切弦迟滞与调准迟滞。
- 按需申请麦克风权限；后台/锁屏释放麦克风，返回前台可恢复。
- 无账号、网络权限、广告、音频保存或上传。

纯音与合成谐波测试只证明算法在对应样本上的表现。真实吉他、多手机采音、系统麦克风开关、权限撤回、耗电、音频路由与持续使用仍需要真机验证。实现完成与实验达标分别记录在 [测试报告](docs/TEST_REPORT.md)。报告中提到的 APK、截图和原始日志保留在维护者本机的 `artifacts/`，不随源码仓库提交。

## 测试

核心单元测试覆盖两种采样率、七根弦、音高偏移、强泛音、衰减、噪声、静音、削波、目标匹配、迟滞与读数过期。

```bash
./scripts/build.sh testDebugUnitTest
./scripts/build.sh connectedDebugAndroidTest
```

第二条命令需要已连接的测试设备或运行中的模拟器。模拟器测试主要验证界面流程；调音能力以真实吉他和手机测试为准。

## 后续安排

详细范围、里程碑和验收方法见 [开发计划](DEVELOPMENT_PLAN.md)，真机测试步骤见 [试用清单](docs/PHONE_TEST_CHECKLIST.md)。正式上架渠道与商业模式在 APK 内测后确定。

代理开发指引维护在 [AGENT.md](AGENT.md)，[AGENTS.md](AGENTS.md) 提供自动加载入口。
