// 운영자 기반 mint/burn/transfer 기능을 제공하는 공통 ERC20 토큰 컨트랙트
// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/token/ERC20/ERC20.sol";

abstract contract BaseToken is ERC20 {
    address public owner;

    mapping(address => bool) public operators;

    event OperatorUpdated(address indexed operator, bool approved);

    event OperatorTransfer(
        address indexed operator,
        address indexed from,
        address indexed to,
        uint256 amount
    );

    event Mint(
        address indexed operator,
        address indexed to,
        uint256 amount
    );

    event Burn(
        address indexed operator,
        address indexed from,
        uint256 amount
    );

    modifier onlyOwner() {
        require(msg.sender == owner, "caller is not owner");
        _;
    }

    modifier onlyOperator() {
        require(operators[msg.sender], "caller is not operator");
        _;
    }

    constructor(
        string memory name_,
        string memory symbol_
    ) ERC20(name_, symbol_) {
        owner = msg.sender;
        operators[msg.sender] = true;
    }

    function setOperator(
        address operator,
        bool approved
    ) external onlyOwner {
        require(operator != address(0), "zero address");

        operators[operator] = approved;

        emit OperatorUpdated(operator, approved);
    }

    function operatorTransfer(
        address from,
        address to,
        uint256 amount
    ) external onlyOperator returns (bool) {
        _transfer(from, to, amount);

        emit OperatorTransfer(
            msg.sender,
            from,
            to,
            amount
        );

        return true;
    }

    function mint(
        address to,
        uint256 amount
    ) external onlyOperator returns (bool) {
        _mint(to, amount);

        emit Mint(
            msg.sender,
            to,
            amount
        );

        return true;
    }

    function burn(
        address from,
        uint256 amount
    ) external onlyOperator returns (bool) {
        _burn(from, amount);

        emit Burn(
            msg.sender,
            from,
            amount
        );

        return true;
    }
}