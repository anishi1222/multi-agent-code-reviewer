---
name: vuejs3-review
description: Vue.js 3コードのベストプラクティス準拠状況を確認します。Composition API、TypeScript、コンポーネント設計、状態管理、パフォーマンス、セキュリティを検証します。
metadata:
  agent: best-practices
---

# Vue.js 3 ベストプラクティスレビュー

以下のリポジトリのVue.js 3コードをベストプラクティスの観点から分析してください。

**対象リポジトリ**: ${repository}

## 確認項目

### アーキテクチャ
- Composition API（`setup` / composables）が使われているか（Options APIではなく）
- コンポーネントとcomposablesが機能/ドメイン別に整理されているか
- プレゼンテーショナルコンポーネントとコンテナコンポーネントが分離されているか
- 再利用可能なロジックが `composables/` ディレクトリに抽出されているか
- Piniaストアがドメイン別に構造化されているか

### TypeScript統合
- `tsconfig.json` で `strict` モードが有効か
- `<script setup lang="ts">` で `defineProps` / `defineEmits` が使われているか
- 複雑なprops/stateにインターフェースまたは型エイリアスが定義されているか
- ジェネリックコンポーネント/composablesが適切に実装されているか

### コンポーネント設計
- 各コンポーネントが単一責任原則に従っているか
- `<script setup>` 構文が使われているか
- PropsがTypeScriptでバリデーションされているか
- slots / scoped slots で柔軟な合成が行われているか

### 状態管理
- グローバル状態にPinia（`defineStore`）が使われているか
- ローカル状態に `ref` / `reactive` が適切に使われているか
- 派生状態に `computed` が使われているか
- 非同期ロジックがPinia actionsで管理されているか

### パフォーマンス最適化
- 動的インポートと `defineAsyncComponent` でコンポーネントが遅延ロードされているか
- `v-once` / `v-memo` が静的要素に使われているか
- 不要な `watch` が避けられ、`computed` が優先されているか
- ツリーシェイキングとViteの最適化が活用されているか

### セキュリティ
- `v-html` の使用を避けているか（使用時はサニタイズされているか）
- CSPヘッダーでXSS/インジェクション攻撃が緩和されているか
- 機密トークンがHTTP-only Cookieに保存されているか（`localStorage`ではなく）

### アクセシビリティ
- セマンティックHTML要素とARIA属性が使われているか
- キーボードナビゲーションが提供されているか
- カラーコントラストがWCAG AA基準を満たしているか

各項目について違反箇所と改善案を報告してください。
