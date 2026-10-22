# タノシミ (tanoshimi)

[한국어](README.md) | **日本語**

> 日韓交流旅行のパーティーマッチング + リアルタイム共同プランニング + AI旅行おすすめサービス

**「タノシミ（楽しみ）」= 期待・わくわく。** 旅行の *一緒に行く仲間探し* から *旅程づくり*、*旅のあとの記録* までを一つのサービスでつなぎます。

---

## 解決する課題

- 日韓旅行で **条件（性別・年齢・国籍・旅行スタイル）に合う同行者** を探せる場がない → 条件ベースのパーティーマッチング
- 複数人で旅程を組むとき **チャット + Excel + 地図** を行き来し、最新版の管理ができない → 編集権（lock）ベースのリアルタイム共同編集 + スナップショットによるロールバック
- アクティビティを選ぶたびに **天気・屋内外・移動ルート** を一つずつ考える必要がある → AIによるアクティビティ推薦・ルート並べ替え・実現可能性チェック

## 主な機能

| モジュール | 内容 |
|---|---|
| **認証・会員** | メール認証付き会員登録、ログイン、ログイン失敗時のアカウントロック、ソーシャルログイン（Google・Naver） |
| **即席集まり（パーティー）** | パーティーの作成・募集・参加申請・承認・強制退出・完了、性別/年齢/国籍による参加条件の制限 |
| **共同プランナー** | パーティーごとに計画表1つ、分単位のドラッグ式スケジュール、編集権lock、自動/手動保存、スナップショットロールバック、賛否投票 |
| **AI推薦・チャットボット** | 同行チャットボット、アクティビティの屋内外（venue_type）AI判定+キャッシュ、天気に応じた再推薦、チャット翻訳、AIクレジットの1日上限 |
| **AIルート最適化** | 固定（航空券・宿泊）/移動可能（アクティビティ）を区別、座標ベースの最近傍法による並べ替え |
| **コミュニティ** | 旅行掲示板（投稿・コメント・いいね）、おすすめ投稿、フォロー、パーティーチャット（WebSocket）、1:1 DM、ユーザーブロック |
| **マイページ** | プロフィール・自己紹介、参加パーティー・完了した旅行、地域制覇ヒートマップ、称号（38種/8カテゴリ）、マナー温度、プロフィール背景のカスタマイズ |
| **管理者** | 会員の停止/復旧、パーティーの強制終了、通報処理、バナー管理、管理者権限の管理 |
| **通知・サポート** | アプリ内通知（フォロー・コメント・パーティー申請/承認/拒否/強制退出）、1:1お問い合わせ掲示板 |

## 技術スタック

| レイヤー | 技術 |
|---|---|
| 言語/ランタイム | Java 21 |
| フレームワーク | Spring Boot 3.5.15 (Web MVC · Data JPA · Security · OAuth2 Client · WebSocket · WebFlux WebClient) |
| ビルド | Gradle (Groovy DSL) |
| ビュー | Thymeleaf + `thymeleaf-extras-springsecurity6`（サーバーサイドレンダリング） |
| フロントエンド | Vanilla JS（`static/js/*.js`）、単一の `app.css`、SockJS + STOMP — Nodeビルドなし |
| DB | MySQL 8（`utf8mb4`）、`schema.sql` を唯一の原本とし番号付きマイグレーションで管理、Hibernate `ddl-auto: update` で補助 |
| リアルタイム | STOMP over WebSocket — エンドポイント `/ws`、ブローカー `/topic`、アプリprefix `/app` |
| ファイル/画像 | Apache Tika（ファイル形式の検証）、metadata-extractor（EXIF GPS）、webp-imageio、AWS S3 / Rekognition SDK |
| 外部連携 | 天気（KMA/OpenWeather）・翻訳（Papago/DeepL）・AI（Anthropic/Gemini）— **すべて `mock` がデフォルト**、キーは `application-local.yml` に設定 |
| テスト | JUnit 5、`spring-security-test`、H2（テスト実行時） |

## クイックスタート

> プロジェクトのソースはリポジトリの [`tanoshimi/`](tanoshimi/) ディレクトリにあります。以下のコマンドはその中で実行してください。

### 1. DBの準備（MySQL 8）

```bash
cd tanoshimi
mysql -u root -p < src/main/resources/db/schema.sql
mysql -u root -p tanoshimi < src/main/resources/db/data.sql   # デモデータ（全アカウントのパスワード: Test1234!）
# 続けて db/ フォルダの migration_v16_* ～ migration_v20_* をファイル名順に実行
```

> **正確なマイグレーション順序とデモデータ（ヒートマップ・称号）のセットアップは [`tanoshimi/src/main/resources/db/README.md`](tanoshimi/src/main/resources/db/README.md)（韓国語）に従ってください。** アプリは `ddl-auto: update` で起動するため、`schema.sql` 実行後にサーバーを一度起動・停止すると不足しているカラムが自動で補完されます。

### 2. 個人用シークレットファイルの作成（git管理外）

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
# -> DBアカウント、（任意）OAuth / 外部APIキーを入力
```

- 外部連携はすべて `mock` がデフォルトのため、**キーがなくても開発・デモが可能** です。
- メール認証のローカルデフォルトは `provider: log` — 認証コードが **アプリのコンソールログ** に出力されます。
- ソーシャルログインは `application.yml` に Google・Naver・LINE の3つが登録されているため、`application-local.yml` に3つすべての `client-id`/`client-secret` を設定しないとアプリが起動しません（使わないproviderはダミー値で可）。

### 3. 起動

```bash
./gradlew bootRun     # http://localhost:8080  （プロファイル: local）
```

### テスト

```bash
./gradlew test
```

## プロジェクト構成

```
tanoshimi/                      # リポジトリルート
├── README.md                   # 韓国語版README
├── README.ja.md                # （このファイル）
├── docs/                       # 開発成果物ドキュメント
└── tanoshimi/                  # Spring Bootアプリケーション
    ├── build.gradle
    └── src/
        ├── main/
        │   ├── java/net/datasa/tanoshimi/
        │   │   ├── controller/         # View + @RestController（27個）
        │   │   ├── service/            # ドメインサービス
        │   │   ├── repository/         # Spring Data JPA
        │   │   ├── domain/{entity,dto} # エンティティ / DTO
        │   │   ├── scheduler/          # FileCleanup(04:00), PartyCompletion(00:30)
        │   │   ├── config/ · auth/ · exception/
        │   └── resources/
        │       ├── templates/          # Thymeleaf
        │       ├── static/{css,js,vendor}
        │       ├── db/                  # schema.sql + migration_v16~ + data.sql
        │       ├── application.yml
        │       └── messages_ko/ja.properties
        └── test/
```

## ドキュメント

各ドキュメントは韓国語で作成されています。

| ドキュメント | 内容 |
|---|---|
| [01. プロジェクト企画書](docs/01-프로젝트-기획서.md) | サービス概要、目標、対象ユーザー、主な機能、技術スタック、チームの役割、スケジュール |
| [02. 要件定義書](docs/02-요구사항-정의서.md) | モジュール別の機能要件（FR）、非機能要件（NFR）、エラーコード |
| [03. 画面設計書（UI）](docs/03-화면설계서.md) | 画面一覧、ルーティング、画面ごとの構成要素・動作、フロントエンド構成 |
| [04. ERD & テーブル設計書](docs/04-ERD-테이블설계서.md) | エンティティ関連図、テーブル定義、主要カラム・制約・インデックス |
| [db/ フォルダの案内](tanoshimi/src/main/resources/db/README.md) | ローカルDBのセットアップ手順、マイグレーション・シードファイルの説明 |

> ドキュメントとコードが異なる場合は **コードが正** です。

## チーム

| 役割 | 担当 |
|---|---|
| チームリーダー / 統合・QA | アーキテクチャ、ブランチ戦略、統合・QA、AIルート最適化、マナー温度、通報/管理者機能の一部 |
| 認証 | 会員登録・ログイン・ソーシャルログイン・メール認証、共通例外処理 |
| プランナー | 共同計画表（編集権・自動保存・スナップショット・投票）、AIレポート、AIコンパニオンキャラクター |
| AI推薦・チャットボット | venue_type判定、天気に応じた再推薦、アクティビティ履歴ベースの推薦、AIクレジット |
| コミュニティ | 掲示板・コメント・フォロー・チャット・DM・ユーザーブロック |
| マイページ | プロフィール・ヒートマップ・称号・アカウント管理・地域マッピング、マイ旅行（my_trips） |

## 開発ルール

- **ブランチ**: 機能ごとに `feature/*` ブランチで作業 → PRで `main` にマージ（ローカルから `main` への直接pushは禁止）。
- **スキーマ**: `schema.sql` を唯一の原本とし、番号付きマイグレーションファイルで管理。マージ前にコンパイルを確認。
- **シークレット**: DBアカウント・OAuth secret・外部APIキーは `application-local.yml`（git管理外）または環境変数でのみ注入。
- **アップロードファイル**: `${user.home}/tanoshimi-uploads`（プロジェクトフォルダの外）。
