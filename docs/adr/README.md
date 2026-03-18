# Architecture Decision Records (ADR)

このディレクトリは、運用・実装に影響する主要な設計判断を記録するための ADR を管理します。

## 目的

- 設計判断の背景・検討案・採用理由を残し、将来の運用判断を容易にする
- 新規メンバーのオンボーディングを短縮する
- 変更時に「なぜそうなっているか」を追跡可能にする

## 命名規則

- `NNNN-short-title.md`（例: `0001-custom-cli-parser.md`）
- 番号は連番、削除せず `Superseded`（置換）で履歴を残す

## ステータス

- `Proposed`
- `Accepted`
- `Superseded by NNNN`
- `Deprecated`

## テンプレート

新しい ADR は `0000-adr-template.md` をコピーして作成してください。
