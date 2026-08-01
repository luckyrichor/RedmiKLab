# GitHub 公开发布设计

## 目标

将 RedmiKLab 的最新可公开源代码发布到 `luckyrichor/RedmiKLab`，Git 远程连接使用独立 SSH 别名 `github.com-luckyrichor`。GitHub 只包含干净的 `main` 历史；本地 `mobile-network-diagnostics` 保留全部开发提交和私人真机记录。

## 本地与远程结构

首次发布结束后只保留两个本地分支和两个 Worktree：

- `/Users/lc/Project/RedmiKLab` 检出公开 `main`，跟踪 `origin/main`。
- `/Users/lc/Project/RedmiKLab/.worktrees/mobile-network-diagnostics` 检出私人 `mobile-network-diagnostics`，不设置远程跟踪分支。
- GitHub 只创建并接收 `main`；禁止 `git push --all`、`git push --mirror` 和上传私人分支。

## 公开内容

公开版本从 `mobile-network-diagnostics` 当前文件快照生成，但不继承其提交历史。公开内容包括 Android 源码、测试、构建文件、README、项目约定以及 `docs/superpowers/plans` 和 `docs/superpowers/specs`。

以下路径不进入公开历史：

- `docs/analysis`
- `docs/superpowers/progress`
- 真实导出报告、设备数据库、APK、构建缓存、`local.properties` 和 `.DS_Store`

公开 `.gitignore` 额外忽略 `.DS_Store` 与任意子目录下的 `.DS_Store`。

## 发布历史

公开 `main` 使用 orphan 根提交 `chore: publish initial public release`，其作者为 `luckyrichor <luckyrichor@gmail.com>`。旧 `main` 暂时改名为 `legacy-main`；完成备份、推送和验证后，确认它是 `mobile-network-diagnostics` 的祖先，再安全删除。

## 后续受控同步

日常开发继续在 `mobile-network-diagnostics` 完成并提交。公开更新不合并私人分支，而是从以下白名单路径复制最新文件状态到公开 `main`：

- `AGENTS.md`
- `README.md`
- `app`
- `build.gradle.kts`
- `core`
- `feature`
- `gradle.properties`
- `gradle`
- `gradlew`
- `gradlew.bat`
- `scripts`
- `settings.gradle.kts`
- `docs/superpowers/plans`
- `docs/superpowers/specs`

新增顶层目录默认不公开，必须先审查再加入白名单。每次同步后检查暂存路径和常见凭据，运行 `testDebugUnitTest` 与 `assembleDebug`，然后在公开 `main` 生成独立提交并仅推送 `origin/main`。

## 验收条件

- SSH 测试明确识别为 GitHub 用户 `luckyrichor`。
- GitHub 仓库为 Public，且只有 `main` 分支。
- 公开历史与私人开发历史没有共同祖先。
- 公开树中不存在两类私人文档路径及常见密钥标记。
- 完整单元测试和 Debug APK 构建成功。
- 最终本地仅保留 `main` 与 `mobile-network-diagnostics`，对应两个 Worktree。
