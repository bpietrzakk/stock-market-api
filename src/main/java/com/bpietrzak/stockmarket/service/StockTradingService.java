package com.bpietrzak.stockmarket.service;

import com.bpietrzak.stockmarket.model.AuditLog;
import com.bpietrzak.stockmarket.model.BankStock;
import com.bpietrzak.stockmarket.model.Wallet;
import com.bpietrzak.stockmarket.model.WalletStock;
import com.bpietrzak.stockmarket.model.enums.TransactionType;
import com.bpietrzak.stockmarket.repository.BankStockRepository;
import com.bpietrzak.stockmarket.repository.WalletRepository;
import com.bpietrzak.stockmarket.repository.WalletStockRepository;
import com.bpietrzak.stockmarket.repository.AuditLogRepository;
import com.bpietrzak.stockmarket.exception.InvalidOperationException;
import com.bpietrzak.stockmarket.exception.NotFoundException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class StockTradingService {
    private final BankStockRepository bankStockRepository;
    private final WalletRepository walletRepository;
    private final WalletStockRepository walletStockRepository;
    private final AuditLogRepository auditLogRepository;

    public StockTradingService(
            BankStockRepository bankStockRepository,
            WalletRepository walletRepository,
            WalletStockRepository walletStockRepository,
            AuditLogRepository auditLogRepository
            ) {
        this.bankStockRepository = bankStockRepository;
        this.walletRepository = walletRepository;
        this.walletStockRepository = walletStockRepository;
        this.auditLogRepository = auditLogRepository;
    }

    // methods buy() and sell()

    @Transactional
    public void buy(UUID walletId, String stockName) {
        // check if stock exist in the bank
        BankStock bankStock = bankStockRepository.findByName(stockName).orElseThrow(() -> new NotFoundException("Stock not found: " + stockName));

        // check if there are any stocks left to sell in bank
        if (bankStock.getQuantity() <= 0) {
            throw new InvalidOperationException("Stock " + stockName + " is out of stock");
        }

        // check if wallet exists, if not -> create
        Wallet wallet = walletRepository.findById(walletId).orElseGet(() -> walletRepository.save(new Wallet(walletId)));

        // create wallet stock entry on first buy of this stock
        WalletStock walletStock = walletStockRepository.findByWalletAndNameWithLock(wallet, stockName).orElseGet(() -> walletStockRepository.save(new WalletStock(wallet, stockName, 0)));

        // transfer stock from bank to wallet
        walletStock.setQuantity(walletStock.getQuantity() + 1);
        bankStock.setQuantity(bankStock.getQuantity() - 1);

        // audit trail
        auditLogRepository.save(new AuditLog(TransactionType.BUY, walletId, stockName));
    }

    @Transactional
    public void sell(UUID walletId, String stockName) {
        // check if stock exist in bank
        BankStock bankStock = bankStockRepository.findByName(stockName).orElseThrow(() -> new NotFoundException("Stock not found: " + stockName));

        // check if wallet exist, if not -> create it
        Wallet wallet = walletRepository.findById(walletId).orElseGet(() -> walletRepository.save(new Wallet(walletId)));

        // check if there are any stocks left to sell in wallet
        WalletStock walletStock = walletStockRepository.findByWalletAndNameWithLock(wallet, stockName)
                .filter(ws -> ws.getQuantity() > 0)
                .orElseThrow(() -> new InvalidOperationException("Wallet doesn't have enough stocks: " + stockName));

        // transfer stock from wallet to bank
        walletStock.setQuantity(walletStock.getQuantity() - 1);
        bankStock.setQuantity(bankStock.getQuantity() + 1);

        // audit trail
        auditLogRepository.save(new AuditLog(TransactionType.SELL, walletId, stockName));
    }
}
