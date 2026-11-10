# Issue tracker：GitHub

本 repository 的 issue 與規格記錄於 GitHub Issues。使用 `gh` CLI，並從已設定的 Git remote 判斷 repository。

## 慣例

- 建立多行 issue 時使用 `gh issue create --body-file <path>`。
- 開始工作前閱讀 issue 內容、留言、標籤、assignee 與 dependency。
- 套用或移除 `docs/agents/triage-labels.md` 定義的標籤。
- 只有在 issue 中留下驗收證據後才可關閉。
- 可用時使用 GitHub 原生 sub-issue 與 issue dependency。
- 無法使用原生 dependency 時，在 issue 內容記錄 `受阻於：#...`。

## 是否將 Pull Request 作為 triage 入口

**不接受以 PR 作為需求入口。**

## Skill 路由

Skill 要求「發布至 issue tracker」時建立 GitHub issue；要求「取得相關 ticket」時讀取該 issue 及其留言。
