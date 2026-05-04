package com.bpietrzak.stockmarket.repository;

import com.bpietrzak.stockmarket.model.Wallet;
import com.bpietrzak.stockmarket.model.WalletStock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WalletStockRepository extends JpaRepository<WalletStock, Long> {
    Optional<WalletStock> findByWalletAndName(Wallet wallet, String stockName);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ws FROM WalletStock ws WHERE ws.wallet = :wallet AND ws.name = :name")
    Optional<WalletStock> findByWalletAndNameWithLock(@Param("wallet") Wallet wallet, @Param("name") String name);

    List<WalletStock> findByWallet(Wallet wallet);
}
