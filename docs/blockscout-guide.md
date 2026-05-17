# blockscout-guide

Besu QBFT 네트워크의 블록, 트랜잭션, ERC-20 전송 내역을 확인하는 Blockscout 실행 가이드

## 실행 순서

#### 1. Besu 네트워크

```bash
cd /QBFT-Network
docker compose up -d
```

#### 2. MySQL + Blockscout

```bash
cd /server
docker compose up -d
```

MySQL, Blockscout DB(postgres), Blockscout 백엔드/프론트엔드가 함께 시작됩니다. Blockscout 백엔드 초기화에 1~2분 걸립니다.

#### 3. Spring 서버

```bash
./gradlew.bat bootRun
```

#### 4. 브라우저 접속

```
http://localhost:4000        # Blockscout UI
http://localhost:4000/txs    # 전체 트랜잭션 목록
```

---

## API test (Postman)

아래 순서대로 실행합니다. 각 단계의 응답에서 `address` 값을 다음 단계에 사용합니다.

---

**1. 컨트랙트 배포**

`POST http://localhost:8080/api/institutions/1/contracts/deploy`

Body (raw / JSON):
```json
{"name":"CBDC"}
```

---

**2. 지갑 생성 1**

`POST http://localhost:8080/api/wallet`

→ 응답의 `address`를 `WALLET1`으로 기록

---

**3. 지갑 생성 2**

`POST http://localhost:8080/api/wallet`

→ 응답의 `address`를 `WALLET2`으로 기록

---

**4. Deployer 잔액 조회**

`GET http://localhost:8080/api/balance/0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73`

→ 1,000,000 MHT (1000000 × 10¹⁸) 확인

---

**5. Deployer → Wallet1 이체**

`POST http://localhost:8080/api/transfer`

Body (raw / JSON):
```json
{
  "from": "0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73",
  "to": "WALLET1_ADDRESS",
  "amount": 1000000000000000000
}
```

---

**6. Wallet1 잔액 조회**

`GET http://localhost:8080/api/balance/WALLET1_ADDRESS`

---

**7. Wallet1 → Wallet2 이체**

`POST http://localhost:8080/api/transfer`

Body (raw / JSON):
```json
{
  "from": "WALLET1_ADDRESS",
  "to": "WALLET2_ADDRESS",
  "amount": 500000000000000000
}
```

---

**8. Wallet2 잔액 조회**

`GET http://localhost:8080/api/balance/WALLET2_ADDRESS`

---

응답의 `transactionHash`를 Blockscout에서 검색하거나 직접 확인:

```
http://localhost:4000/tx/트랜잭션_HASH
```

---

## 실패 시 재실행

Blockscout가 정상적으로 뜨지 않으면 `server/` 에서 아래 3개 명령어를 순서대로 실행합니다.

```bash
cd /server
docker compose down -v
docker compose up -d
docker compose logs -f blockscout-backend
```

`Access BlockScoutWeb.Endpoint at` 메시지가 나오면 준비 완료입니다.

## Docker 초기화 (전체 리셋)

```bash
cd /server
docker compose down -v

cd /QBFT-Network
docker compose down -v
```

---

## 오류 대응

#### Blockscout 화면이 안 열릴 때

백엔드 초기화가 끝나지 않은 경우입니다. 로그를 확인합니다.

```bash
cd /server
docker compose logs -f blockscout-backend
```

`[info] Access BlockScoutWeb.Endpoint at` 메시지가 나오면 준비된 것입니다.

#### `econnrefused` 반복

Besu 컨테이너 상태를 확인합니다.

```bash
docker ps
```

`besu-node1`이 없으면 QBFT-Network를 먼저 실행합니다.

#### `No contract deployed for institution 1`

API test 첫 번째 단계(컨트랙트 배포)를 먼저 실행합니다.

#### `Something went wrong` 화면

컨테이너를 재시작합니다.

```bash
cd /server
docker compose restart blockscout-backend blockscout-frontend
```

브라우저에서 `Ctrl + F5`로 강력 새로고침합니다.
