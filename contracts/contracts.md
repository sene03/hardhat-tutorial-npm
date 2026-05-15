# BaseToken

운영자(Operator) 기반의 mint / burn / transfer 기능을 제공하는 공통 ERC20 토큰 컨트랙트입니다.

`CBDC`, `DepositToken` 등 실제 토큰 컨트랙트들이 상속하여 사용하는 베이스 컨트랙트입니다.

---

# 개요

기본 OpenZeppelin ERC20은 일반 사용자 중심 송금만 지원합니다.

본 프로젝트에서는 은행 및 Settlement 컨트랙트가 사용자 토큰을 대신 이동시키거나,
토큰을 발행/소각해야 하므로 운영자 권한 개념을 추가했습니다.

이를 위해 `BaseToken`은 아래 기능을 제공합니다.

- Operator 권한 관리
- Operator 기반 대리 송금
- Operator 기반 Mint
- Operator 기반 Burn

---

# 상속 구조

```solidity
BaseToken
 ├── CBDC
 └── DepositToken
```

---

# 주요 상태값

## owner

```solidity
address public owner;
```

컨트랙트를 배포한 주소입니다.

- operator 등록 가능
- 관리자 역할 수행

---

## operators

```solidity
mapping(address => bool) public operators;
```

운영 권한이 있는 주소 목록입니다.

예시:
- 중앙은행 지갑
- 기관 지갑
- Settlement 컨트랙트

---

# 생성자

```solidity
constructor(
    string memory name_,
    string memory symbol_
)
```

ERC20 이름과 심볼을 설정합니다.

배포 시:
- 배포자를 owner로 등록
- 배포자를 기본 operator로 등록

---

# Modifier

## onlyOwner

```solidity
modifier onlyOwner()
```

owner만 호출 가능하도록 제한합니다.

---

## onlyOperator

```solidity
modifier onlyOperator()
```

operator 권한이 있는 주소만 호출 가능하도록 제한합니다.

---

# 주요 기능

---

## setOperator

```solidity
function setOperator(
    address operator,
    bool approved
)
```

특정 주소에 operator 권한을 부여하거나 제거합니다.

### 접근 권한
- onlyOwner

### 사용 예시

Settlement 컨트랙트에 operator 권한 부여:

```solidity
setOperator(settlementAddress, true);
```

---

## operatorTransfer

```solidity
function operatorTransfer(
    address from,
    address to,
    uint256 amount
)
```

operator가 사용자 대신 토큰을 이동시킵니다.

### 특징

일반 ERC20 `transfer()` 와 달리:

```text
msg.sender != from
```

이어도 송금 가능합니다.

### 사용 목적

- 기관 내부 대리 송금
- Settlement 기반 정산 처리
- 사용자 gas 부담 제거

---

## mint

```solidity
function mint(
    address to,
    uint256 amount
)
```

새 토큰을 발행합니다.

### 접근 권한
- onlyOperator

### 사용 예시

타행이체 수신 시:

```text
받는 은행 사용자에게 예금토큰 신규 발행
```

---

## burn

```solidity
function burn(
    address from,
    uint256 amount
)
```

기존 토큰을 소각합니다.

### 접근 권한
- onlyOperator

### 사용 예시

타행이체 송신 시:

```text
보내는 은행 사용자 예금토큰 소각
```

---

# 타행이체 동작 흐름

Settlement 컨트랙트는 아래 흐름으로 기관 간 이체를 처리합니다.

```text
1. 보내는 사용자 DepositToken burn
2. 기관 간 CBDC reserve 이동
3. 받는 사용자 DepositToken mint
```

이 과정에서 Settlement 컨트랙트는
각 은행 토큰 컨트랙트의 operator 권한을 이용합니다.

---

# 이벤트(Event)

## OperatorUpdated

```solidity
event OperatorUpdated(address operator, bool approved);
```

operator 권한 변경 시 발생

---

## OperatorTransfer

```solidity
event OperatorTransfer(
    address operator,
    address from,
    address to,
    uint256 amount
);
```

operator 기반 송금 발생 시 기록

---

## Mint

```solidity
event Mint(
    address operator,
    address to,
    uint256 amount
);
```

토큰 발행 시 기록

---

## Burn

```solidity
event Burn(
    address operator,
    address from,
    uint256 amount
);
```

토큰 소각 시 기록

---

# 핵심 목적

`BaseToken`은 단순 ERC20이 아니라:

```text
기관/중앙은행/Settlement 기반 금융 정산 모델을 위한
운영자 제어형 토큰 구조
```

를 구현하기 위한 공통 베이스 컨트랙트입니다.