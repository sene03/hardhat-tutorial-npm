package com.example.server.api;

import com.example.server.dto.BalanceResponse;
import com.example.server.dto.TransferRequest;
import com.example.server.dto.TransferResponse;
import com.example.server.dto.WalletResponse;
import com.example.server.service.TokenService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TokenController {

	private final TokenService tokenService;

	public TokenController(TokenService tokenService) {
		this.tokenService = tokenService;
	}

	@GetMapping("/balance/{address}")
	public BalanceResponse balance(@PathVariable String address) {
		return tokenService.balanceOf(address);
	}

	@PostMapping("/transfer")
	public TransferResponse transfer(@RequestBody TransferRequest request) {
		return tokenService.transfer(request);
	}

	@PostMapping("/wallet")
	public WalletResponse wallet() {
		return tokenService.createWallet();
	}
}
