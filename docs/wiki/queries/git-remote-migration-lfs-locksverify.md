---
title: 更换 remote 地址后 push 被 Git LFS 锁校验挡下
type: query
tags: [git, lfs, remote, 排查]
created: 2026-09-20
updated: 2026-09-20
sources: []
status: active
---

# 更换 remote 地址后 push 被 Git LFS 锁校验挡下

## 问题

`git remote set-url origin git@github.com:ReachForStar/dnsopt-swift.git` 后 `git push` 失败：

```
$ git config lfs.https://github.com/ReachForStar/dnsopt-swift.git/info/lfs.locksverify false
Post "https://lfs.github.com/ReachForStar/dnsopt-swift/locks/verify": EOF
error: failed to push some refs to 'github.com:ReachForStar/dnsopt-swift.git'
```

旧地址（`xyx1926885268/dnsopt-swift`）推送一直正常。

## 根因

Git LFS 的 `locksverify` 是**按 remote URL 逐项配置**的：`git config --get-regexp lfs` 显示旧地址条目

```
lfs.https://github.com/xyx1926885268/dnsopt-swift.git/info/lfs.locksverify false
```

而新地址没有对应条目，于是 git-lfs 在 pre-push 重新启用锁校验，走 `https://lfs.github.com/...` 被网络层掐断（EOF），
整个 push 被判定失败。注意 `git lfs ls-files` 为空——本仓库当前**没有任何 LFS 对象**，
`.gitattributes` 里只有 `*.exe filter=lfs` 规则，锁校验纯属多余流量。

## 解法

```bash
git remote set-url origin git@github.com:ReachForStar/dnsopt-swift.git
git config lfs.https://github.com/ReachForStar/dnsopt-swift.git/info/lfs.locksverify false
git push origin main
```

## 复发预防

- 换 remote 地址后，把 `lfs.*` 下的 URL 相关配置（`locksverify`、`access` 等）一并迁移到新地址，别只改 `remote.origin.url`。
- push 报 `failed to push some refs` 但输出里出现 `locks/verify`／`lfs.github.com` 时，问题在 LFS 而不是 refs 本身：
  先用 `git config --get-regexp lfs` 对比新旧地址配置。

## 关联页面

- [GitHub Actions 工作流（CI / Release）](../entities/github-actions-workflows.md)
