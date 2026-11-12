# 项目开发指引

## 项目与当前范围

弦准（暂用名）是原生 Android 六弦/七弦吉他调音器，先交付 APK 内测，再确定商店渠道。当前版本为中文、离线，支持标准六弦 E2 A2 D3 G3 B3 E4 与七弦 Drop A（A1 E2 A2 D3 G3 B3 E4），A4 = 440 Hz，支持自动选弦和手动固定目标。七弦按用户常用定弦使用 A1（55 Hz），不是标准七弦 B1。

用户已经授权持续开发，按当前任务推进可以独立完成的工作。真实吉他与物理手机验证仍待设备可用；不要把代码完成或合成音测试通过等同于真机验收。

## 目录与职责

- `app/src/main/java/com/xianzhun/tuner/core/`：不依赖 Android 的基频检测、目标弦和调音状态机。
- `app/src/main/java/com/xianzhun/tuner/audio/`：AudioRecord 采集、输入设备选择和资源释放。
- `TunerViewModel.kt`：通过 StateFlow 整合调音状态；`MainActivity.kt`：权限与生命周期入口。
- `app/src/main/java/com/xianzhun/tuner/ui/`：Compose 界面、主题和刻度绘制。
- `app/src/test/`：核心单元测试；`app/src/androidTest/`：设备上的界面和采音生命周期测试。
- `scripts/`：构建、环境和 USB 安装脚本。
- `README.md`：环境和日常命令；`DEVELOPMENT_PLAN.md`：范围与验收基线；`docs/TEST_REPORT.md`：已验证结果与限制；`docs/PHONE_TEST_CHECKLIST.md`：真机步骤。按任务需要查阅并更新。

## 工具链与命令

固定工具链为 JDK 21、Gradle 8.13、AGP 8.13.2、Kotlin 2.2.21、Compose BOM 2025.08.00。最低 Android API 26，compile/target API 36，SDK Build Tools 35.0.0。Gradle 8.13 不使用 JDK 25；不要只为消除版本更新提示批量升级依赖。

在项目根目录执行：

```bash
./scripts/build.sh testDebugUnitTest lintDebug assembleDebug
./scripts/build.sh assembleDebugAndroidTest
./scripts/build.sh connectedDebugAndroidTest
./scripts/build.sh assembleRelease
./scripts/install-phone.sh
```

设备测试需要已授权的测试设备或模拟器；安装脚本只接受唯一连接的物理手机。发布构建需要本机原有签名，调试构建不需要。脚本会检测 Mac 上的常用工具位置；其他环境应设置 `JAVA_HOME` 和 `ANDROID_HOME`，或使用 Android Studio / Gradle Wrapper。

## 实现约束

- Kotlin 使用 4 空格缩进，保持单个 app 模块与现有职责划分。新增依赖应解决具体问题，并核查许可证与最低系统版本。
- 采音和音高计算在后台执行，UI 只消费状态；频率换算必须使用实际采样率。
- 采音会话要在暂停、离开前台和取消时释放；重新启动应等待旧会话释放，避免同时占用麦克风。
- 区分两个 E 弦以及七弦 A1/五弦 A2 的弦号和八度；自动匹配只使用当前定弦的琴弦。切换定弦必须清除旧读数和识别历史。手动模式保持目标，自动模式保留切弦迟滞。
- 调准反馈必须依赖连续有效输入。静音、削波、低可信度或中断时撤销旧的调准状态，并清理过期读数。
- Compose 自绘内容应限制绘制边界，检查大字体、内容滚动和开始/暂停按钮的可见性。
- 保持本机处理，不记录或上传原始音频。新功能涉及网络、权限或数据处理时，同步调整实现与隐私说明。

## 验证与证据

- 修改检测算法或状态机时，运行核心单元测试；覆盖音高偏移、强泛音、静音、范围外输入、迟滞和读数过期等受影响行为。
- 修改 Android 采音、权限、生命周期或界面时，运行相关设备测试。合成输入、模拟器与真实吉他结果分开记录。
- 发布 APK 前运行构建与 Lint；涉及布局时查看实际渲染，确认修复后的版本。仅修改文档时检查准确性和链接即可。
- 在报告中写明实际运行的检查、设备、结果和未验证事项。不能用旧报告证明新版本已测试，不能把实验目标写成测得结果。

## 签名与版本控制

- `.signing/` 内的原签名和口令只保存在本机或安全备份，不提交、不打印。后续版本复用原签名；不要为解决安装冲突重建签名或自动卸载用户手机上的应用。
- `scripts/create-signing-key.py` 只用于首次建立签名身份；已有项目在新机器上应恢复授权备份。
- 提交源代码、资源、测试、构建文件、Gradle Wrapper（包括 JAR）、脚本和必要文档，保留脚本执行权限。
- 不提交 SDK/JDK、构建产物、APK、缓存、IDE 本地配置、`local.properties`、密钥、口令或原始录音；生成文件保留在被忽略的 `artifacts/` 等目录。
- 提交前检查暂存区文件清单与差异。推送到用户指定的仓库，保留已有远端历史，默认使用普通推送。

## 交付沟通

默认用中文，说明做了什么、如何验证和还缺什么。真机未连接时继续推进独立工作，将声学精度、端到端延迟、兼容性和持续使用测试留为明确的待验证项。
