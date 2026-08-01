# GitHub Public Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish a sanitized, buildable RedmiKLab snapshot to the public `luckyrichor/RedmiKLab` GitHub repository while keeping full development history and real-device evidence local.

**Architecture:** Keep `mobile-network-diagnostics` as the private source-of-truth branch. Build an orphan public `main` in a temporary linked Worktree, remove private evidence paths, verify and push only `main`, then relocate public `main` to the repository root and delete the obsolete legacy branch.

**Tech Stack:** Git 2.50.1, Git Worktree, SSH, GitHub, Gradle Wrapper, JDK 17, Android SDK.

## Global Constraints

- Remote Git transport must use `git@github.com-luckyrichor:luckyrichor/RedmiKLab.git`.
- Public commit identity must be `luckyrichor <luckyrichor@gmail.com>` and scoped to the public Worktree.
- Never push `mobile-network-diagnostics`, `legacy-main`, `--all`, or `--mirror`.
- Exclude `docs/analysis` and `docs/superpowers/progress` from every public commit.
- Run credential-path checks, `testDebugUnitTest`, and `assembleDebug` before the first push.

---

### Task 1: Record and protect the private repository

**Files:**
- Create: `docs/superpowers/specs/2026-08-01/github-public-release.md`
- Create: `docs/superpowers/plans/2026-08-01/github-public-release.md`

**Interfaces:**
- Consumes: existing `mobile-network-diagnostics` branch and project documentation convention.
- Produces: approved publication contract and a verified bundle backup.

- [ ] **Step 1: Commit the approved specification and plan on the private branch**

```bash
git add docs/superpowers/specs/2026-08-01/github-public-release.md \
  docs/superpowers/plans/2026-08-01/github-public-release.md
git commit -m "docs: plan sanitized GitHub publication"
```

- [ ] **Step 2: Create and verify a full local bundle backup**

```bash
mkdir -p .git/backups
git bundle create .git/backups/before-public-2026-08-01.bundle --all
git bundle verify .git/backups/before-public-2026-08-01.bundle
```

### Task 2: Build the orphan public branch

**Files:**
- Modify: `.gitignore`
- Exclude: `docs/analysis/**`
- Exclude: `docs/superpowers/progress/**`

**Interfaces:**
- Consumes: latest `mobile-network-diagnostics` tree.
- Produces: local public `main` with one root commit and no private-history parent.

- [ ] **Step 1: Rename old main and create the detached temporary Worktree**

```bash
git branch -m main legacy-main
git worktree add --detach .worktrees/public-release mobile-network-diagnostics
```

- [ ] **Step 2: Create orphan main and restore the latest tree**

```bash
git -C .worktrees/public-release switch --orphan main
git -C .worktrees/public-release restore \
  --source=mobile-network-diagnostics --staged --worktree -- .
```

- [ ] **Step 3: Remove private paths and add macOS ignores**

```bash
git -C .worktrees/public-release rm -r docs/analysis docs/superpowers/progress
```

Use `apply_patch` to append `.DS_Store` and `**/.DS_Store` to the public `.gitignore`.

- [ ] **Step 4: Configure Worktree-scoped public identity**

```bash
git -C .worktrees/public-release config extensions.worktreeConfig true
git -C .worktrees/public-release config --worktree user.name luckyrichor
git -C .worktrees/public-release config --worktree user.email luckyrichor@gmail.com
```

### Task 3: Verify and commit the public snapshot

**Files:**
- Verify: all staged public files.

**Interfaces:**
- Consumes: sanitized public index.
- Produces: tested public root commit.

- [ ] **Step 1: Verify exclusions, staged paths, and credential patterns**

```bash
test ! -e docs/analysis
test ! -e docs/superpowers/progress
git diff --cached --check
git grep --cached -l -I -E \
  'ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AIza[0-9A-Za-z_-]{30,}|AKIA[0-9A-Z]{16}|-----BEGIN (RSA |OPENSSH |EC )?PRIVATE KEY-----'
```

No credential-pattern output is allowed.

- [ ] **Step 2: Run the full public build verification**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
./gradlew testDebugUnitTest assembleDebug
```

- [ ] **Step 3: Commit and inspect the public root commit**

```bash
git commit -m "chore: publish initial public release"
git log -1 --format='%h %an <%ae> %s'
```

### Task 4: Create and push the public GitHub repository

**Files:**
- Modify: shared Git remote configuration.

**Interfaces:**
- Consumes: tested local public `main` and authenticated SSH alias.
- Produces: public GitHub `origin/main`.

- [ ] **Step 1: Create an empty public `luckyrichor/RedmiKLab` repository**

Create it without GitHub-generated README, `.gitignore`, or license.

- [ ] **Step 2: Add SSH origin and push only public main**

```bash
git remote add origin git@github.com-luckyrichor:luckyrichor/RedmiKLab.git
ssh -T git@github.com-luckyrichor
git push -u origin main
git ls-remote --heads origin main
```

### Task 5: Normalize the final local layout

**Files:**
- Remove Worktree directory: `.worktrees/public-release`

**Interfaces:**
- Consumes: verified local and remote public main.
- Produces: two local branches, two Worktrees, and one remote branch.

- [ ] **Step 1: Remove temporary Worktree and switch root to public main**

```bash
git -C .worktrees/public-release status --short
git worktree remove .worktrees/public-release
git switch main
git config --worktree user.name luckyrichor
git config --worktree user.email luckyrichor@gmail.com
```

- [ ] **Step 2: Verify ancestry and delete legacy main safely**

```bash
git merge-base --is-ancestor legacy-main mobile-network-diagnostics
git -C .worktrees/mobile-network-diagnostics branch -d legacy-main
```

- [ ] **Step 3: Verify final branches, Worktrees, remote refs, and public tree**

```bash
git branch -vv
git worktree list
git ls-remote --heads origin
git ls-tree -r --name-only main
git ls-tree -r --name-only mobile-network-diagnostics
```
