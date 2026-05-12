# blockscout-guide

Blockscout를 Docker로 실행해서 현재 Besu QBFT 네트워크의 블록, 트랜잭션, 컨트랙트, ERC-20 토큰 전송 내역을 GUI로 확인하는 실행 문서입니다.

Blockscout는 Spring 서버를 직접 조회하지 않습니다. Spring API 또는 Hardhat이 Besu에 트랜잭션을 보내면, Blockscout가 Besu RPC를 인덱싱해서 웹 화면으로 보여줍니다.

```text
Spring API / Hardhat
        |
        v
Besu QBFT Network
        |
        v RPC
Blockscout Indexer
        |
        v
Blockscout Web UI
```

## 실행하기

#### Prerequisites

- Docker Desktop
- Git Bash
- Java 17
- Node v22
- Blockscout repository: `C:\Users\<USER>\Desktop\blockscout`
- Project repository: `C:\Users\<USER>\Desktop\hardhat-tutorial-npm`

Blockscout repository가 없으면 먼저 clone합니다.

```bash
cd /c/Users/<USER>/Desktop
git clone https://github.com/blockscout/blockscout.git
```

#### 1. Besu 네트워크 실행

```bash
cd /c/Users/<USER>/Desktop/hardhat-tutorial-npm/QBFT-Network
docker compose up -d
```

확인:

```bash
docker ps
```

다음 컨테이너가 떠 있어야 합니다.

- `besu-node1`
- `besu-node2`
- `besu-node3`
- `besu-node4`

#### 2. MySQL 실행

```bash
cd /c/Users/<USER>/Desktop/hardhat-tutorial-npm/server
docker compose up -d
```

확인:

```bash
docker ps
```

`local-currency-mysql` 컨테이너가 떠 있어야 합니다.

#### 3. Spring 서버 실행

기존에 `bootRun`이 켜져 있으면 해당 터미널에서 `Ctrl + C`로 종료한 뒤 다시 실행합니다.

```bash
cd /c/Users/<USER>/Desktop/hardhat-tutorial-npm/server
./gradlew.bat bootRun
```

Spring 서버 주소:

```text
http://localhost:8080
```

#### 4. Blockscout 실행

현재 프로젝트에는 Blockscout용 override 파일이 있습니다.

```text
blockscout/besu-local.override.yml
```

이 파일은 Blockscout를 현재 Besu Docker network에 붙이고, UI를 `4000` 포트로 엽니다.

```bash
cd /c/Users/<USER>/Desktop/blockscout/docker-compose
```

기존 Blockscout 컨테이너가 있으면 먼저 종료합니다.

```bash
docker compose \
  -f geth.yml \
  -f ../../hardhat-tutorial-npm/blockscout/besu-local.override.yml \
  down
```

실행:

```bash
docker compose \
  -f geth.yml \
  -f ../../hardhat-tutorial-npm/blockscout/besu-local.override.yml \
  up -d
```

로그 확인:

```bash
docker compose \
  -f geth.yml \
  -f ../../hardhat-tutorial-npm/blockscout/besu-local.override.yml \
  logs -f backend
```

브라우저 접속:

```text
http://localhost:4000
```

전체 트랜잭션 목록:

```text
http://localhost:4000/txs
```

## API test

#### 1. 컨트랙트 배포

`/api/transfer`를 호출하기 전에 institution 1의 Token 컨트랙트가 먼저 배포되어 있어야 합니다.

```bash
cd /c/Users/<USER>/Desktop/hardhat-tutorial-npm
```

```bash
curl -X POST http://localhost:8080/api/institutions/1/contracts/deploy \
  -H "Content-Type: application/json" \
  -d '{"name":"CBDC"}'
```

응답 예시:

```json
{
  "institutionId": 1,
  "contractAddress": "0x...",
  "transactionHash": "0x..."
}
```

- `contractAddress`: 배포된 Token 컨트랙트 주소
- `transactionHash`: 컨트랙트 배포 트랜잭션 해시

이미 배포되어 있으면 conflict 응답이 날 수 있습니다. 그 경우에는 새로 배포하지 않고 다음 단계로 넘어가면 됩니다.

#### 2. 사용자 지갑 생성

```bash
curl -X POST http://localhost:8080/api/wallet
```

응답 예시:

```json
{
  "address": "0x35789234bcb016724db7c83f33cb755b3b8900bb",
  "privateKey": "0x..."
}
```

응답의 `address` 값을 복사합니다.

#### 3. 토큰 전송

`복사한_ADDRESS`를 위에서 생성한 사용자 지갑 주소로 바꿉니다.

```bash
curl -X POST http://localhost:8080/api/transfer \
  -H "Content-Type: application/json" \
  -d '{
    "from": "0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73",
    "to": "복사한_ADDRESS",
    "amount": 1000000000000000000
  }'
```

응답 예시:

```json
{
  "transactionHash": "0xe2fbc8872678caa21a6cf0533f96bfd59b1a30dff1df4d18adb7bfd6daf8730d",
  "from": "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73",
  "to": "0x35789234bcb016724db7c83f33cb755b3b8900bb",
  "status": "0x1"
}
```

- `status: "0x1"`: 성공
- `transactionHash`: Blockscout에서 검색할 값

## Blockscout에서 확인하기

브라우저에서 접속합니다.

```text
http://localhost:4000
```

검색창에 `transactionHash`를 입력합니다.

직접 URL로 확인할 수도 있습니다.

```text
http://localhost:4000/tx/트랜잭션_HASH
```

예시:

```text
http://localhost:4000/tx/0xe2fbc8872678caa21a6cf0533f96bfd59b1a30dff1df4d18adb7bfd6daf8730d
```

`/txs` 목록에서 다음처럼 보이면 정상입니다.

```text
Txn hash              Block   From         To                 Value ETH   Fee ETH
0xe2fbc88726...730d   14162   0xFE...Bd73  My Hardhat Token   0           0
```

#### 목록 화면 해석

- `Txn hash`: 트랜잭션 해시
- `Block`: 트랜잭션이 포함된 블록 번호
- `From`: 실제 트랜잭션 서명자
- `To`: 호출된 컨트랙트 또는 수신 주소
- `Value ETH`: native coin 전송량
- `Fee ETH`: 트랜잭션 수수료

현재 `/api/transfer`는 ERC-20 `transfer()` 호출입니다. 그래서 `To`가 사용자 지갑이 아니라 `My Hardhat Token` 컨트랙트로 보이는 것이 정상입니다.

#### 상세 화면 해석

- `Status`: 트랜잭션 성공 여부
- `From`: 기관 지갑 주소
- `To`: Token 컨트랙트 주소
- `Gas price`: private QBFT gasless 설정이면 `0`
- `Fee`: gas price가 `0`이면 `0`
- `Token transfers`: 실제 ERC-20 이동 내역
- `Logs`: ERC-20 `Transfer` 이벤트

예상 결과:

```text
Transaction From: 기관 지갑
Transaction To: My Hardhat Token 컨트랙트
Token Transfer: 기관 지갑 -> 사용자 지갑
Value ETH: 0
Fee ETH: 0
```

현재 코드 기준으로 사용자가 직접 트랜잭션을 서명하지 않습니다. Spring 서버가 DB에 저장된 기관 지갑 private key를 복호화해서 트랜잭션을 서명합니다. 그래서 Blockscout의 일반 트랜잭션 `From`은 기관 지갑으로 나옵니다.

## 현재 설정 설명

#### Besu gas price

`QBFT-Network/docker-compose.yml`의 각 Besu 노드에는 다음 설정이 들어 있습니다.

```text
--min-gas-price=0
```

Spring 서버도 private QBFT 실험에 맞춰 gas price를 0으로 보냅니다.

- `TokenService.transfer()`: gas price `0`
- `InstitutionContractDeploymentService.deploy()`: gas price `0`

따라서 Blockscout에서 새 트랜잭션의 `Gas price`, `Fee`가 `0`으로 보입니다.

#### Blockscout override

현재 override 파일:

```text
blockscout/besu-local.override.yml
```

주요 설정:

```yaml
ETHEREUM_JSONRPC_VARIANT: besu
ETHEREUM_JSONRPC_HTTP_URL: http://besu-node1:8545/
ETHEREUM_JSONRPC_WS_URL: ws://besu-node1:8546/
CHAIN_ID: "1337"
NEXT_PUBLIC_APP_HOST: localhost:4000
NEXT_PUBLIC_API_HOST: localhost:4000
INDEXER_DISABLE_BLOCK_REWARD_FETCHER: "true"
```

Blockscout backend는 `besu-node1` 컨테이너에 직접 연결됩니다. UI는 `http://localhost:4000`에서 확인합니다.

## 오류 대응

#### Git Bash에서 명령어가 끊길 때

Git Bash에서는 PowerShell의 백틱 `` ` ``을 쓰지 않습니다. 줄바꿈은 `\`를 사용합니다.

정상:

```bash
docker compose \
  -f geth.yml \
  -f ../../hardhat-tutorial-npm/blockscout/besu-local.override.yml \
  up -d
```

잘못된 예:

```bash
docker compose `
  -f geth.yml `
  up -d
```

#### `No contract deployed for institution 1`

컨트랙트를 먼저 배포해야 합니다.

```bash
curl -X POST http://localhost:8080/api/institutions/1/contracts/deploy \
  -H "Content-Type: application/json" \
  -d '{"name":"CBDC"}'
```

#### `Something went wrong. Try refreshing the page or come back later.`

Blockscout frontend가 backend API 주소를 잘못 보거나 브라우저 캐시가 남아 있을 때 발생할 수 있습니다.

컨테이너를 재생성합니다.

```bash
cd /c/Users/<USER>/Desktop/blockscout/docker-compose
```

```bash
docker compose \
  -f geth.yml \
  -f ../../hardhat-tutorial-npm/blockscout/besu-local.override.yml \
  down
```

```bash
docker compose \
  -f geth.yml \
  -f ../../hardhat-tutorial-npm/blockscout/besu-local.override.yml \
  up -d
```

브라우저에서 `Ctrl + F5`로 강력 새로고침합니다.

#### `Method not enabled` 또는 `block_reward` 오류

QBFT 실험에서는 블록 보상 조회가 필요하지 않습니다. override 파일에서 block reward fetcher를 끕니다.

```yaml
INDEXER_DISABLE_BLOCK_REWARD_FETCHER: "true"
```

설정 변경 후에는 Blockscout를 다시 생성해야 합니다.

```bash
docker compose \
  -f geth.yml \
  -f ../../hardhat-tutorial-npm/blockscout/besu-local.override.yml \
  down

docker compose \
  -f geth.yml \
  -f ../../hardhat-tutorial-npm/blockscout/besu-local.override.yml \
  up -d
```

#### `econnrefused`가 반복될 때

Besu 컨테이너가 떠 있는지 확인합니다.

```bash
docker ps
```

`besu-node1`이 없으면 Besu를 다시 실행합니다.

```bash
cd /c/Users/<USER>/Desktop/hardhat-tutorial-npm/QBFT-Network
docker compose up -d
```

#### 트랜잭션은 성공했는데 화면에 바로 안 보일 때

Blockscout 인덱싱에 몇 초 걸릴 수 있습니다. 직접 상세 URL로 확인합니다.

```text
http://localhost:4000/tx/트랜잭션_HASH
```

API로도 확인할 수 있습니다.

```text
http://localhost:4000/api/v2/transactions/트랜잭션_HASH
```

API에는 나오는데 UI만 깨지면 `Ctrl + F5`로 새로고침합니다.

## 최종 순서 요약

```bash
cd /c/Users/3-10/Desktop/hardhat-tutorial-npm/QBFT-Network
docker compose up -d
```

```bash
cd /c/Users/3-10/Desktop/hardhat-tutorial-npm/server
docker compose up -d
```

```bash
cd /c/Users/3-10/Desktop/hardhat-tutorial-npm/server
./gradlew.bat bootRun
```

새 Git Bash 터미널:

```bash
cd /c/Users/<USER>/Desktop/blockscout/docker-compose
docker compose \
  -f geth.yml \
  -f ../../hardhat-tutorial-npm/blockscout/besu-local.override.yml \
  up -d
```

컨트랙트 배포:

```bash
cd /c/Users/<USER>/Desktop/hardhat-tutorial-npm
curl -X POST http://localhost:8080/api/institutions/1/contracts/deploy \
  -H "Content-Type: application/json" \
  -d '{"name":"CBDC"}'
```

지갑 생성:

```bash
curl -X POST http://localhost:8080/api/wallet
```

토큰 전송:

```bash
curl -X POST http://localhost:8080/api/transfer \
  -H "Content-Type: application/json" \
  -d '{
    "from": "0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73",
    "to": "복사한_ADDRESS",
    "amount": 1000000000000000000
  }'
```

Blockscout 확인:

```text
http://localhost:4000/txs
```
