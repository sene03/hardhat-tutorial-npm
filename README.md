# Hardhat & Besu QBFT tutorial

Hyperledger Besu QBFT 로컬 네트워크에 ERC-20 토큰을 배포하고, Spring Boot 서버에서 web3j로 잔액 조회와 토큰 전송을 실습하는 프로젝트입니다.

## 폴더 구조

- `contracts`: ERC-20 스마트 컨트랙트
- `scripts`: Hardhat 배포 스크립트
- `QBFT-Network`: Docker 기반 Besu QBFT 네트워크 설정
- `server`: Spring Boot + web3j REST API 서버

## 실행하기

```bash
# Besu 네트워크 띄우기
cd QBFT-Network
docker compose up -d
docker compose logs -f # 로그 확인
```

`genesis.json`의 `alloc`을 바꾼 뒤에는 기존 체인 데이터에 자동 반영되지 않는다. 개발망을 새 genesis로 다시 시작하려면 각 노드의 `key`, `key.pub`는 유지하고 Besu 데이터만 초기화한다.

```bash
cd QBFT-Network
docker compose down

find Node-1/data Node-2/data Node-3/data Node-4/data \
  -mindepth 1 \
  ! -name key \
  ! -name key.pub \
  -exec rm -rf {} +

docker compose up -d
```

```bash
# Contract 배포하기
npx hardhat run scripts/deploy-token.ts --network besu

# ------------출력 결과--------------
Deploying Token with account: 0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73
Deployer balance: 199.999999997801657 ETH
Token deployed to: 0x2E1f232a9439C3D459FcEca0BeEf13acc8259Dd8
```

```bash
# 환경변수 주입 후 서버 실행
export TOKEN_CONTRACT_ADDRESS=<0x방금_출력된_주소>
export BESU_SIGNER_PRIVATE_KEY=0x8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63
export WALLET_AES_KEY_BASE64='zQJMnPnNmVAlhWIODSOAs0ed0HIVgK2cuuwspZ+xQgE='
# 로컬 3306 포트 충돌을 피하려고 compose의 MySQL은 호스트 13306 포트로 노출된다.
# 기본값과 다르게 실행할 때만 DB_URL을 직접 지정한다.
# export DB_URL='jdbc:mysql://localhost:13306/local_currency?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul'

cd server
./gradlew bootRun
```

- TOKEN_CONTRACT_ADDRESS: 위 Token이 배포된 블록의 주소
- BESU_SIGNER_PRIVATE_KEY: 위 토큰을 배포한 주소(0xfe...)의 private key. 해당 계정으로 transfer 함수 호출 시 사용됨
- WALLET_AES_KEY_BASE64: DB의 `institution_wallet.encrypted_key` 복호화에 사용하는 개발용 AES-256 key

현재 구현한 스마트 컨트랙트 `Token.sol`이 컨트랙트를 배포한 계정에게 민팅하도록 구현되어있음. 

`deploy-token.ts`는 `hardhat.config.ts`의 첫 번째 계정으로 배포하므로, 첫 번째 계정이 토큰을 가지고 있다. --> 해당 계정은 genesis.json과 hardhat.config.ts에서 확인할 수 있음.

## API test

서버 실행 후 Apidog에서 Base URL을 `http://localhost:8080`으로 설정하고 테스트한다.

#### 기관별 토큰 컨트랙트 배포

- Method: `POST`
- URL: `/api/institutions/{institutionId}/contracts/deploy`
- 예시: `/api/institutions/1/contracts/deploy`
- Body: 없음 또는 JSON

요청 Body 예시:

```json
{
  "name": "CBDC"
}
```

`name`을 생략하면 중앙은행은 `CBDC`, 참가은행은 `DEPOSIT_TOKEN`으로 저장된다. 서버는 DB에서 기관의 `encrypted_key`를 복호화하고, 기관의 `rpc_endpoint`로 `Token.json` bytecode를 배포한 뒤 `contract` 테이블에 주소를 저장한다.

응답 예시:

```json
{
  "institutionId": 1,
  "institutionName": "Central Bank",
  "institutionType": "CENTRAL_BANK",
  "contractName": "CBDC",
  "contractAddress": "0x...",
  "transactionHash": "0x...",
  "rpcEndpoint": "http://localhost:8545",
  "signerAddress": "0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73"
}
```

#### 잔액 조회

- Method: `GET`
- URL: `/api/balance/{address}`
- 예시: `/api/balance/0xfe3b557e8fb62b89f4916b721be55ceb828dbd73`
- Body: 없음

응답 예시:

```json
{
  "address": "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73",
  "balance": 1000000000000000000000000
}
```

#### 지갑 생성

- Method: `POST`
- URL: `/api/wallet`
- Body: 없음

응답 예시:

```json
{
  "address": "0x...",
  "privateKey": "0x..."
}
```

#### 토큰 전송

- Method: `POST`
- URL: `/api/transfer`
- Body type: `JSON`

요청 Body 예시:

```json
{
  "from": "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73",
  "to": "0x받는_지갑_주소",
  "amount": 1000000000000000000
}
```

응답 예시:

```json
{
  "transactionHash": "0x...",
  "from": "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73",
  "to": "0x받는_지갑_주소",
  "status": "0x1"
}
```

- `amount`는 MHT의 최소 단위 기준이다. `1 MHT = 1000000000000000000`
- `from` 주소는 서버가 private key를 알고 있는 주소여야 한다.

## 4기관 초기 데이터

- Node-1: Central Bank, `http://localhost:8545`, `0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73`
- Node-2: Commercial Bank 1, `http://localhost:8547`, `0x627306090abaB3A6e1400e9345bC60c78a8BEf57`
- Node-3: Commercial Bank 2, `http://localhost:8549`, `0xf17f52151EbEF6C7334FAD080c5704D77216b732`
- Node-4: Commercial Bank 3, `http://localhost:8551`, `0xE9BA79E62a58225065bF24313896CD332dAFCB3C`

MySQL 초기화 시 `server/db/init/02-seed.sql`이 위 기관, 지갑, Besu 노드 정보를 넣는다. `institution_wallet.encrypted_key`는 개발용 `WALLET_AES_KEY_BASE64`로 AES-GCM 암호화된 값이다.

## 추가 설명

#### [Token.sol](./contracts/Token.sol)
```solidity
contract Token is ERC20 {
    address public owner;

    constructor() ERC20("My Hardhat Token", "MHT") {
        owner = msg.sender;
        _mint(msg.sender, 1_000_000 * 10 ** decimals());
    }
}
```

#### [genesis.json](./QBFT-Network/genesis.json)
```json
{
  "config" : {
    "chainId" : 1337,
    "berlinBlock" : 0,
    "qbft" : {
      "blockperiodseconds" : 2,
      "epochlength" : 30000,
      "requesttimeoutseconds" : 4
    }
  },
  "nonce" : "0x0",
  "timestamp" : "0x58ee40ba",
  "gasLimit" : "0x47b760",
  "difficulty" : "0x1",
  "mixHash" : "0x63746963616c2062797a616e74696e65206661756c7420746f6c6572616e6365",
  "coinbase" : "0x0000000000000000000000000000000000000000",
  "alloc" : {
    "fe3b557e8fb62b89f4916b721be55ceb828dbd73" : {
      "privateKey" : "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63",
      "comment" : "private key and this comment are ignored.  In a real chain, the private key should NOT be stored",
      "balance" : "0xad78ebc5ac6200000"
    },
    "627306090abaB3A6e1400e9345bC60c78a8BEf57" : {
      "privateKey" : "c87509a1c067bbde78beb793e6fa76530b6382a4c0241e5e4a9ec0a0f44dc0d3",
      "comment" : "private key and this comment are ignored.  In a real chain, the private key should NOT be stored",
      "balance" : "90000000000000000000000"
    },
    "f17f52151EbEF6C7334FAD080c5704D77216b732" : {
      "privateKey" : "ae6ae8e5ccbfb04590405997ee2d52d2b330726137b875053c36d94e974d162f",
      "comment" : "private key and this comment are ignored.  In a real chain, the private key should NOT be stored",
      "balance" : "90000000000000000000000"
    }
  },
  "extraData" : "0xf87aa00000000000000000000000000000000000000000000000000000000000000000f8549448545d2f1c9e6ba009eecdc2115b1eb205579d1494a68564b0d1bf751befbcaedd198aa2eb17ada8e79456f0a856d6759bf23408cee47b5b066bab84cd97945e0bc97008a86801d36ee5ea4c5caba352d88725c080c0"
}
```

- alloc 
	- 초기 계정 잔액을 미리 넣는 영역. 
	- genesis block이 만들어질 때 여기에 있는 주소들은 처음부터 native coin, 즉 Besu 체인의 ETH 같은 가스비용 코인을 갖는다.
	- 우리가 만든 MHT 토큰이 아니라 ETH 토큰을 말함!
- extraData
	- QBFT에서 초기 validator 목록
	- 이 네트워크에서 처음 블록을 만들 수 있는 노드들이 encoded 형태로 들어감
	- 현재 Node-1부터 Node-4까지의 validator 주소들이 포함되어 있다.


#### [hardhat.config.ts](./hardhat.config.ts)
```ts
const config: HardhatUserConfig = {
  solidity: "0.8.28",
  networks: {
    besu: {
      url: "http://localhost:8545",
      chainId: 1337,
      accounts: [
        "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63",
        "c87509a1c067bbde78beb793e6fa76530b6382a4c0241e5e4a9ec0a0f44dc0d3",
        "ae6ae8e5ccbfb04590405997ee2d52d2b330726137b875053c36d94e974d162f",
      ],
    },
  },
};
```

- accounts: 계정의 private key 목록
