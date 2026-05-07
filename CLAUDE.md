# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This repository has two sub-projects that work together:

1. **Hardhat project** (root) — compiles and deploys an ERC-20 smart contract (`Token.sol`) to a local Hyperledger Besu QBFT network.
2. **Spring Boot server** (`server/`) — a REST API that interacts with the deployed contract via web3j.

## Hardhat Commands (run from repo root)

```bash
npx hardhat compile                              # Compile contracts → artifacts/
npx hardhat test                                 # Run all tests against Hardhat in-process network
npx hardhat test test/Token.js                  # Run a single test file
REPORT_GAS=true npx hardhat test               # Test with gas usage report
npx hardhat run scripts/deploy-token.ts --network besu   # Deploy to local Besu network
```

## Spring Boot Commands (run from `server/`)

```bash
./gradlew bootRun          # Start Spring Boot server (port 8080)
./gradlew build            # Build JAR
./gradlew test             # Run tests
./gradlew test --tests "com.example.server.SomeTest"   # Run a single test class
```

## Besu QBFT Network (run from `QBFT-Network/`)

```bash
docker compose up -d       # Start all 4 Besu nodes
docker compose down        # Stop network
docker compose logs -f node1   # Follow node1 logs
```

Node1 is the primary RPC endpoint: `http://localhost:8545` (chain ID 1337).  
Nodes 2–4 expose RPC on ports 8547, 8549, 8551 respectively.

## Architecture

```
contracts/Token.sol          ← ERC-20 (OpenZeppelin), symbol MHT, 1M tokens minted to deployer
scripts/deploy-token.ts      ← Deploys Token to --network besu using hardhat.config.ts signers
artifacts/contracts/Token.sol/Token.json  ← ABI used by Spring Boot server
QBFT-Network/genesis.json    ← Chain config + 3 pre-funded accounts
QBFT-Network/docker-compose.yml  ← 4-node QBFT cluster
server/                      ← Spring Boot 4.x / Java 21 REST API
  build.gradle               ← Dependencies (add web3j here when implementing)
  src/main/resources/application.yaml  ← App config (add besu.rpc-url, contract-address, private-key)
```

### Pre-funded Genesis Accounts (chainId 1337)

| Address | Private Key |
|---------|-------------|
| `0xfe3b557e8fb62b89f4916b721be55ceb828dbd73` | `8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63` |
| `0x627306090abaB3A6e1400e9345bC60c78a8BEf57` | `c87509a1c067bbde78beb793e6fa76530b6382a4c0241e5e4a9ec0a0f44dc0d3` |
| `0xf17f52151EbEF6C7334FAD080c5704D77216b732` | `ae6ae8e5ccbfb04590405997ee2d52d2b330726137b875053c36d94e974d162f` |

The same keys appear in `hardhat.config.ts` under the `besu` network.

### Spring Boot ↔ Besu Integration Pattern

The server calls the ERC-20 contract using web3j's raw function encoding (no generated wrapper needed). The typical flow:

1. Load `Credentials` from a private key string.
2. Build a `Web3j` instance pointing at `http://localhost:8545`.
3. Encode function calls using `Function` + `FunctionEncoder` from `org.web3j.abi`.
4. Send signed transactions via `RawTransactionManager` and wait for receipt.
5. For read calls (`balanceOf`) use `EthCall` with no signing.

The contract ABI is at `artifacts/contracts/Token.sol/Token.json` — copy the `abi` array into a constant in the service class or load it at runtime.

### API Surface (planned)

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/balance/{address}` | ERC-20 `balanceOf` |
| POST | `/api/transfer` | Signed `transfer(to, amount)` — `from` + private key in body or config |
| POST | `/api/wallet` | Generate new key pair, return `{address, privateKey}` |

Private keys and contract address are injected via `application.yaml` / environment variables, not hardcoded.
