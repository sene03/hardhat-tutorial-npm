CREATE TABLE institution (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '식별자',
  institution_code VARCHAR(10) NOT NULL COMMENT '기관 코드',
  institution_name VARCHAR(100) NOT NULL COMMENT '은행명',
  account_number VARCHAR(50) NULL COMMENT '계좌번호',
  wallet_address VARCHAR(50) NULL COMMENT '지갑 주소',
  encrypted_private_key TEXT NULL COMMENT '암호화된 private key',
  enode_url VARCHAR(512) NULL COMMENT 'Besu 노드 enode URL',
  rpc_endpoint VARCHAR(255) NULL COMMENT 'Besu 노드 JSON-RPC 엔드포인트',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',
  PRIMARY KEY (id),
  UNIQUE KEY uq_institution_code (institution_code)
) COMMENT='기관 정보';

CREATE TABLE bank_wallet (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '식별자',
  institution_id BIGINT UNSIGNED NOT NULL COMMENT '은행 ID (institution.id)',
  wallet_address VARCHAR(50) NOT NULL COMMENT '계좌번호',
  balance DECIMAL(18, 2) NOT NULL DEFAULT 0 COMMENT '잔액',
  field2 TEXT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',
  PRIMARY KEY (id),
  UNIQUE KEY uq_bank_wallet_wallet_address (wallet_address),
  CONSTRAINT fk_bank_wallet_institution
    FOREIGN KEY (institution_id) REFERENCES institution (id)
    ON DELETE CASCADE
) COMMENT='은행 지갑 정보';

CREATE TABLE contract (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '식별자',
  institution_id BIGINT UNSIGNED NOT NULL COMMENT '기관 ID',
  name ENUM('CBDC', 'DEPOSIT_TOKEN', 'CONTRACT') NULL COMMENT '컨트랙트 이름',
  address CHAR(42) NULL COMMENT '컨트랙트 주소',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',
  PRIMARY KEY (id),
  UNIQUE KEY uq_contract_institution_name (institution_id, name),
  CONSTRAINT chk_contract_address
    CHECK (address IS NULL OR REGEXP_LIKE(address, '^0x[0-9a-fA-F]{40}$')),
  CONSTRAINT fk_contract_institution
    FOREIGN KEY (institution_id) REFERENCES institution (id)
    ON DELETE CASCADE
) COMMENT='기관별 스마트 컨트랙트 정보';