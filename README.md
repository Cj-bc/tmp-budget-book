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
