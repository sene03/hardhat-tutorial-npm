// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.0;

import "@openzeppelin/contracts/token/ERC20/ERC20.sol";

contract Token is ERC20 {
    address public owner;
    mapping(address => bool) public operators;

    event OperatorUpdated(address indexed operator, bool approved);
    event OperatorTransfer(address indexed operator, address indexed from, address indexed to, uint256 amount);

    modifier onlyOwner() {
        require(msg.sender == owner, "Token: caller is not owner");
        _;
    }

    modifier onlyOperator() {
        require(operators[msg.sender], "Token: caller is not operator");
        _;
    }

    constructor() ERC20("My Hardhat Token", "MHT") {
        owner = msg.sender;
        operators[msg.sender] = true;
        _mint(msg.sender, 1_000_000 * 10 ** decimals());
    }

    function setOperator(address operator, bool approved) external onlyOwner {
        require(operator != address(0), "Token: operator is zero address");
        operators[operator] = approved;
        emit OperatorUpdated(operator, approved);
    }

    function operatorTransfer(address from, address to, uint256 amount) external onlyOperator returns (bool) {
        _transfer(from, to, amount);
        emit OperatorTransfer(msg.sender, from, to, amount);
        return true;
    }
}
