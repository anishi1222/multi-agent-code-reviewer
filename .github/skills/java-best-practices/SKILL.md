---
name: java-best-practices
description: Javaコードのベストプラクティス準拠状況を確認します。Records、パターンマッチング、不変性、Optional、命名規則、ストリーム活用を検証します。
metadata:
  agent: best-practices
---

# Javaベストプラクティスチェック

以下のリポジトリのJavaコードをベストプラクティスの観点から分析してください。

**対象リポジトリ**: ${repository}

## 確認項目

### データモデリング
- DTOや不変データ構造にJava Recordsが使われているか
- コンパクトコンストラクタでバリデーションとデフォルト値が適切に設定されているか
- `List.copyOf()` / `Map.copyOf()` による防御的コピーが行われているか

### パターンマッチング
- `instanceof` にパターンマッチングが活用されているか
- `switch` 式が適切に使われているか（従来の `switch` 文ではなく）

### 型推論と不変性
- `var` がローカル変数で適切に使われているか（型が右辺から明確な場合のみ）
- クラスとフィールドが可能な限り `final` になっているか
- `List.of()` / `Map.of()` / `Stream.toList()` で不変コレクションが使われているか

### ストリームとラムダ
- コレクション処理にStreams APIが活用されているか
- メソッド参照（例: `stream.map(Foo::toBar)`）が使われているか

### Null処理
- `null` の返却・受け取りを避けているか
- 不在の可能性がある値に `Optional<T>` が使われているか
- `Objects.requireNonNull()` でnullチェックが行われているか

### 命名規則（Google Java Style Guide準拠）
- クラス・インターフェース: `UpperCamelCase`
- メソッド・変数: `lowerCamelCase`
- 定数: `UPPER_SNAKE_CASE`
- パッケージ: `lowercase`
- クラスは名詞、メソッドは動詞で命名されているか
- 略語やハンガリアン記法が避けられているか

各項目について違反箇所と改善案を報告してください。
