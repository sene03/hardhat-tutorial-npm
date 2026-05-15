// 기관별 예금 토큰 발행 및 관리를 위한 상업은행 토큰 컨트랙트
// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.20;

import "./BaseToken.sol";

contract DepositToken is BaseToken {

    uint256 public institutionId;

    constructor(
        uint256 _institutionId,
        string memory bankName,
        string memory symbol_
    )
        BaseToken(
            string.concat(bankName, " Deposit Token"),
            symbol_
        )
    {
        institutionId = _institutionId;

        _mint(
            msg.sender,
            1_000_000 * 10 ** decimals()
        );
    }
}