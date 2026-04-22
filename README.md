# 家計簿

Svelte + Vite + Bun で動作するオフライン家計簿アプリ。

## セットアップ

```bash
bun install
bun run dev
```

## ビルド・プレビュー

```bash
bun run build
bun run preview
```

## 機能

| タブ | 機能 |
|------|------|
| 🏠 ホーム | クイック入力・最近の記録 |
| 📊 月次 | カテゴリ別予算管理（1ヶ月〜1年） |
| 🎯 積立 | 目標別の積立記録・計画差額表示 |
| 🔄 固定費 | 毎月自動記録される固定収支 |
| 📈 分析 | 収支グラフ・購入パターン検出・エクスポート |

## データ保存

`localStorage` にオフライン保存。アカウント不要。
「分析」タブの「エクスポート」で JSON バックアップを作成できます。

## 技術スタック

- [Svelte 4](https://svelte.dev/)
- [Vite 5](https://vitejs.dev/)
- [Bun](https://bun.sh/)
