// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.0;

import "@openzeppelin/contracts/token/ERC20/ERC20.sol";

contract Token is ERC20 {
    address public owner;

    constructor() ERC20("My Hardhat Token", "MHT") {
        owner = msg.sender;
        _mint(msg.sender, 1_000_000 * 10 ** decimals());
    }
}
