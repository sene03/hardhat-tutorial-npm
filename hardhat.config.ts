import { HardhatUserConfig } from "hardhat/config";
import "@nomicfoundation/hardhat-toolbox";

const config: HardhatUserConfig = {
  solidity: "0.8.28",
  networks: {
    besu: {
      url: "http://localhost:8545",
      chainId: 1337,
      gasPrice: 0,
      accounts: [
        "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63",
        "c87509a1c067bbde78beb793e6fa76530b6382a4c0241e5e4a9ec0a0f44dc0d3",
        "ae6ae8e5ccbfb04590405997ee2d52d2b330726137b875053c36d94e974d162f",
        "fdad4ce4c7c8382ea0357ad12071156ba54963cabed82f415e24c43f537fe784",
      ],
    },
  },
};

export default config;
