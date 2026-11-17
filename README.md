# 大肥鱼 · 桌面助手（Android）

把大肥鱼 Live2D 模型做成**系统级悬浮窗**，点一下说话、听懂、回答、开口念出来。

- 这是**原生 Android App**，用 Kotlin 写，能盖在任何 App 上面
- 形象：`kamiya-whale`（神宫凉子版 DeepSeek-chan，已缩放纹理，44MB → 约 4MB）
- 语音：系统 `SpeechRecognizer`（听）+ 系统 `TextToSpeech`（说）
- 大脑：DeepSeek 官方 API（用你自己的 API Key）

## 这个 App 不是什么

- **不是 DSH 本体**。它调的是 `api.deepseek.com`，用的是你填的人格 prompt。
  所以它**没有** DSH 的记忆、技能、工具、文件权限——那是另一套东西。
  想要"带记忆的鱼"，二期要把对话接口换成 DSH 的本地 HTTP（需要 DSH 的 token，目前拿不到）。
- 第一次启动需要联网：Live2D 运行库（pixi + Cubism Core）从 CDN 加载。
  想让 App 完全离线，把下面三个 JS 下载后塞进 `app/src/main/assets/lib/` 并改 `index.html` 里的 `<script src>`：
  - `live2dcubismcore.min.js`
  - `pixi.min.js`（6.5.10）
  - `cubism4.min.js`（pixi-live2d-display 0.4.0）

## 怎么编译（三种，任选）

### A. GitHub Actions（推荐，不用装任何东西）
1. 新建一个空仓库（private 也行）
2. 把本目录全部文件推上去（注意 `.github/workflows/build.yml` 要一起推）
3. 仓库页面 → Actions → `build-apk` → Run workflow
4. 跑完后在 Artifacts 里下载 `dsh-whale-apk.zip`，里面有 `app-debug.apk`

```bash
cd whale-app
git init && git add -A && git commit -m init
git remote add origin https://github.com/<你的账号>/<仓库>.git
git push -u origin main
```

### B. Android Studio（有电脑的话）
1. Open 本目录
2. 等 Gradle sync 完
3. Run ▶ 或 Build → Build APK(s)

### C. 命令行（装了 Android SDK 的机器）
```bash
export ANDROID_SDK_ROOT=/path/to/sdk
gradle :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 装到手机后

1. 打开 App，填 **DeepSeek API Key**（`platform.deepseek.com` 申请），点保存
2. 点「悬浮窗权限」→ 在系统设置里允许「显示在其他应用上层」
3. 点「麦克风」→ 允许录音
4. 点「把鱼放到屏幕上」→ 一条常驻通知 + 屏幕右上角出现鱼
5. **点一下鱼**开始听，说完自动识别、自动回答、自动朗读
6. **长按拖动**换位置

## 已知限制

- 语音识别依赖系统服务，ColorOS/MIUI 部分机型离线不可用，需要联网
- Android 12+ 必须有常驻通知（这是系统强制，藏不掉）
- 悬浮窗里跑 WebView 比较吃内存，低端机可能卡
- 模型自带 8 组动作、23 个表情、8 个装扮槽位，但**这个 App 只用默认待机动作**；想让它按情绪换表情，在 `index.html` 里调 `model.expression('Dizzy')` 之类
- 说话时嘴会跟着开合（驱动 `ParamMouthOpenY`），但没做精确的音频对齐

## 目录

```
whale-app/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── assets/
│   │   ├── index.html          # Live2D 渲染 + 气泡 + 口型
│   │   └── live2d/             # 模型文件（dafeiyu.*）
│   ├── java/com/dsh/whale/
│   │   ├── MainActivity.kt     # 配置页
│   │   └── FloatingService.kt  # 悬浮窗 + 语音 + API
│   └── res/
├── .github/workflows/build.yml # 云编译
└── README.md
```
