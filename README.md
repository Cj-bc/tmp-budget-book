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

### `deps.json` の再生成

Gradle の依存（`gradle/libs.versions.toml` や `build.gradle.kts` の dependencies、プラグイン）を変更したら `deps.json` を再生成してコミットしてください。

```bash
USE_BWRAP=0 $(nix build --no-link --print-out-paths .#web.mitmCache.updateScript)
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

## データ保存

Wasm 版はブラウザの `localStorage`、Android 版は `SharedPreferences` に端末内だけで保存します。分析タブから JSON を出力し、別端末でもインポートできます。
