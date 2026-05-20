以下、`README.md` としてそのまま使える全文です。

````markdown
# GUI_for_JDK_StatusCheck_by_OracleJMS

Oracle Java Management Service（JMS）から取得した実データを使い、企業内の Java Runtime / JDK 状態を視覚的に確認するための Web アプリケーションです。

本プロジェクトでは、OCI CLI を使って Oracle JMS の Fleet / Managed Instance 情報を JSON として取得し、Spring Boot バックエンドでリスクスコアを計算し、React フロントエンドでリスクマップとして表示します。

---

## このプロジェクトの全体ロードマップ

本プロジェクトは、最終的には Oracle JMS のデータを活用した Java Runtime / JDK 状態の可視化・分析ダッシュボードを目指しています。

段階的には、以下の Phase で拡張していきます。

```text
Phase 1:
  JMSからJSONファイルを取得し、
  そのJSONをSpring Bootに読み込ませて、
  Java Runtime / JDK状態を可視化する基本PoC

Phase 2:
  Web画面からJMS JSONファイルをアップロードできるようにする

Phase 3:
  OCI CLIによるJMSデータ取得をスクリプト化し、
  ワンコマンドでJSON Exportできるようにする

Phase 4:
  Spring AI / Ollama を使い、
  ルールベースではなくAIによる自然文の分析・推奨アクションを生成する

Phase 5:
  Spring BootからOCI SDK / JMS APIを直接呼び出し、
  JSONファイルを手動で扱わずにJMSデータを取得・可視化する
````

---

## Phase 1 の位置付け

現在の実装は **Phase 1** です。

Phase 1 は、最も基本的な PoC です。

具体的には、Oracle JMS から OCI CLI を使って JSON ファイルをダウンロードし、その JSON ファイルを Spring Boot アプリケーションに読み込ませ、React 画面で可視化します。

```text
Oracle JMS
  ↓ OCI CLI
fleets.json / managed-instances.json
  ↓ 手動コピー
Spring Boot
  ↓ REST API
React / Vite
  ↓
Java Runtime Risk Map
```

この Phase 1 では、まだ以下は行いません。

```text
JMS APIへの直接接続
OCI SDK連携
画面からのJSONアップロード
AIによる自然文分析
DB永続化
認証機能
```

Phase 1 の目的は、まず **JMSの実データをJSONとして取得し、それをWebアプリで読み込み、Java Runtime / JDK の状態を視覚化できることを確認する** ことです。

つまり、Phase 1 は以下を証明するためのPoCです。

```text
JMSのデータは取得できる
取得したデータはSpring Bootで処理できる
Java Runtimeの状態はリスクスコア化できる
React UIで視覚的に表現できる
```

この最小構成をベースに、Phase 2以降でアップロード機能、CLI自動化、AI分析、OCI SDK連携へ発展させます。

---

## 目的

Oracle JMS のコンソールでは、Managed Instance、Java Runtime、Applications、Java Libraries などの情報を確認できます。

しかし、通常の JMS UI は一覧表や詳細画面が中心であり、以下のような判断を一目で行うには工夫が必要です。

* どのホストが危険なのか
* どの Java Runtime が更新対象なのか
* どの Managed Instance を優先的に確認すべきか
* Fleet 全体のリスク状態はどうか
* Java 8 / UPDATE_REQUIRED / アプリ数 / JRE数などを総合的にどう見るべきか

このアプリは、JMS から取得したデータをもとに、Java 環境の状態を視覚的に理解することを目的としています。

---

## やりたいこと

このアプリで実現したいことは以下です。

```text
Oracle JMS 実データ
  ↓
OCI CLI で JSON 取得
  ↓
Spring Boot が JSON を読み込み
  ↓
Java Runtime / Security Status / Application Count からリスク計算
  ↓
React UI でリスクマップ表示
```

最終的には、JMS のデータを単なる一覧表ではなく、以下のような形で表示します。

* Fleet全体のリスクスコア
* Managed Instance数
* Critical / High / Medium / Low の件数
* 最もリスクが高いホスト
* ホストごとのJavaバージョン
* Java Security Status
* OS情報
* JRE数
* アプリケーション数
* 推奨対応アクション

---

## 現在の実装状況

現時点では、以下の Phase 1 が完了しています。

```text
Phase 1:
  OCI CLIでJMSデータをJSON取得
  Spring BootでJSON読み込み
  リスクスコアを計算
  Reactでリスクマップ表示
```

現在の画面例では、以下のような情報を表示しています。

```text
Fleet: R4_Advanced_JMS

Overall Risk Score: 92
Managed Instances: 2
Critical: 0
Top Risk Host: engine.lab.local

192.168.1.6
  Java: 1.8.0_481
  Security Status: UPDATE_REQUIRED
  OS: Mac OS X
  Risk: HIGH 85

engine.lab.local
  Java: 1.8.0_481
  Security Status: UPDATE_REQUIRED
  OS: Oracle Linux 8
  Risk: HIGH 100
```

---

## アーキテクチャ

```text
+-----------------------------+
| Oracle Java Management      |
| Service / JMS Fleet         |
+-------------+---------------+
              |
              | OCI CLI
              v
+-----------------------------+
| fleets.json                 |
| managed-instances.json      |
+-------------+---------------+
              |
              | read from resources
              v
+-----------------------------+
| Spring Boot Backend         |
|                             |
| - JSON読み込み              |
| - リスクスコア計算          |
| - REST API提供              |
+-------------+---------------+
              |
              | /api/*
              v
+-----------------------------+
| React / Vite Frontend       |
|                             |
| - Risk Summary              |
| - Runtime Risk Map          |
| - Selected Instance Detail  |
| - Runtime Inventory         |
+-----------------------------+
```

---

## 技術スタック

### Backend

* Java 17
* Spring Boot
* Maven
* Jackson
* REST API

### Frontend

* React
* Vite
* lucide-react
* CSS

### Data Source

* Oracle Cloud Infrastructure
* Oracle Java Management Service
* OCI CLI
* JMS Fleet
* Managed Instance Usage

---

## 前提条件

Mac または Linux 環境を想定しています。

必要なもの：

```text
Java 17+
Maven
Node.js / npm
OCI CLI
jq
Oracle Cloud account
Oracle JMS Fleet
```

確認コマンド：

```bash
java -version
mvn -version
node -v
npm -v
oci --version
jq --version
```

---

## OCI CLI のセットアップ

OCI CLI が未設定の場合は、以下を実行します。

```bash
oci setup config
```

設定時に以下を入力します。

```text
User OCID
Tenancy OCID
Region
API Signing Key
```

API Key を作成した後、OCI Console 側で Public Key を登録します。

OCI Console:

```text
My profile
  → Tokens and keys
  → API keys
  → Add API key
  → Paste public key
```

Mac側で公開鍵を表示するには以下を実行します。

```bash
cat ~/.oci/oci_api_key_public.pem
```

登録後、疎通確認します。

```bash
oci iam region list
```

JSON形式でリージョン一覧が返れば成功です。

---

## JMS Fleet データを取得する

### 1. Compartment OCID を設定

JMS Fleet が存在する Compartment の OCID を設定します。

例：

```bash
export COMPARTMENT_ID="ocid1.compartment.oc1..xxxxxxxxxxxxxxxxxxxxxxxx"
```

確認：

```bash
echo "$COMPARTMENT_ID"
```

---

### 2. 出力ディレクトリを作成

```bash
mkdir -p ~/jms-json-export
```

---

### 3. Fleet一覧をJSONで取得

```bash
oci jms fleet list \
  --compartment-id "$COMPARTMENT_ID" \
  --all \
  --output json > ~/jms-json-export/fleets.json
```

確認：

```bash
cat ~/jms-json-export/fleets.json | jq '.data.items[] | {name: ."display-name", id: .id, state: ."lifecycle-state"}'
```

例：

```json
{
  "name": "R4_Advanced_JMS",
  "id": "ocid1.jmsfleet.oc1.iad.xxxxx",
  "state": "ACTIVE"
}
```

---

### 4. 対象FleetのOCIDを取得

```bash
export FLEET_ID=$(cat ~/jms-json-export/fleets.json | jq -r '.data.items[] | select(."display-name"=="R4_Advanced_JMS") | .id')

echo "$FLEET_ID"
```

---

### 5. Managed Instance情報を取得

```bash
oci jms managed-instance-usage summarize \
  --fleet-id "$FLEET_ID" \
  --output json > ~/jms-json-export/managed-instances.json
```

確認：

```bash
cat ~/jms-json-export/managed-instances.json | jq '.'
```

この JSON には、以下のような情報が含まれます。

```text
hostname
managed-instance-id
managed-instance-type
agent.java-version
agent.java-security-status
operating-system
approximate-application-count
approximate-installation-count
approximate-jre-count
time-first-seen
time-last-seen
```

---

## プロジェクト構成

```text
GUI_for_JDK_StatusCheck_by_OracleJMS/
  backend/
    pom.xml
    src/main/java/com/example/fleetcommander/
      FleetCommanderApplication.java
      JmsController.java
    src/main/resources/jms-data/
      fleets.json
      managed-instances.json

  frontend/
    package.json
    vite.config.js
    src/
      App.jsx
      App.css
```

---

## JMS JSON データをプロジェクトにコピー

取得した JSON を Spring Boot の resources 配下へコピーします。

```bash
mkdir -p ~/java-fleet-commander-ai/backend/src/main/resources/jms-data

cp ~/jms-json-export/fleets.json \
  ~/java-fleet-commander-ai/backend/src/main/resources/jms-data/

cp ~/jms-json-export/managed-instances.json \
  ~/java-fleet-commander-ai/backend/src/main/resources/jms-data/
```

---

## Backend の起動

```bash
cd ~/java-fleet-commander-ai/backend
mvn spring-boot:run
```

正常に起動すると、Spring Boot は以下で起動します。

```text
http://localhost:8080
```

API確認：

```bash
curl http://localhost:8080/api/risk-summary | jq
```

例：

```json
{
  "fleetName": "R4_Advanced_JMS",
  "totalManagedInstances": 2,
  "overallRiskScore": 92,
  "criticalCount": 0,
  "highCount": 2,
  "mediumCount": 0,
  "lowCount": 0,
  "topRiskHost": "engine.lab.local"
}
```

Managed Instance 一覧確認：

```bash
curl http://localhost:8080/api/managed-instances | jq
```

---

## Frontend の起動

```bash
cd ~/java-fleet-commander-ai/frontend
npm install
npm run dev
```

Frontend は以下で起動します。

```text
http://localhost:5173
```

---

## Vite のポート固定

本プロジェクトでは、Vite のポートを `5173` に固定します。

`frontend/vite.config.js`:

```js
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': 'http://localhost:8080'
    }
  }
})
```

これにより、React 側から `/api/*` にアクセスすると、Spring Boot の `localhost:8080` に転送されます。

確認：

```bash
curl http://localhost:5173/api/risk-summary | jq
```

---

## 起動時の構成

開発時はターミナルを2つ使います。

### Terminal 1: Backend

```bash
cd ~/java-fleet-commander-ai/backend
mvn spring-boot:run
```

### Terminal 2: Frontend

```bash
cd ~/java-fleet-commander-ai/frontend
npm run dev
```

ブラウザで開きます。

```text
http://localhost:5173
```

---

## 使用中ポートの確認

Spring Boot:

```bash
lsof -i :8080
```

Vite / React:

```bash
lsof -i :5173
```

不要なViteプロセスを止める場合：

```bash
lsof -ti :5173 | xargs kill -9
```

過去に 5174 / 5175 でも起動していた場合：

```bash
lsof -ti :5174 | xargs kill -9
lsof -ti :5175 | xargs kill -9
```

---

## リスクスコアの考え方

現在の Phase 1 では、簡易的なルールベースでリスクスコアを計算しています。

例：

```text
Java 1.8.x                  +30
UPDATE_REQUIRED             +35
Application Count >= 5      +15
Installation Count >= 4     +10
JRE Count >= 3              +10
```

ただし、`1.8.0_481` のような比較的新しい Java 8 update を単純に Critical と判定しないようにしています。

現在の方針：

```text
UPDATE_REQUIRED + Java 8 + アプリ数が多い
  → HIGH

VULNERABLE / UNSUPPORTED / KNOWN_SECURITY_ISSUES
  → CRITICAL
```

つまり、Critical は本当に重大な状態に限定し、通常の更新必要状態は High として扱います。

---

## API一覧

### Risk Summary

```http
GET /api/risk-summary
```

レスポンス例：

```json
{
  "fleetName": "R4_Advanced_JMS",
  "totalManagedInstances": 2,
  "overallRiskScore": 92,
  "criticalCount": 0,
  "highCount": 2,
  "mediumCount": 0,
  "lowCount": 0,
  "topRiskHost": "engine.lab.local"
}
```

---

### Managed Instances

```http
GET /api/managed-instances
```

レスポンス例：

```json
[
  {
    "hostname": "192.168.1.6",
    "javaVersion": "1.8.0_481",
    "javaSecurityStatus": "UPDATE_REQUIRED",
    "osName": "Mac OS X",
    "osFamily": "MACOS",
    "osArchitecture": "aarch64",
    "applicationCount": 4,
    "installationCount": 2,
    "jreCount": 1,
    "riskScore": 85,
    "riskLevel": "HIGH",
    "recommendation": "192.168.1.6 requires attention. Review Java version 1.8.0_481 and apply required updates."
  }
]
```

---

## トラブルシューティング

### 1. `oci iam region list` が NotAuthenticated になる

API Key が OCI Console に正しく登録されていない可能性があります。

確認：

```bash
cat ~/.oci/config
```

確認項目：

```text
user
fingerprint
key_file
tenancy
region
```

API Key の Fingerprint が OCI Console 側と一致している必要があります。

---

### 2. `jq: parse error` が出る

JSONファイルの中身がエラーメッセージになっている可能性があります。

確認：

```bash
cat ~/jms-json-export/managed-instances.json
```

---

### 3. Spring Boot のポート 8080 が重複する

確認：

```bash
lsof -i :8080
```

停止：

```bash
kill -9 <PID>
```

---

### 4. Vite のポートが 5174 / 5175 になる

過去の Vite プロセスが残っています。

確認：

```bash
lsof -i :5173
lsof -i :5174
lsof -i :5175
```

停止：

```bash
lsof -ti :5173 | xargs kill -9
lsof -ti :5174 | xargs kill -9
lsof -ti :5175 | xargs kill -9
```

---

### 5. React画面にデータが出ない

Backend API が動いているか確認します。

```bash
curl http://localhost:8080/api/risk-summary | jq
```

Vite proxy が効いているか確認します。

```bash
curl http://localhost:5173/api/risk-summary | jq
```

両方成功すれば、ブラウザを強制リロードします。

```text
Command + Shift + R
```

---

## 今後の拡張予定

今後は以下を追加予定です。

### Phase 2: JSONアップロード機能

現在は `managed-instances.json` を resources 配下に手動コピーしています。

将来的には、画面から以下をアップロードできるようにします。

```text
fleets.json
managed-instances.json
jre-usage.json
```

---

### Phase 3: OCI CLI Export Script

JMSデータ取得をワンコマンド化します。

例：

```bash
./export-jms-json.sh
```

---

### Phase 4: Spring AI / Ollama 連携

現在の recommendation はルールベースです。

将来的には、Spring AI と Ollama を使って、より自然な分析文を生成します。

例：

```text
このホストは Java 8 を利用しており、UPDATE_REQUIRED 状態です。
アプリケーション数が多いため、まずステージング環境で更新検証を行うことを推奨します。
```

---

### Phase 5: JMS API / OCI SDK 直接連携

最終的には JSON ファイル手動取得ではなく、Spring Boot から OCI SDK / API を使って JMS データを直接取得する構成を目指します。

```text
Spring Boot
  ↓
OCI SDK
  ↓
Oracle JMS
  ↓
リアルタイム可視化
```

---

## このプロジェクトの位置づけ

このプロジェクトは、以前作成した JMS AI Assistant とは別プロジェクトです。

### JMS AI Assistant

```text
JMSドキュメントに対するRAG / AI Chatbox
```

### GUI_for_JDK_StatusCheck_by_OracleJMS

```text
JMS実データを使ったJDK / Java Runtime状態の可視化Webアプリ
```

つまり、本プロジェクトは Chatbox ではなく、JMSデータを利用した Visual Intelligence / Risk Dashboard です。

---

## Repository Name

```text
GUI_for_JDK_StatusCheck_by_OracleJMS
```

---
```
```

