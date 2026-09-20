# 領域文件

InviteCodeKt 是單一 domain context 的 repository。

修改領域行為前：

1. 閱讀 `/CONTEXT.md`。
2. 閱讀 `/docs/adr/` 下的相關 ADR。
3. 使用 `CONTEXT.md` 定義的標準詞彙。
4. 發現與 ADR 衝突時必須明確提出，不得直接覆寫既有決策。

`CONTEXT.md` 是詞彙表，不是實作規格。共同語言改變時，透過 domain modeling 新增或修改詞彙。只有難以回復且具有實質取捨的決策才建立 ADR。
