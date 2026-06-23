package com.enterprise.reporting;

import com.enterprise.transaction.Transaction;
import com.enterprise.transaction.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReportingService {

    private final TransactionRepository transactionRepository;

    public ReportingService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    // Get transaction volume per day for the last 30 days
    public Map<LocalDate, Long> getDailyTransactionVolume(int days) {
        LocalDateTime start = LocalDateTime.now().minusDays(days);
        List<Transaction> transactions = transactionRepository.findAll()
                .stream()
                .filter(tx -> tx.getCreatedAt().isAfter(start))
                .collect(Collectors.toList());

        return transactions.stream()
                .collect(Collectors.groupingBy(
                        tx -> tx.getCreatedAt().toLocalDate(),
                        Collectors.counting()
                ));
    }

    // Get success rate (percentage of SETTLED vs total)
    public Map<String, Double> getSuccessRate() {
        long total = transactionRepository.count();
        if (total == 0) return Map.of("success", 0.0, "failed", 0.0);
        long settled = transactionRepository.findAll().stream()
                .filter(tx -> "SETTLED".equals(tx.getStatus().name()))
                .count();
        double success = (double) settled / total * 100;
        double failed = 100 - success;
        return Map.of("success", success, "failed", failed);
    }

    // Average transaction value per day (last 30 days)
    public Map<LocalDate, BigDecimal> getAverageDailyAmount(int days) {
        LocalDateTime start = LocalDateTime.now().minusDays(days);
        List<Transaction> transactions = transactionRepository.findAll()
                .stream()
                .filter(tx -> tx.getCreatedAt().isAfter(start))
                .collect(Collectors.toList());

        return transactions.stream()
                .collect(Collectors.groupingBy(
                        tx -> tx.getCreatedAt().toLocalDate(),
                        Collectors.averagingDouble(tx -> tx.getAmount().doubleValue())
                ))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> BigDecimal.valueOf(e.getValue())
                ));
    }

    // Top merchants by total transaction amount
    public List<Map<String, Object>> getTopMerchants(int limit) {
        return transactionRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        Transaction::getMerchantId,
                        Collectors.summingDouble(tx -> tx.getAmount().doubleValue())
                ))
                .entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(limit)
                .map(e -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("merchantId", e.getKey());
                    map.put("totalAmount", BigDecimal.valueOf(e.getValue()));
                    return map;
                })
                .collect(Collectors.toList());
    }
}