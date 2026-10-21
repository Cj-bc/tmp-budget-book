# 家計簿（Kotlin/Wasm + Android）

Kotlin Multiplatform と Compose Multiplatform に移植したオフライン家計簿です。ブラウザ（Kotlin/Wasm）と Android は、同一の Kotlin UI とドメインロジックを利用します。

## 開発環境

Nix では `nix develop .#android` に JDK 21、Gradle、Android SDK（Platform 35、Build-Tools 36.0.0）が入ります。`nix develop .#web` は Kotlin/Wasm 用です。

## 実行・ビルド

```bash
# Kotlin/Wasm の開発サーバー
gradle :composeApp:wasmJsBrowserDevelopmentRun

# Kotlin/Wasm の配布物
gradle :composeApp:wasmJsBrowserDistribution

# Android debug APK
gradle :androidApp:assembleDebug

# 単体テスト（commonTest を JVM 上で実行）
gradle :composeApp:testAndroidHostTest
```

`wasmJsNodeTest` では Skiko の wasm を Node で読み込めずにテストが走らないため、テストは Android のホストテストで実行します。

Wasm の配布物は `composeApp/build/dist/wasmJs/productionExecutable/`、APK は `androidApp/build/outputs/apk/debug/` に生成されます。

アプリ本体は `composeApp`（Kotlin Multiplatform の Android ライブラリ）にあり、`androidApp` はそれを APK にまとめるだけのモジュールです（マニフェストと Android のリソースを持ちます）。`composeApp` の Compose リソース（文字列とフォント）は Compose Multiplatform 1.7 ではライブラリの APK に入らないので、`androidApp` が assets にコピーしています。

### バージョン情報

設定画面の「このアプリについて」に、ビルドしたコミット（GitHub のコミットへのリンク付き）、ビルド日時（UTC）、対応するバックアップの最新バージョン（`BACKUP_VERSION`）を表示します。コミットとビルド日時はビルド時に `composeApp` の `generateBuildInfo` タスクが生成します。

- コミットは `git rev-parse HEAD` から取得し、未コミットの変更があれば `-dirty` を付けます。`.git` のない環境では `-Pkakeibo.commit=<hash>` で渡してください（`nix build` は flake の `self.rev` を自動で渡します）。
- コンテナイメージは `.git` をコピーしないので、`--build-arg COMMIT=$(git rev-parse HEAD)` を付けてビルドしてください。
- ビルド日時は `-Pkakeibo.buildTime=2026-01-01T00:00:00Z` で固定できます。

## Development

### Nix でのビルド

`nix build .#web` は nix のサンドボックス内（ネットワークなし）で Kotlin/Wasm の配布物を、`nix build .#android` は debug APK（`result/tmp-budget-book.apk`）をビルドします。依存はすべて事前に固定したものを使います。

- Maven 依存: `deps.json`（nixpkgs の `gradle.fetchDeps` が使うロックファイル）
- npm 依存: `kotlin-js-store/wasm/yarn.lock` と、Kotlin Gradle プラグインに同梱されている tooling 用の `yarn.lock`（`flake.nix` 内で jar から取り出します）

### Gradle の処理のキャッシュ

ビルドは 2 つの derivation に分かれています。

- `gradleDeps`（`.#web.gradleDeps`、`.#android.gradleDeps`）: ビルドスクリプト（`*.gradle.kts`、`gradle/`、`gradle.properties`）、`kotlin-js-store`、`androidApp` だけをソースに、仮のソースで Gradle のビルドを一度走らせ、`GRADLE_USER_HOME`（Maven 依存、コンパイル済みビルドスクリプト、変換済みの artifact、Kotlin の npm tooling）を出力します。`.#web.gradleDeps` は web のタスクだけを、`.#android.gradleDeps` は web と Android のタスクを走らせます。Android SDK の Build-Tools（aapt2）は x86_64 向けしかないので、web のビルドは Android SDK に依存させず、aarch64-linux（Termux など）でもビルドできるようにしています。
- `web` と `android`: `gradleDeps` の出力を `GRADLE_USER_HOME` にコピーし、`--offline` でビルドします。

Kotlin のソースだけを変更した場合は `gradleDeps` が Nix ストアから再利用されるので、依存の取得やビルドスクリプトのコンパイルはやり直しません。ビルドスクリプト、`kotlin-js-store`、`androidApp` を変更すると `gradleDeps` から再ビルドされます。

新しいソースセット（例: `jsMain`）を使い始めたときは、`flake.nix` の `gradleDeps` の `preBuild` にある仮のソースを置くソースセットの一覧にも追加してください。足りないと `web` や `android` のオフラインビルドが依存を見つけられずに失敗します。

#### オフラインでのビルド

依存が一度 Nix ストアに揃っていれば、ネットワークなしでもビルドできます。その場合は `--offline` を付けてください。

```bash
nix build .#web --offline
```

Nix はまだビルドしていない derivation（ソースを変更した後の `web` など）について、ビルドする前に substituter（`cache.nixos.org`）へ出力があるかを問い合わせます。オフラインではこの問い合わせが `unable to download 'https://cache.nixos.org/<hash>.narinfo': Couldn't resolve host name` で失敗し、ビルドが止まります。`--offline` を付けると substituter を使わなくなります。

### `deps.json` の再生成

Gradle の依存（`gradle/libs.versions.toml` や `build.gradle.kts` の dependencies、プラグイン）を変更したら `deps.json` を再生成してコミットしてください。

```bash
USE_BWRAP=0 $(nix build --no-link --print-out-paths .#android.gradleDeps.mitmCache.updateScript)
```

web と Android の両方の依存を記録するため `.#android.gradleDeps` を使います。Android のビルドが必要なので x86_64-linux で実行してください。

リポジトリのルートで実行すると `deps.json` が上書きされます。flake は git で追跡されているファイルしか参照しないため、`deps.json` を新規作成した場合は `git add` してから `nix build` してください。

### Kotlin のバージョンを上げるとき

`gradle/libs.versions.toml` の `kotlin` を変更したら、`deps.json` の再生成に加えて `flake.nix` の以下も更新が必要です。

1. `kotlinGradlePluginJar` の `url` 中のバージョンと `hash`。`hash` は再生成した `deps.json` の `org/jetbrains/kotlin#kotlin-gradle-plugin/<version>/gradle813` の `jar` の値です。
2. tooling 用 `fetchYarnDeps`（`yarnLock = kotlinToolingYarnLock`）の `hash`。一度 `nix build .#web` を実行し、hash mismatch のエラーに表示される `got:` の値に置き換えてください。

`kotlin-js-store/wasm/yarn.lock` が変わった場合も、プロジェクト用 `fetchYarnDeps` の `hash` を同じ手順で更新してください。

### Gradle のバージョン

`flake.nix` の `gradle`（`mkGradle`）は `gradle/wrapper/gradle-wrapper.properties` の `distributionUrl` と同じバージョンに揃えてください。`hash` は `https://services.gradle.org/distributions/gradle-<version>-bin.zip.sha256` の値を SRI 形式に変換したものです。

### Android Gradle Plugin を上げるとき

`flake.nix` の `buildToolsVersion` を、その AGP が既定で使う Build-Tools のバージョンに揃えてください（AGP 9.4 は 36.0.0）。SDK に無いと AGP がダウンロードしようとし、サンドボックスでは `Failed to find Build Tools revision` で失敗します。

## 機能

お金は「資金（Fund）」に入れて管理します。収入は資金に配分され、支出は資金から払います。資金の余りも不足も翌月に繰り越します（残高はマイナスにもなります）。

| タブ | 機能 |
|---|---|
| 記録 | 支出（資金から払う）・収入（資金に入れる）の記録、最近の記録（削除と取り消し） |
| 資金 | 資金ごとの残高と、今月使える額に対する残高のバー |
| 固定費 | 毎月の支出の自動記録・有効/無効 |
| 分析 | 12ヶ月の収支・累積残高・購入パターン |

画面上部の「設定」から設定画面を開けます。設定画面では、カテゴリの追加・アーカイブ、支出カテゴリの既定の資金とピン留めの変更、JSON バックアップの書き出しと復元ができます。

記録・資金・設定の画面はリデザインまでの暫定のものです。

## 多言語対応

UI の文字列は Compose Multiplatform のリソース（`composeApp/src/commonMain/composeResources/`）にあります。既定は英語（`values/`）で、端末の言語が日本語なら `values-ja/` を使います。文字列を追加するときは両方に追加してください。

フォントは Noto Sans JP（Regular / Bold）を同梱しています。ライセンスは `licenses/NotoSansJP-OFL.txt` です。

カテゴリは ULID の ID と名前を持ち、画面にはその名前を表示します。ID は支出・収入を通じて一意です。新しい家計簿は「食費」などのプリセットのカテゴリ（`model/Categories.kt`）を作成時の言語の名前で作って始まります。作った後のプリセットは、ユーザーが追加したカテゴリと同じ普通のカテゴリです。

## データ保存

Wasm 版はブラウザの `localStorage`、Android 版は `SharedPreferences` に端末内だけで保存します。設定画面から JSON を書き出し、別端末で復元できます。

データはすべて 1 つの JSON（`LedgerData`）として保存します。バックアップは `{"version": 3, "exportedAt": ..., "data": ...}` の形です。
バージョン 2 のデータ（保存データとバックアップ）は読み込むときに変換します（`data/v2/Migration.kt`）。バージョン 2 のカテゴリは名前を持たず、プリセットは ID（`food` など）から言語に合わせた名前を、それ以外は ID をそのまま名前として表示していたので、その名前を付けます。また、バージョン 2 の ID は同じ種類（支出・収入）の中でだけ一意だった（`other` は両方にある）ので、各カテゴリに新しい ULID を付け、取引・固定費・配分ルールからの参照も付け替えます。バージョン 1 からの変換でも、カテゴリには新しい ULID を付けます。

バージョン 1 のデータ（保存データとバックアップ）も読み込むときに変換します（`data/v1/Migration.kt`）。

- 支出カテゴリごとに同じ名前の資金を作り、そのカテゴリの既定の資金にします。過去の支出と固定費はその資金から払ったことにします。
- 予算・積立・収入・収入の固定費は変換せずに捨て、捨てたものをログに出します。

収入の配分の合計が収入額と一致しないなど、整合性の取れないデータは保存も復元もしません（`model/Validation.kt`）。
