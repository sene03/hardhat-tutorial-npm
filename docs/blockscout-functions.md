# Blockscout 화면 기능 설명

이 문서는 Blockscout UI에서 보이는 주요 화면과 값의 의미를 현재 Besu QBFT, CBDC, DepositToken, Settlement 구조에 맞춰 설명합니다.

Blockscout는 Spring 서버를 직접 보는 도구가 아닙니다. Spring이 Web3j로 Besu에 트랜잭션을 보내면, Blockscout가 Besu RPC를 인덱싱해 블록, 트랜잭션, 토큰 이동, 컨트랙트 호출 내역을 화면에 표시합니다.

```
Spring API -> Web3j -> Besu QBFT -> Blockscout Indexer -> Blockscout UI
```

---

## 현재 네트워크 설정

```json
{
  "chainId": 1337,
  "zeroBaseFee": true,
  "qbft": { "blockperiodseconds": 2 },
  "gasLimit": "0x47b760"
}
```

- 블록은 약 2초마다 생성됩니다. 트랜잭션이 없어도 빈 블록이 계속 생성되는 것이 정상입니다.
- `zeroBaseFee`, `gasPrice=0`, `--min-gas-price=0` 설정으로 수수료는 항상 0입니다.

---

## Transactions

```
http://localhost:4000/txs
```

체인에 포함된 트랜잭션 목록입니다. 컨트랙트 배포, ERC-20 transfer, mint, burn, Settlement.settle 호출 등 모든 온체인 동작이 여기에 표시됩니다.

#### 상단 요약 값

- `Transactions 0 (24h)`: 최근 24시간 트랜잭션 수. 인덱싱 기준에 따라 0으로 보일 수 있습니다.
- `Pending transactions 0 (1h)`: mempool 대기 트랜잭션 수. 0이면 대기 없음입니다.
- `Transactions fees 0 ETH`: 수수료 합계. gas price가 0이므로 항상 0입니다.

#### 목록 컬럼

- `Txn hash`: 트랜잭션 고유 해시. Spring API 응답의 `transactionHash`와 동일합니다.
- `Block`: 트랜잭션이 포함된 블록 번호입니다.
- `From / To`: 서명한 지갑 주소 / 호출 대상 주소입니다. 토큰 송수신자가 아닙니다.
- `Value ETH`: native coin 전송량입니다. ERC-20 송금은 항상 0입니다.
- `Fee ETH`: 트랜잭션 수수료입니다. gas price가 0이므로 항상 0입니다.

#### From/To 해석 예시

```
From: 0xFE...Bd73
To:   Settlement contract
```

중앙은행 지갑이 Settlement 컨트랙트를 호출한 것입니다. 실제 토큰 이동은 Token transfers 탭에서 확인해야 합니다.

---

## Internal Transactions

```
http://localhost:4000/internal-txs
```

EVM 내부 call trace 기반의 트랜잭션 목록입니다.

현재 `There are no internal transactions.`로 보이는 것은 정상일 수 있습니다. 현재 프로젝트의 토큰 이동은 ERC-20 이벤트 로그로 기록되며, native ETH 전송이 없고 Blockscout 설정상 internal transaction fetcher가 제한되어 있습니다.

Internal transactions가 비어 있어도 토큰 이동이 없다는 뜻이 아닙니다. 토큰 이동은 Token transfers 또는 트랜잭션 상세의 Logs에서 확인하세요.

---

## Blocks

```
http://localhost:4000/blocks
```

Besu QBFT 체인이 생성한 블록 목록입니다. 약 2초마다 블록이 생성되므로 `1s ago`, `2s ago` 간격으로 계속 추가됩니다.

#### 컬럼 의미

- `Block`: 블록 번호입니다. 높을수록 최신입니다.
- `Size, bytes`: 블록 데이터 크기입니다. 빈 블록은 약 840 bytes입니다.
- `Miner`: 해당 블록을 제안한 QBFT validator 주소입니다.
- `Txn`: 블록에 포함된 트랜잭션 수입니다. 0이면 빈 블록입니다.
- `Gas used`: 블록 내 트랜잭션의 gas 사용 합계입니다.
- `Reward ETH`: 블록 보상입니다. private QBFT에서는 0입니다.
- `Burnt fees ETH`: EIP-1559 소각 수수료입니다. zeroBaseFee 설정으로 0입니다.

---

## Top Accounts

```
http://localhost:4000/accounts
```

native coin(ETH) 잔액 기준으로 주소를 정렬한 목록입니다.

여기서 보이는 잔액은 `genesis.json`의 `alloc`에 설정된 초기 native coin 잔액입니다. CBDC나 DepositToken 잔액이 아닙니다.

현재 프로젝트의 업무 잔액은 아래 토큰 잔액으로 확인해야 합니다.

- CBDC
- Commercial Bank 1 Deposit Token (BANK2)
- Commercial Bank 2 Deposit Token (BANK3)
- Commercial Bank 3 Deposit Token (BANK4)

---

## Verified Contracts

```
http://localhost:4000/verified-contracts
```

Blockscout에 Solidity 소스코드를 검증 등록한 컨트랙트 목록입니다.

현재 `Verified contracts 0`으로 표시되는 것은 컨트랙트가 없다는 뜻이 아닙니다. 컨트랙트는 배포되어 있지만 소스코드를 Blockscout에 제출하고 검증하지 않은 상태입니다. 검증하지 않아도 트랜잭션, 토큰 이동, 로그 인덱싱은 정상 동작하나, Method 이름이나 컨트랙트 read/write UI는 제한됩니다.

---

## Tokens

```
http://localhost:4000/tokens
```

Blockscout가 인식한 ERC-20 토큰 컨트랙트 목록입니다.

같은 이름의 토큰이 여러 개 보이는 이유는 컨트랙트를 여러 번 배포했기 때문입니다. Blockscout는 체인에 남아 있는 과거 컨트랙트도 모두 인덱싱합니다.

현재 API가 실제로 사용하는 컨트랙트 주소는 Spring 서버 DB의 `contract` 테이블을 기준으로 확인하세요.

```sql
SELECT institution_id, name, address FROM contract ORDER BY institution_id, name;
```

---

## Token Transfers

```
http://localhost:4000/token-transfers
```

ERC-20 `Transfer` 이벤트 기준으로 실제 토큰 이동 내역을 보여주는 화면입니다. Transactions의 `Value ETH`가 0이어도 여기서는 CBDC, BANK2, BANK3, BANK4 이동이 표시됩니다.

#### 컬럼 의미

- `Txn hash`: 이벤트가 발생한 트랜잭션 해시입니다. 한 tx에서 여러 줄이 나올 수 있습니다.
- `From / To`: ERC-20 이벤트 기준 토큰 출발지와 도착지입니다.
- `Amount`: 이동한 토큰 수량입니다. decimals가 반영된 값입니다.
- `Token`: 어떤 토큰 컨트랙트에서 발생했는지 나타냅니다.

#### 특수 주소

- `0x0000...0000` 에서 주소로 표시되면 mint입니다. `Transfer(zero, to, amount)` 이벤트로 기록됩니다.
- 주소에서 `0x0000...0000` 으로 표시되면 burn입니다. `Transfer(from, zero, amount)` 이벤트로 기록됩니다.

#### 같은 은행 내부 송금

```
From: 0x62...Ef57
To:   0x69...593B
Token: BANK2
```

예금토큰이 그대로 이동합니다. CBDC 움직임은 없습니다.

#### 다른 은행 송금 (같은 tx hash에 3줄)

```
0x62...Ef57  ->  0x00...0000   BANK2   (burn)
0x62...Ef57  ->  0xf1...b732   CBDC    (reserve 간 이동)
0x00...0000  ->  0xDd...4Bf4   BANK3   (mint)
```

출발 은행 토큰이 burn되고, CBDC로 reserve 정산 후, 도착 은행 토큰이 mint됩니다.

---

## Gas Tracker

```
http://localhost:4000/gas-tracker
```

현재 네트워크의 gas fee 상태를 보여줍니다.

```
Network utilization 0.00%
Base fee: 0
Priority: 0
```

private Besu 네트워크에서 gas price와 base fee를 0으로 설정했기 때문에 모든 값이 0입니다. gas limit은 트랜잭션 실행에 필요하지만 gas price가 0이므로 실제 수수료 ETH는 발생하지 않습니다.

---

## Contract Verification

```
http://localhost:4000/contract-verification
```

배포된 컨트랙트의 Solidity 소스코드와 컴파일 설정을 업로드해 검증하는 화면입니다. 검증 완료 시 Blockscout에서 컨트랙트 ABI, read/write UI, Method 이름이 표시됩니다.

---

## API Documentation

```
http://localhost:4000/api-docs
```

Blockscout REST API Swagger UI입니다.

자주 사용하는 엔드포인트는 다음과 같습니다.

```
GET /api/v2/transactions/{hash}                    트랜잭션 상세
GET /api/v2/transactions/{hash}/token-transfers    트랜잭션 내 토큰 이동
GET /api/v2/transactions/{hash}/logs               트랜잭션 이벤트 로그
GET /api/v2/blocks                                 블록 목록
GET /api/v2/token-transfers                        전체 토큰 이동 내역
GET /api/v2/tokens                                 토큰 목록
GET /api/v2/addresses/{address}/token-balances     주소별 토큰 잔액
```

---

## 송금 유형별 Blockscout 표시 요약

#### CBDC 송금

- Transactions To: CBDC 컨트랙트
- Token transfers: 0xFE...Bd73 에서 수신 지갑으로, Token: CBDC

#### 같은 은행 내부 송금

- Transactions To: DepositToken 컨트랙트
- Token transfers: 기관 지갑에서 사용자 지갑으로, Token: BANK#

#### 다른 은행 정산

- Transactions To: Settlement 컨트랙트
- Token transfers: burn, CBDC 이동, mint 3줄이 같은 tx hash로 표시됩니다.

---

## 자주 헷갈리는 점

`Value ETH`가 0이면 송금이 안 된 것처럼 보일 수 있습니다. ERC-20 송금은 native ETH를 보내지 않으므로 0이 정상입니다.

`Fee ETH`가 0인 것은 gas price를 0으로 설정했기 때문입니다.

Internal transactions가 비어 있어도 토큰 이동이 없다는 뜻은 아닙니다. ERC-20 이동은 Token transfers와 Logs에서 확인해야 합니다.

Blocks에 빈 블록이 계속 생기는 것은 QBFT의 정상 동작입니다. 약 2초마다 블록이 생성됩니다.

Tokens에 같은 이름의 토큰이 여러 개 보이는 것은 여러 번 배포했기 때문입니다. 현재 사용하는 컨트랙트는 DB의 `contract` 테이블 주소로 확인하세요.

Top accounts의 ETH 잔액은 genesis 초기 native coin 잔액입니다. CBDC나 DepositToken 잔액이 아닙니다.

`0x0000...0000` 주소는 ERC-20에서 mint 또는 burn을 표현하기 위해 사용하는 zero address입니다.
