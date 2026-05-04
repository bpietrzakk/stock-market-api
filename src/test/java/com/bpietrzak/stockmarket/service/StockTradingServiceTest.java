package com.bpietrzak.stockmarket.service;

import com.bpietrzak.stockmarket.exception.InvalidOperationException;
import com.bpietrzak.stockmarket.exception.NotFoundException;
import com.bpietrzak.stockmarket.model.BankStock;
import com.bpietrzak.stockmarket.model.Wallet;
import com.bpietrzak.stockmarket.model.WalletStock;
import com.bpietrzak.stockmarket.model.AuditLog;
import com.bpietrzak.stockmarket.model.enums.TransactionType;
import com.bpietrzak.stockmarket.repository.AuditLogRepository;
import com.bpietrzak.stockmarket.repository.BankStockRepository;
import com.bpietrzak.stockmarket.repository.WalletRepository;
import com.bpietrzak.stockmarket.repository.WalletStockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockTradingServiceTest {

    // mock data
    @Mock private BankStockRepository bankStockRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private WalletStockRepository walletStockRepository;
    @Mock private AuditLogRepository auditLogRepository;

    // create mock service
    @InjectMocks private StockTradingService service;

    @Test
    void buyShouldThrowNotFoundWhenStockDoesNotExist() {
        // create data and mock
        UUID walletId = UUID.randomUUID();
        String stockName = "DontExist";
        when(bankStockRepository.findByName(stockName)).thenReturn(Optional.empty());

        // call and check if an exception was thrown
        assertThatThrownBy(() -> service.buy(walletId, stockName))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(stockName);
    }
    @Test
    void buyShouldThrowInvalidOperationWhenBankHasNoStocks() {
        // create data and mock
        UUID walletId = UUID.randomUUID();
        String stockName = "ZeroInBank";
        BankStock emptyStock = new BankStock(stockName, 0);
        when(bankStockRepository.findByName(stockName)).thenReturn(Optional.of(emptyStock));

        // call and check if an exception was thrown
        assertThatThrownBy(() -> service.buy(walletId, stockName))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining(stockName);
    }

    @Test
    void sellShouldThrowNotFoundWhenStockDoesNotExist() {
        // create data and mock
        UUID walletId = UUID.randomUUID();
        String stockName = "DontExist";
        when(bankStockRepository.findByName(stockName)).thenReturn(Optional.empty());

        // call and check if an exception was thrown
        assertThatThrownBy(() -> service.sell(walletId, stockName))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(stockName);
    }

    @Test
    void sellShouldThrowInvalidOperationWhenWalletStockDoesNotExist() {
        // create data and mock
        UUID walletId = UUID.randomUUID();
        Wallet wallet = new Wallet(walletId);
        String stockName = "WalletStockDoesNotExist";
        BankStock bankStock = new BankStock(stockName, 1);
        when(bankStockRepository.findByName(stockName)).thenReturn(Optional.of(bankStock));
        when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));
        when(walletStockRepository.findByWalletAndNameWithLock(wallet, stockName)).thenReturn(Optional.empty());

        // call and check if an exception was thrown
        assertThatThrownBy(() -> service.sell(walletId, stockName))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining(stockName);
    }

    @Test
    void sellShouldThrowInvalidOperationWhenWalletStockQuantityIsZero() {
        // create data and mock
        UUID walletId = UUID.randomUUID();
        Wallet wallet = new Wallet(walletId);
        String stockName = "WalletStockQuantityIsZero";
        BankStock bankStock = new BankStock(stockName, 1);
        WalletStock walletStock = new WalletStock(wallet, stockName, 0);
        when(bankStockRepository.findByName(stockName)).thenReturn(Optional.of(bankStock));
        when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));
        when(walletStockRepository.findByWalletAndNameWithLock(wallet, stockName)).thenReturn(Optional.of(walletStock));

        // call and check if an exception was thrown
        assertThatThrownBy(() -> service.sell(walletId, stockName))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining(stockName);
    }

    @Test
    void buyShouldTransferStockFromBankToWalletAndLogIt() {
        // create data and mock
        UUID walletId = UUID.randomUUID();
        Wallet wallet = new Wallet(walletId);
        String stockName = "example";
        BankStock bankStock = new BankStock(stockName, 1);
        WalletStock walletStock = new WalletStock(wallet, stockName, 1);
        when(bankStockRepository.findByName(stockName)).thenReturn(Optional.of(bankStock));
        when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));
        when(walletStockRepository.findByWalletAndNameWithLock(wallet, stockName)).thenReturn(Optional.of(walletStock));

        // call
        service.buy(walletId, stockName);

        // check stock transfer
        assertThat(bankStock.getQuantity()).isEqualTo(0);
        assertThat(walletStock.getQuantity()).isEqualTo(2);

        // check audit log
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog savedLog = captor.getValue();

        assertThat(savedLog.getType()).isEqualTo(TransactionType.BUY);
        assertThat(savedLog.getWalletId()).isEqualTo(walletId);
        assertThat(savedLog.getStockName()).isEqualTo(stockName);
    }

    @Test
    void sellShouldTransferStockFromWalletToBankAndLogIt() {
        // create data and mock
        UUID walletId = UUID.randomUUID();
        Wallet wallet = new Wallet(walletId);
        String stockName = "example";
        BankStock bankStock = new BankStock(stockName, 1);
        WalletStock walletStock = new WalletStock(wallet, stockName, 1);
        when(bankStockRepository.findByName(stockName)).thenReturn(Optional.of(bankStock));
        when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));
        when(walletStockRepository.findByWalletAndNameWithLock(wallet, stockName)).thenReturn(Optional.of(walletStock));

        // call
        service.sell(walletId, stockName);

        // check stock transfer
        assertThat(bankStock.getQuantity()).isEqualTo(2);
        assertThat(walletStock.getQuantity()).isEqualTo(0);

        // check audit log
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog savedLog = captor.getValue();

        assertThat(savedLog.getType()).isEqualTo(TransactionType.SELL);
        assertThat(savedLog.getWalletId()).isEqualTo(walletId);
        assertThat(savedLog.getStockName()).isEqualTo(stockName);
    }

    @Test
    void buyShouldCreateWalletWhenItDoesNotExist() {
        // create data and mock
        UUID walletId = UUID.randomUUID();
        String stockName = "example";
        BankStock bankStock = new BankStock(stockName, 1);
        WalletStock walletStock = new WalletStock(null, stockName, 0);
        when(bankStockRepository.findByName(stockName)).thenReturn(Optional.of(bankStock));
        when(walletRepository.findById(walletId)).thenReturn(Optional.empty());
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));
        when(walletStockRepository.findByWalletAndNameWithLock(any(Wallet.class), eq(stockName))).thenReturn(Optional.of(walletStock));

        // call
        service.buy(walletId, stockName);

        // check wallet was created
        verify(walletRepository).save(any(Wallet.class));

        // check stock transfer
        assertThat(bankStock.getQuantity()).isEqualTo(0);
        assertThat(walletStock.getQuantity()).isEqualTo(1);

        // check audit log
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog savedLog = captor.getValue();
        assertThat(savedLog.getType()).isEqualTo(TransactionType.BUY);
        assertThat(savedLog.getWalletId()).isEqualTo(walletId);
        assertThat(savedLog.getStockName()).isEqualTo(stockName);
    }

    @Test
    void buyShouldCreateWalletStockWhenItDoesNotExist() {
        // create data and mock
        UUID walletId = UUID.randomUUID();
        Wallet wallet = new Wallet(walletId);
        String stockName = "example";
        BankStock bankStock = new BankStock(stockName, 1);
        when(bankStockRepository.findByName(stockName)).thenReturn(Optional.of(bankStock));
        when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));
        when(walletStockRepository.findByWalletAndNameWithLock(wallet, stockName)).thenReturn(Optional.empty());
        when(walletStockRepository.save(any(WalletStock.class))).thenAnswer(inv -> inv.getArgument(0));

        // call
        service.buy(walletId, stockName);

        // check walletStock was created
        verify(walletStockRepository).save(any(WalletStock.class));

        // check stock transfer
        assertThat(bankStock.getQuantity()).isEqualTo(0);

        // check audit log
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog savedLog = captor.getValue();
        assertThat(savedLog.getType()).isEqualTo(TransactionType.BUY);
        assertThat(savedLog.getWalletId()).isEqualTo(walletId);
        assertThat(savedLog.getStockName()).isEqualTo(stockName);
    }
}
