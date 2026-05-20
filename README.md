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
```

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

## 重要: JMS JSONデータについて

このリポジトリには、実際の JMS JSON データは含めていません。

理由は、JMS から取得した JSON には以下のような環境固有情報が含まれる可能性があるためです。

```text
Compartment OCID
Fleet OCID
Managed Instance OCID
ホスト名
IPアドレス
OS情報
Java Runtime情報
アプリケーション数
JRE数
```

そのため、`backend/src/main/resources/jms-data/` 配下の JSON ファイルは `.gitignore` により Git 管理対象外にしています。

利用者は、自分の OCI / JMS 環境から以下の2つの JSON ファイルを取得し、ローカル環境に配置する必要があります。

```text
backend/src/main/resources/jms-data/fleets.json
backend/src/main/resources/jms-data/managed-instances.json
```

JSON を取得する手順は、本 README の **「JMS Fleet データを取得する」** セクションを参照してください。

最小限必要なデータは以下です。

```text
fleets.json
managed-instances.json
```

`jre-usage.json` は Phase 1 では必須ではありません。Java バージョンや Java Security Status は `managed-instances.json` から取得しています。

---

## 目的

Oracle JMS のコンソールでは、Managed Instance、Java Runtime、Applications、Java Libraries などの情報を確認できます。

しかし、通常の JMS UI は一覧表や詳細画面が中心であり、以下のような判断を一目で行うには工夫が必要です。

- どのホストが危険なのか
- どの Java Runtime が更新対象なのか
- どの Managed Instance を優先的に確認すべきか
- Fleet 全体のリスク状態はどうか
- Java 8 / UPDATE_REQUIRED / アプリ数 / JRE数などを総合的にどう見るべきか

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

- Fleet全体のリスクスコア
- Managed Instance数
- Critical / High / Medium / Low の件数
- 最もリスクが高いホスト
- ホストごとのJavaバージョン
- Java Security Status
- OS情報
- JRE数
- アプリケーション数
- 推奨対応アクション

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
Fleet: Advanced_JMS_log

Overall Risk Score: 92
Managed Instances: 2
Critical: 0
Top Risk Host: engine.lab.local

192.168.11.2
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

`Advanced_JMS_log` はサンプル名です。利用者は、自分のJMS環境に存在するFleet名に置き換えてください。

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

- Java 17
- Spring Boot
- Maven
- Jackson
- REST API

### Frontend

- React
- Vite
- lucide-react
- CSS

### Data Source

- Oracle Cloud Infrastructure
- Oracle Java Management Service
- OCI CLI
- JMS Fleet
- Managed Instance Usage

---

## 前提条件

Mac または Linux 環境を想定しています。

必要なもの：

```text
Java 17+
Maven 3.6.3+
Node.js 20+
npm
OCI CLI
jq
Git
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
git --version
```

---

## Oracle Linux 8 での動作確認

本プロジェクトは Oracle Linux 8 上でも動作確認済みです。

確認済み環境の例：

```text
OS: Oracle Linux 8
Java: 17
Node.js: 20.20.2
npm: 10.8.2
OCI CLI: 3.81.1
jq: 1.6
Maven: 3.9.9
```

Oracle Linux 8 の標準環境では、Node.js や Maven が古い場合があります。その場合は以下の手順で更新してください。

### Node.js 20 のインストール

既存の古い Node.js を削除し、Node.js 20 stream を有効化します。

```bash
sudo dnf module reset nodejs -y
sudo dnf remove nodejs npm -y
sudo dnf module enable nodejs:20 -y
sudo dnf module install nodejs:20/common -y
```

確認：

```bash
node -v
npm -v
```

期待例：

```text
v20.x.x
10.x.x
```

### OCI CLI のインストール

Oracle Linux 8 では、Developer Repository を有効化して OCI CLI をインストールできます。

```bash
sudo dnf -y install oraclelinux-developer-release-el8
sudo dnf install python36-oci-cli -y
```

確認：

```bash
oci --version
```

### jq の確認

```bash
jq --version
```

未インストールの場合：

```bash
sudo dnf install jq -y
```

### Maven 3.9.9 のインストール

Oracle Linux 8 標準の Maven が古い場合、Spring Boot のビルドで以下のようなエラーが出ることがあります。

```text
maven-compiler-plugin requires Maven version 3.6.3
```

その場合は Maven 3.9.9 をインストールします。

```bash
cd /tmp

curl -LO https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz

sudo tar -xzf apache-maven-3.9.9-bin.tar.gz -C /opt

sudo ln -sfn /opt/apache-maven-3.9.9 /opt/maven
```

PATH を設定します。

```bash
sudo tee /etc/profile.d/maven.sh > /dev/null <<'EOF'
export MAVEN_HOME=/opt/maven
export PATH=$MAVEN_HOME/bin:$PATH
EOF
```

現在のターミナルに反映します。

```bash
source /etc/profile.d/maven.sh
hash -r
```

確認：

```bash
which mvn
mvn -v
```

期待例：

```text
/opt/maven/bin/mvn
Apache Maven 3.9.9
```

もし PATH が反映されない場合は、直接以下で起動できます。

```bash
/opt/maven/bin/mvn spring-boot:run
```

---

## 初回セットアップ手順

このリポジトリを利用する場合は、まず GitHub からプロジェクトを clone します。

```bash
cd ~
git clone https://github.com/bull2023x/GUI_for_JDK_StatusCheck_by_OracleJMS.git
cd GUI_for_JDK_StatusCheck_by_OracleJMS
```

本リポジトリには、実際の JMS JSON データは含めていません。

利用者は、自分の OCI / JMS 環境から以下の JSON ファイルを取得し、ローカル環境に配置する必要があります。

```text
backend/src/main/resources/jms-data/fleets.json
backend/src/main/resources/jms-data/managed-instances.json
```

全体の流れは以下です。

```text
1. GitHub から clone
2. OCI CLI を設定
3. JMS Fleet / Managed Instance 情報を JSON として取得
4. JSON を backend/src/main/resources/jms-data/ に配置
5. Spring Boot Backend を起動
6. React / Vite Frontend を起動
7. ブラウザで http://localhost:5173 を開く
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

Mac / Linux 側で公開鍵を表示するには以下を実行します。

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

複数行コマンドがうまく貼れない場合に備えて、1行コマンドで記載します。

```bash
oci jms fleet list --compartment-id "$COMPARTMENT_ID" --all --output json > ~/jms-json-export/fleets.json
```

確認：

```bash
cat ~/jms-json-export/fleets.json | jq '.data.items[] | {name: ."display-name", id: .id, state: ."lifecycle-state"}'
```

例：

```json
{
  "name": "Advanced_JMS_log",
  "id": "ocid1.jmsfleet.oc1.iad.xxxxx",
  "state": "ACTIVE"
}
```

---

### 4. 対象FleetのOCIDを取得

`Advanced_JMS_log` はサンプル名です。利用者は、自分のJMS環境に存在するFleet名に置き換えてください。

```bash
export FLEET_NAME="Advanced_JMS_log"
```

Fleet OCID を取得します。

```bash
export FLEET_ID=$(cat ~/jms-json-export/fleets.json | jq -r --arg name "$FLEET_NAME" '.data.items[] | select(."display-name"==$name) | .id')
```

確認：

```bash
echo "$FLEET_ID"
```

`ocid1.jmsfleet...` のような値が表示されればOKです。

Fleet名が分からない場合は、以下で一覧確認できます。

```bash
cat ~/jms-json-export/fleets.json | jq -r '.data.items[] | ."display-name"'
```

---

### 5. Managed Instance情報を取得

```bash
oci jms managed-instance-usage summarize --fleet-id "$FLEET_ID" --output json > ~/jms-json-export/managed-instances.json
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
cd ~/GUI_for_JDK_StatusCheck_by_OracleJMS

mkdir -p backend/src/main/resources/jms-data

cp ~/jms-json-export/fleets.json backend/src/main/resources/jms-data/fleets.json
cp ~/jms-json-export/managed-instances.json backend/src/main/resources/jms-data/managed-instances.json
```

確認：

```bash
ls -lh backend/src/main/resources/jms-data
```

中身確認：

```bash
cat backend/src/main/resources/jms-data/managed-instances.json | jq '.data.items[] | {hostname: .hostname, javaVersion: .agent."java-version", securityStatus: .agent."java-security-status"}'
```

---

## Backend の起動

```bash
cd ~/GUI_for_JDK_StatusCheck_by_OracleJMS/backend
mvn spring-boot:run
```

Oracle Linux 8 などで Maven を `/opt/maven` に入れた場合は、以下でも起動できます。

```bash
cd ~/GUI_for_JDK_StatusCheck_by_OracleJMS/backend
/opt/maven/bin/mvn spring-boot:run
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
  "fleetName": "Advanced_JMS_log",
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

別ターミナルで実行します。

```bash
cd ~/GUI_for_JDK_StatusCheck_by_OracleJMS/frontend
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
cd ~/GUI_for_JDK_StatusCheck_by_OracleJMS/backend
mvn spring-boot:run
```

または：

```bash
cd ~/GUI_for_JDK_StatusCheck_by_OracleJMS/backend
/opt/maven/bin/mvn spring-boot:run
```

### Terminal 2: Frontend

```bash
cd ~/GUI_for_JDK_StatusCheck_by_OracleJMS/frontend
npm install
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
  "fleetName": "Advanced_JMS_log",
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
    "hostname": "192.168.11.2",
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
    "recommendation": "192.168.11.2 requires attention. Review Java version 1.8.0_481 and apply required updates."
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

### 2. `oci jms fleet list` で MissingParameter になる

以下のようなエラーが出る場合：

```text
compartmentId or fleetId must be provided
```

`--compartment-id` が指定されていない可能性があります。

正しい例：

```bash
oci jms fleet list --compartment-id "$COMPARTMENT_ID" --all --output json > ~/jms-json-export/fleets.json
```

複数行コマンドの貼り付けに失敗する場合は、1行コマンドで実行してください。

---

### 3. `jq: parse error` が出る

JSONファイルの中身がエラーメッセージになっている可能性があります。

確認：

```bash
cat ~/jms-json-export/managed-instances.json
```

---

### 4. Spring Boot のポート 8080 が重複する

確認：

```bash
lsof -i :8080
```

停止：

```bash
kill -9 <PID>
```

---

### 5. Vite のポートが 5174 / 5175 になる

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

### 6. React画面にデータが出ない

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

Linuxブラウザの場合は通常のリロード、またはキャッシュ削除後に再読み込みしてください。

---

### 7. `vite: command not found` が出る

frontend の依存ライブラリがまだインストールされていない可能性があります。

```bash
cd ~/GUI_for_JDK_StatusCheck_by_OracleJMS/frontend
npm install
npm run dev
```

---

### 8. Maven のバージョンが古い

以下のようなエラーが出る場合：

```text
maven-compiler-plugin requires Maven version 3.6.3
```

Maven 3.6.3 以上が必要です。Oracle Linux 8 の場合は、本 README の **「Oracle Linux 8 での動作確認」** セクションにある Maven 3.9.9 の手順を参照してください。

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
