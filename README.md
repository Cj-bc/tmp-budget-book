# 家計簿（Kotlin/Wasm + Android）

Kotlin Multiplatform と Compose Multiplatform に移植したオフライン家計簿です。ブラウザ（Kotlin/Wasm）と Android は、同一の Kotlin UI とドメインロジックを利用します。

## 開発環境

Nix では `nix develop` に JDK 21、Gradle、Android SDK command-line tools が入ります。Android ビルドには SDK Platform 35 のインストールも必要です。

## 実行・ビルド

```bash
# Kotlin/Wasm の開発サーバー
gradle :composeApp:wasmJsBrowserDevelopmentRun

# Kotlin/Wasm の配布物
gradle :composeApp:wasmJsBrowserDistribution

# Android debug APK
gradle :composeApp:assembleDebug
```

Wasm の配布物は `composeApp/build/dist/wasmJs/productionExecutable/`、APK は `composeApp/build/outputs/apk/debug/` に生成されます。

## Development

### Nix でのビルド

`nix build .#web` は nix のサンドボックス内（ネットワークなし）で Kotlin/Wasm の配布物をビルドします。依存はすべて事前に固定したものを使います。

- Maven 依存: `deps.json`（nixpkgs の `gradle.fetchDeps` が使うロックファイル）
- npm 依存: `kotlin-js-store/wasm/yarn.lock` と、Kotlin Gradle プラグインに同梱されている tooling 用の `yarn.lock`（`flake.nix` 内で jar から取り出します）

### Gradle の処理のキャッシュ

ビルドは 2 つの derivation に分かれています。

- `gradleDeps`（`.#web.gradleDeps`）: ビルドスクリプト（`*.gradle.kts`、`gradle/`、`gradle.properties`）と `kotlin-js-store` だけをソースに、仮のソースで Gradle のビルドを一度走らせ、`GRADLE_USER_HOME`（Maven 依存、コンパイル済みビルドスクリプト、変換済みの artifact、Kotlin の npm tooling）を出力します。
- `web`: `gradleDeps` の出力を `GRADLE_USER_HOME` にコピーし、`--offline` でビルドします。

Kotlin のソースだけを変更した場合は `gradleDeps` が Nix ストアから再利用されるので、依存の取得やビルドスクリプトのコンパイルはやり直しません。ビルドスクリプトや `kotlin-js-store` を変更すると `gradleDeps` から再ビルドされます。

新しいソースセット（例: `jsMain`）を使い始めたときは、`flake.nix` の `gradleDeps` の `preBuild` にある仮のソースを置くソースセットの一覧にも追加してください。足りないと `web` のオフラインビルドが依存を見つけられずに失敗します。

#### オフラインでのビルド

依存が一度 Nix ストアに揃っていれば、ネットワークなしでもビルドできます。その場合は `--offline` を付けてください。

```bash
nix build .#web --offline
```

Nix はまだビルドしていない derivation（ソースを変更した後の `web` など）について、ビルドする前に substituter（`cache.nixos.org`）へ出力があるかを問い合わせます。オフラインではこの問い合わせが `unable to download 'https://cache.nixos.org/<hash>.narinfo': Couldn't resolve host name` で失敗し、ビルドが止まります。`--offline` を付けると substituter を使わなくなります。

### `deps.json` の再生成

Gradle の依存（`gradle/libs.versions.toml` や `build.gradle.kts` の dependencies、プラグイン）を変更したら `deps.json` を再生成してコミットしてください。

```bash
USE_BWRAP=0 $(nix build --no-link --print-out-paths .#web.gradleDeps.mitmCache.updateScript)
```

リポジトリのルートで実行すると `deps.json` が上書きされます。flake は git で追跡されているファイルしか参照しないため、`deps.json` を新規作成した場合は `git add` してから `nix build` してください。

### Kotlin のバージョンを上げるとき

`gradle/libs.versions.toml` の `kotlin` を変更したら、`deps.json` の再生成に加えて `flake.nix` の以下も更新が必要です。

1. `kotlinGradlePluginJar` の `url` 中のバージョンと `hash`。`hash` は再生成した `deps.json` の `org/jetbrains/kotlin#kotlin-gradle-plugin/<version>/gradle813` の `jar` の値です。
2. tooling 用 `fetchYarnDeps`（`yarnLock = kotlinToolingYarnLock`）の `hash`。一度 `nix build .#web` を実行し、hash mismatch のエラーに表示される `got:` の値に置き換えてください。

`kotlin-js-store/wasm/yarn.lock` が変わった場合も、プロジェクト用 `fetchYarnDeps` の `hash` を同じ手順で更新してください。

### Gradle のバージョン

`flake.nix` の `gradle`（`mkGradle`）は `gradle/wrapper/gradle-wrapper.properties` の `distributionUrl` と同じバージョンに揃えてください。`hash` は `https://services.gradle.org/distributions/gradle-<version>-bin.zip.sha256` の値を SRI 形式に変換したものです。

## 機能

| タブ | 機能 |
|---|---|
| 🏠 ホーム | クイック入力・最近の記録 |
| 📊 月次 | カテゴリ別予算管理（1ヶ月〜1年） |
| 🎯 積立 | 目標別の積立記録・計画差額 |
| 🔄 固定費 | 毎月の自動記録・有効/無効 |
| 📈 分析 | 12ヶ月の収支・累積残高・購入パターン・JSONバックアップ |

## 多言語対応

UI の文字列は Compose Multiplatform のリソース（`composeApp/src/commonMain/composeResources/`）にあります。既定は英語（`values/`）で、端末の言語が日本語なら `values-ja/` を使います。文字列を追加するときは両方に追加してください。

フォントは Noto Sans JP（Regular / Bold）を同梱しています。ライセンスは `licenses/NotoSansJP-OFL.txt` です。

組み込みのカテゴリは言語によらない ID（`food` など）で保存します。

## データ保存

Wasm 版はブラウザの `localStorage`、Android 版は `SharedPreferences` に端末内だけで保存します。分析タブから JSON を出力し、別端末でもインポートできます。
