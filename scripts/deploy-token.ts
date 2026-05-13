import hre from "hardhat";

async function main() {
  const [deployer] = await hre.ethers.getSigners();

  console.log("Deploying contracts with:", deployer.address);

  // CBDC
  const cbdc = await hre.ethers.deployContract("CBDCToken");
  await cbdc.waitForDeployment();

  console.log("CBDC deployed:", await cbdc.getAddress());

  // Bank Deposit Token
const depositToken = await hre.ethers.deployContract(
  "DepositToken",
  [2, "Bank2", "B2DT"]
);

  await depositToken.waitForDeployment();

  console.log(
    "DepositToken deployed:",
    await depositToken.getAddress()
  );

  // Settlement
  const settlement = await hre.ethers.deployContract(
    "Settlement",
    [await cbdc.getAddress()]
  );

  await settlement.waitForDeployment();

  console.log(
    "Settlement deployed:",
    await settlement.getAddress()
  );
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});