// 기관 간 토큰 정산을 하나의 트랜잭션으로 처리하는 Settlement 컨트랙트
// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.20;

interface IBankToken {
    function burn(address from, uint256 amount) external returns (bool);
    function mint(address to, uint256 amount) external returns (bool);
}

interface ICBDC {
    function operatorTransfer(address from, address to, uint256 amount) external returns (bool);
}

contract Settlement {
    address public owner;
    address public cbdc;

    mapping(uint256 => address) public bankToken;
    mapping(uint256 => address) public bankReserveWallet;

    event BankUpdated(
        uint256 indexed institutionId,
        address indexed token,
        address indexed reserveWallet
    );

    event Settled(
        uint256 indexed fromInstitutionId,
        uint256 indexed toInstitutionId,
        address indexed fromUser,
        address toUser,
        uint256 amount
    );

    modifier onlyOwner() {
        require(msg.sender == owner, "Settlement: caller is not owner");
        _;
    }

    constructor(address _cbdc) {
        require(_cbdc != address(0), "Settlement: cbdc is zero address");

        owner = msg.sender;
        cbdc = _cbdc;
    }

    function setBank(
        uint256 institutionId,
        address token,
        address reserveWallet
    ) external onlyOwner {
        require(institutionId != 0, "Settlement: invalid institution");
        require(token != address(0), "Settlement: token is zero address");
        require(reserveWallet != address(0), "Settlement: reserve is zero address");

        bankToken[institutionId] = token;
        bankReserveWallet[institutionId] = reserveWallet;

        emit BankUpdated(institutionId, token, reserveWallet);
    }

    function settle(
        uint256 fromInstitutionId,
        uint256 toInstitutionId,
        address fromUser,
        address toUser,
        uint256 amount
    ) external returns (bool) {
        require(fromInstitutionId != toInstitutionId, "Settlement: same institution");
        require(fromUser != address(0), "Settlement: fromUser is zero address");
        require(toUser != address(0), "Settlement: toUser is zero address");
        require(amount > 0, "Settlement: invalid amount");

        address fromToken = bankToken[fromInstitutionId];
        address toToken = bankToken[toInstitutionId];

        address fromReserve = bankReserveWallet[fromInstitutionId];
        address toReserve = bankReserveWallet[toInstitutionId];

        require(fromToken != address(0), "Settlement: from token not set");
        require(toToken != address(0), "Settlement: to token not set");
        require(fromReserve != address(0), "Settlement: from reserve not set");
        require(toReserve != address(0), "Settlement: to reserve not set");

        require(IBankToken(fromToken).burn(fromUser, amount), "Settlement: burn failed");
        require(ICBDC(cbdc).operatorTransfer(fromReserve, toReserve, amount), "Settlement: cbdc transfer failed");
        require(IBankToken(toToken).mint(toUser, amount), "Settlement: mint failed");

        emit Settled(
            fromInstitutionId,
            toInstitutionId,
            fromUser,
            toUser,
            amount
        );

        return true;
    }
}