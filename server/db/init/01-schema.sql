CREATE TABLE institution (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '기관 ID (BIGSERIAL)',
  name VARCHAR(100) NOT NULL COMMENT '기관명',
  type ENUM('CENTRAL_BANK', 'COMMERCIAL_BANK') NOT NULL COMMENT '기관 유형: CENTRAL_BANK=중앙은행, COMMERCIAL_BANK=참가은행',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
  PRIMARY KEY (id),
  UNIQUE KEY uq_institution_name (name)
) COMMENT='참여 기관 기본 정보';

CREATE TABLE institution_wallet (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '기관 지갑 ID (BIGSERIAL)',
  institution_id BIGINT UNSIGNED NOT NULL COMMENT '기관 ID',
  address CHAR(42) NOT NULL COMMENT '블록체인 지갑 주소 (0x + 40 hex, checksum 주소 길이)',
  encrypted_key TEXT NOT NULL COMMENT 'AES 암호화된 private key',
  role ENUM('ADMIN', 'CBDC_ISSUER', 'BANK') NOT NULL COMMENT '지갑 역할: ADMIN, CBDC_ISSUER, BANK',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
  PRIMARY KEY (id),
  UNIQUE KEY uq_institution_wallet_institution_id (institution_id),
  UNIQUE KEY uq_institution_wallet_address (address),
  CONSTRAINT chk_institution_wallet_address
    CHECK (REGEXP_LIKE(address, '^0x[0-9a-fA-F]{40}$')),
  CONSTRAINT fk_institution_wallet_institution
    FOREIGN KEY (institution_id) REFERENCES institution (id)
    ON DELETE CASCADE
) COMMENT='기관별 온체인 서명용 계정 정보';

CREATE TABLE contract (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '컨트랙트 ID (BIGSERIAL)',
  institution_id BIGINT UNSIGNED NOT NULL COMMENT '기관 ID',
  name ENUM('CBDC', 'DEPOSIT_TOKEN') NOT NULL COMMENT '컨트랙트 이름: CBDC=중앙은행 CBDC, DEPOSIT_TOKEN=참가은행 예금 토큰',
  address CHAR(42) NOT NULL COMMENT '온체인 컨트랙트 주소 (0x + 40 hex, checksum 주소 길이)',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
  PRIMARY KEY (id),
  UNIQUE KEY uq_contract_institution_id (institution_id),
  UNIQUE KEY uq_contract_address (address),
  CONSTRAINT chk_contract_address
    CHECK (REGEXP_LIKE(address, '^0x[0-9a-fA-F]{40}$')),
  CONSTRAINT fk_contract_institution
    FOREIGN KEY (institution_id) REFERENCES institution (id)
    ON DELETE CASCADE
) COMMENT='기관별 배포 스마트 컨트랙트 정보';

CREATE TABLE besu_node (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Besu 노드 ID (BIGSERIAL)',
  institution_id BIGINT UNSIGNED NOT NULL COMMENT '기관 ID',
  enode_url VARCHAR(512) NOT NULL COMMENT 'Besu 노드 enode URL (enode://...)',
  rpc_endpoint VARCHAR(255) NOT NULL COMMENT 'Besu 노드 JSON-RPC 엔드포인트',
  is_validator BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'validator 노드 여부',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
  PRIMARY KEY (id),
  UNIQUE KEY uq_besu_node_institution_id (institution_id),
  UNIQUE KEY uq_besu_node_enode_url (enode_url),
  UNIQUE KEY uq_besu_node_rpc_endpoint (rpc_endpoint),
  CONSTRAINT chk_besu_node_enode_url
    CHECK (enode_url LIKE 'enode://%'),
  CONSTRAINT chk_besu_node_rpc_endpoint
    CHECK (REGEXP_LIKE(rpc_endpoint, '^https?://.+')),
  CONSTRAINT fk_besu_node_institution
    FOREIGN KEY (institution_id) REFERENCES institution (id)
    ON DELETE CASCADE
) COMMENT='기관이 운영하는 Besu 노드 정보';
