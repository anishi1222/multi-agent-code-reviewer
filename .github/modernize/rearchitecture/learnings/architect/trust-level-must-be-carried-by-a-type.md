# Trust Level Must Be Carried By A Type

信頼境界を「どのディレクトリから来たか」で区別したいなら、その出自を型に載せて末端まで運ぶ。素の `List<Path>` に併合した時点で、下流の検証器は何を書いても信頼レベル別の扱いを実装できない。

## What Happened

multi-agent-code-reviewer / t18.1（ADR-0007）。セキュリティレビューは
「リポジトリ由来の agent 定義への防御がデニーリストのみ」と報告し、アローリストの追加を
提案していた。しかし実際に読むと、検証器 (`AgentDefinitionPolicy`) は既に十分な制約
（64 KiB 上限・名前の文字種・model 接頭辞・要素数上限）を**稼働させていた**。

真の欠陥は検証ロジックではなく**型**にあった。

- 利用者が `--agents-dir` で明示したパス（信頼できる）
- `AgentPathConfig.DEFAULT_DIRECTORIES` = CWD 相対 = レビュー対象リポジトリ内（未信頼）

この 2 系統が `ApplicationPortFactory` で 1 本の `List<Path>` に併合され、
`LoadAgentPort.loadAll(List<Path>)` に渡されていた。**併合した瞬間に出自の情報は消える**ので、
以後どこにどんな検証を足しても「未信頼側だけ厳しくする」は実装不可能だった。
さらに `AgentConfig` にも出自を保持する要素がなく、事後の権限縮小もできなかった。

検証器を強化する提案をそのまま実装していたら、全経路が一律に厳しくなるか一律に緩いままか
のどちらかにしかならず、根本は残っていた。

## Takeaway

- **信頼レベルは「検証の強さ」ではなく「値の出自」で決まる。** 出自を落とす設計は、
  検証をいくら足しても信頼モデルを表現できない。
- 複数の信頼レベルを持つ入力を**同じコレクション型に併合しない**。併合が必要なら、
  要素側に出自を持たせてから併合する。
- 信頼レベルは**フラグで格上げ可能にしない**。`--allow-repo-agents` のようなオプトインは、
  利用者が反射的に付けるようになった時点で防御がゼロに戻る。出自から決まる恒久属性にする。
- **強制はテストで差分を取る**のが確実。同一入力を 2 つの出自で読ませ、
  受理と拒否に分かれることを主張する。出自を握り潰した実装は両者が同結果になり必ず落ちる
  （単一経路のテストは出自をハードコードしても通ってしまう）。

## Example

```
// 悪い: 出自が消える
List<Path> merged = new ArrayList<>(configuredDirs);
merged.addAll(userSuppliedDirs);
loadAll(merged);

// 良い: 出自が要素に載る
List<AgentSourceDirectory> merged = concat(
    configuredDirs.map(d -> new AgentSourceDirectory(d, REPOSITORY_SUPPLIED)),
    userSuppliedDirs.map(d -> new AgentSourceDirectory(d, USER_SUPPLIED)));
```

## History

- 2026-08-05 (multi-agent-code-reviewer/t18.1): initial — ADR-0007 D1 の根拠として記録
