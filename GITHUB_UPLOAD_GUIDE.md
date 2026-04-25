# GitHub 上传指南

## 上传前准备

### 1. 已筛选的文件清单

以下文件已整理到 `HuanHuaJian` 文件夹，可以直接上传：

**必须上传的文件:**
- `app/src/` - 所有源代码和资源文件
- `app/build.gradle` - 模块构建配置
- `app/proguard-rules.pro` - 混淆规则
- `app/.gitignore` - Git 忽略规则
- `build.gradle` - 项目构建配置
- `settings.gradle` - 项目设置
- `gradle.properties` - Gradle 属性
- `gradlew` / `gradlew.bat` - Gradle 包装器脚本
- `gradle/wrapper/` - Gradle 包装器文件
- `README.md` - 项目说明
- `LICENSE.md` - 开源协议
- `fastlane/` - 应用商店元数据（可选）

**已排除的文件（不需要上传）:**
- `.gradle/` - Gradle 缓存
- `.idea/` - IDE 配置
- `app/build/` - 构建产物
- `build/` - 项目构建产物
- `local.properties` - 本地 SDK 路径（含绝对路径）
- `app/release/` - 发布版 APK
- `*.iml` - 模块配置文件

### 2. 文件大小检查

GitHub 对文件大小有限制：
- **单个文件最大**: 100 MB
- **推荐单个文件**: 小于 50 MB
- **仓库总大小**: 建议小于 1 GB

本项目需要注意的大文件：
- `gradle/wrapper/gradle-8.13-all.zip` (~221 MB) - **超过限制！**

**解决方案**: 
- 方案1: 删除 `gradle/wrapper/gradle-8.13-all.zip`，只保留 `gradle-wrapper.properties` 和 `gradle-wrapper.jar`
- 方案2: 使用 Git LFS (Large File Storage)

## 上传步骤

### 方法一：通过 Git 命令行上传（推荐）

1. **安装 Git**（如未安装）
   - 下载地址: https://git-scm.com/download/win

2. **配置 Git**
   ```bash
   git config --global user.name "Sliver-47"
   git config --global user.email "your-email@example.com"
   ```

3. **初始化仓库并上传**
   ```bash
   # 进入项目文件夹
   cd C:\Users\Administrator\Desktop\HuanHuaJian
   
   # 初始化 Git 仓库
   git init
   
   # 添加所有文件
   git add .
   
   # 提交更改
   git commit -m "Initial commit: HuanHuaJian Android app"
   
   # 添加远程仓库（替换为你的仓库地址）
   git remote add origin https://github.com/Sliver-47/HuanHuaJian.git
   
   # 推送到 GitHub
   git push -u origin main
   ```

### 方法二：通过 GitHub Desktop 上传

1. 下载并安装 GitHub Desktop: https://desktop.github.com/
2. 登录你的 GitHub 账号
3. 选择 "Add existing repository"
4. 选择 `HuanHuaJian` 文件夹
5. 填写提交信息并点击 "Commit to main"
6. 点击 "Publish repository"

### 方法三：通过网页直接上传

1. 打开 https://github.com/Sliver-47/HuanHuaJian
2. 点击 "Upload files"
3. 拖拽或选择文件上传
4. 填写提交信息并点击 "Commit changes"

**注意**: 网页上传有 100 个文件限制，文件较多时建议使用 Git 命令行。

## 上传后检查

上传完成后，请检查以下内容：

1. **文件完整性**: 确认所有源代码文件都已上传
2. **敏感信息**: 检查是否包含 API 密钥、密码等敏感信息
3. **构建测试**: 克隆仓库到本地，测试能否正常构建
   ```bash
   git clone https://github.com/Sliver-47/HuanHuaJian.git
   cd HuanHuaJian
   gradlew assembleDebug
   ```

## 注意事项

### 1. 安全事项
- **不要上传** `local.properties` 文件（包含本地 SDK 路径）
- **不要上传** 签名密钥文件（`.jks`, `.keystore`）
- **不要上传** Google Services JSON 文件（如包含敏感信息）
- **检查** 代码中是否硬编码了 API 密钥或密码

### 2. 仓库配置
- 在 GitHub 仓库设置中添加项目描述
- 选择合适的开源协议（已包含 LICENSE.md）
- 可以启用 Issues 和 Discussions 功能

### 3. 后续维护
- 定期提交代码更改
- 使用有意义的提交信息
- 为重要版本创建 Tag
- 及时更新 README.md

## 常见问题

**Q: 为什么 `gradle-8.13-all.zip` 不能上传？**
A: GitHub 限制单个文件最大 100MB。可以删除此文件，Gradle 会自动下载。

**Q: 上传后构建失败怎么办？**
A: 检查 `local.properties` 是否正确配置，以及 SDK 路径是否有效。

**Q: 如何更新已上传的代码？**
A: 修改文件后执行：
```bash
git add .
git commit -m "更新说明"
git push
```

---

*指南生成时间: 2026-04-25*
