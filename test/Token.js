const { expect } = require("chai");
const { loadFixture } = require("@nomicfoundation/hardhat-toolbox/network-helpers");

describe("Token contract", function () {
  async function deployTokenFixture() {
    const [owner, addr1, addr2] = await ethers.getSigners();
    const hardhatToken = await ethers.deployContract("Token");
    await hardhatToken.waitForDeployment();
    return { hardhatToken, owner, addr1, addr2 };
  }

  describe("Deployment", function () {
    it("Should set the right owner", async function () {
      const { hardhatToken, owner } = await loadFixture(deployTokenFixture);
      expect(await hardhatToken.owner()).to.equal(owner.address);
    });

    it("Should assign the total supply of tokens to the owner", async function () {
      const { hardhatToken, owner } = await loadFixture(deployTokenFixture);
      const ownerBalance = await hardhatToken.balanceOf(owner.address);
      expect(await hardhatToken.totalSupply()).to.equal(ownerBalance);
    });

    it("Should have correct name and symbol", async function () {
      const { hardhatToken } = await loadFixture(deployTokenFixture);
      expect(await hardhatToken.name()).to.equal("My Hardhat Token");
      expect(await hardhatToken.symbol()).to.equal("MHT");
    });

    it("Should have 18 decimals", async function () {
      const { hardhatToken } = await loadFixture(deployTokenFixture);
      expect(await hardhatToken.decimals()).to.equal(18);
    });
  });

  describe("Transactions", function () {
    it("Should transfer tokens between accounts", async function () {
      const { hardhatToken, owner, addr1, addr2 } = await loadFixture(deployTokenFixture);

      await expect(
        hardhatToken.transfer(addr1.address, 50)
      ).to.changeTokenBalances(hardhatToken, [owner, addr1], [-50, 50]);

      await expect(
        hardhatToken.connect(addr1).transfer(addr2.address, 50)
      ).to.changeTokenBalances(hardhatToken, [addr1, addr2], [-50, 50]);
    });

    it("Should emit Transfer events", async function () {
      const { hardhatToken, owner, addr1, addr2 } = await loadFixture(deployTokenFixture);

      await expect(hardhatToken.transfer(addr1.address, 50))
        .to.emit(hardhatToken, "Transfer")
        .withArgs(owner.address, addr1.address, 50);

      await expect(hardhatToken.connect(addr1).transfer(addr2.address, 50))
        .to.emit(hardhatToken, "Transfer")
        .withArgs(addr1.address, addr2.address, 50);
    });

    it("Should fail if sender doesn't have enough tokens", async function () {
      const { hardhatToken, owner, addr1 } = await loadFixture(deployTokenFixture);
      const initialOwnerBalance = await hardhatToken.balanceOf(owner.address);

      await expect(
        hardhatToken.connect(addr1).transfer(owner.address, 1)
      ).to.be.revertedWithCustomError(hardhatToken, "ERC20InsufficientBalance");

      expect(await hardhatToken.balanceOf(owner.address)).to.equal(initialOwnerBalance);
    });
  });

  describe("Allowance", function () {
    it("Should approve spender and update allowance", async function () {
      const { hardhatToken, owner, addr1 } = await loadFixture(deployTokenFixture);

      await hardhatToken.approve(addr1.address, 100);
      expect(await hardhatToken.allowance(owner.address, addr1.address)).to.equal(100);
    });

    it("Should emit Approval event", async function () {
      const { hardhatToken, owner, addr1 } = await loadFixture(deployTokenFixture);

      await expect(hardhatToken.approve(addr1.address, 100))
        .to.emit(hardhatToken, "Approval")
        .withArgs(owner.address, addr1.address, 100);
    });

    it("Should transferFrom using allowance", async function () {
      const { hardhatToken, owner, addr1, addr2 } = await loadFixture(deployTokenFixture);

      await hardhatToken.approve(addr1.address, 100);

      await expect(
        hardhatToken.connect(addr1).transferFrom(owner.address, addr2.address, 100)
      ).to.changeTokenBalances(hardhatToken, [owner, addr2], [-100, 100]);

      expect(await hardhatToken.allowance(owner.address, addr1.address)).to.equal(0);
    });

    it("Should fail transferFrom if allowance exceeded", async function () {
      const { hardhatToken, owner, addr1, addr2 } = await loadFixture(deployTokenFixture);

      await hardhatToken.approve(addr1.address, 50);

      await expect(
        hardhatToken.connect(addr1).transferFrom(owner.address, addr2.address, 100)
      ).to.be.revertedWithCustomError(hardhatToken, "ERC20InsufficientAllowance");
    });
  });
});
