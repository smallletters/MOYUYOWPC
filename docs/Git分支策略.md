# MOYUYO Git 分支策略

> **版本**: V1.0.0
> **生效日期**: 2026-07-14
> **策略**: Trunk-Based Development（基于主干开发）

---

## 一、分支模型

```
main          ─────── ● ─────────────── ● ──── (生产发布)
                       \                /
develop       ───────── ● ── ● ── ● ───
                         \  /    \  /
feature/*      ── ● ── ●     ── ●
```

## 二、分支约定

| 分支前缀 | 用途 | 源分支 | 合入目标 | 说明 |
|---------|------|--------|---------|------|
| `main` | 生产发布 | — | — | 只允许通过 PR 合入，禁止直接推送 |
| `develop` | 集成测试 | `main` | `main` | 日常开发集成分支，所有 feature 合入此分支 |
| `feature/*` | 功能开发 | `develop` | `develop` | 如 `feature/user-login`、`feature/pet-hub-3d` |
| `fix/*` | Bug 修复 | `develop` | `develop` | 如 `fix/cart-price-error` |
| `hotfix/*` | 紧急修复 | `main` | `main`, `develop` | 仅用于生产环境紧急问题 |

## 三、开发流程

### 3.1 开发新功能
1. 从 `develop` 拉取 `feature/<功能名>` 分支
2. 在 feature 分支上开发，保持小粒度提交
3. 完成后发起 PR → `develop`
4. 至少 1 人 Review 通过后合入

### 3.2 修复 Bug
1. 从 `develop` 拉取 `fix/<问题描述>` 分支
2. 修复后发起 PR → `develop`
3. 紧急修复走 `hotfix/*` 分支

### 3.3 发布
1. `develop` 分支通过集成测试后，发起 PR → `main`
2. 技术负责人审核通过后合入
3. 在 `main` 上打 Tag（如 `v1.0.0`）

## 四、提交规范

采用 Conventional Commits 规范：

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

### type 类型

| Type | 用途 |
|------|------|
| `feat` | 新功能 |
| `fix` | Bug 修复 |
| `docs` | 文档更新 |
| `style` | 代码格式（不影响功能） |
| `refactor` | 代码重构 |
| `perf` | 性能优化 |
| `test` | 测试相关 |
| `chore` | 构建/工具/依赖变更 |

### 示例
```
feat(user): 添加 OAuth 密码登录接口
fix(cart): 修复购物车数量越界问题
docs(readme): 更新部署指南
```

## 五、分支保护规则

| 分支 | 规则 | 说明 |
|------|------|------|
| `main` | 禁止直接推送 | 仅通过 PR 合入 |
| `main` | 至少 1 个 Approve | Code Review 通过 |
| `main` | 状态检查通过 | CI 流水线通过 |
| `develop` | 禁止直接推送 | 仅通过 PR 合入 |
| `develop` | 至少 1 个 Approve | Code Review 通过 |

## 六、注意事项

- 保持分支短生命周期（不超过 3 天）
- 每天从 `develop` 同步代码到 feature 分支
- 提交信息使用英文（项目国际化），描述使用中文
- 禁止强制推送（`git push --force`）到 `main` 和 `develop`
