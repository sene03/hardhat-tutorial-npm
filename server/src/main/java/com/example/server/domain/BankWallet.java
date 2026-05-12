package com.example.server.domain;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
@Entity
@Table(name = "bank_wallet")
public class BankWallet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "institution_id", nullable = false)
    private Long institutionId;
    @Column(name = "wallet_address", nullable = false, length = 50)
    private String walletAddress;
    @Column(name = "encrypted_private_key", nullable = false, columnDefinition = "TEXT")
    private String encryptedPrivateKey;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    protected BankWallet() {
    }
    public BankWallet(
            Long institutionId,
            String walletAddress,
            String encryptedPrivateKey
    ) {
        this.institutionId = institutionId;
        this.walletAddress = walletAddress;
        this.encryptedPrivateKey = encryptedPrivateKey;
        this.balance = BigDecimal.ZERO;
    }
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.balance == null) {
            this.balance = BigDecimal.ZERO;
        }
    }
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
    public Long getId() {
        return id;
    }
    public Long getInstitutionId() {
        return institutionId;
    }
    public String getWalletAddress() {
        return walletAddress;
    }
    public String getEncryptedPrivateKey() {
        return encryptedPrivateKey;
    }
    public BigDecimal getBalance() {
        return balance;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}