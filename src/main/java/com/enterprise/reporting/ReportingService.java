package com.enterprise.reporting;

import com.enterprise.currency.ExchangeRateService;
import com.enterprise.tenant.Tenant;
import com.enterprise.tenant.TenantContext;
import com.enterprise.tenant.TenantRepository;
import com.enterprise.transaction.Transaction;
import com.enterprise.transaction.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReportingService {

    private final TransactionRepository transactionRepository;
    private final TenantRepository tenantRepository;
    private final ExchangeRateService exchangeRateService;

    public ReportingService(TransactionRepository transactionRepository,
                            TenantRepository tenantRepository,
                            ExchangeRateService exchangeRateService) {
        this.transactionRepository = transactionRepository;
        this.tenantRepository = tenantRepository;
        this.exchangeRateService = exchangeRateService;
    }

    public List<Transaction> getTransactionsBetween(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(23, 59, 59);
        return transactionRepository.findAll().stream()
                .filter(tx -> tx.getCreatedAt().isAfter(start) && tx.getCreatedAt().isBefore(end))
                .collect(Collectors.toList());
    }

    // Get tenant base currency
    private String getBaseCurrency() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) return "GBP";
        return tenantRepository.findById(tenantId)
                .map(Tenant::getBaseCurrency)
                .orElse("GBP");
    }

    public Map<LocalDate, Long> getDailyVolume(LocalDate startDate, LocalDate endDate) {
        List<Transaction> transactions = getTransactionsBetween(startDate, endDate);
        return transactions.stream()
                .collect(Collectors.groupingBy(
                        tx -> tx.getCreatedAt().toLocalDate(),
                        Collectors.counting()
                ));
    }

    public Map<LocalDate, BigDecimal> getDailyAverage(LocalDate startDate, LocalDate endDate) {
        List<Transaction> transactions = getTransactionsBetween(startDate, endDate);
        String baseCurrency = getBaseCurrency();
        return transactions.stream()
                .collect(Collectors.groupingBy(
                        tx -> tx.getCreatedAt().toLocalDate(),
                        Collectors.averagingDouble(tx -> exchangeRateService.convert(tx.getAmount(), tx.getCurrency(), baseCurrency).doubleValue())
                ))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> BigDecimal.valueOf(e.getValue()).setScale(2, RoundingMode.HALF_UP)
                ));
    }

    public Map<String, Object> getSummaryMetrics(LocalDate startDate, LocalDate endDate) {
        List<Transaction> transactions = getTransactionsBetween(startDate, endDate);
        String baseCurrency = getBaseCurrency();
        long total = transactions.size();
        if (total == 0) {
            return Map.of(
                    "totalTransactions", 0L,
                    "totalVolume", BigDecimal.ZERO,
                    "average", BigDecimal.ZERO,
                    "successRate", 0.0,
                    "settledCount", 0L,
                    "pendingCount", 0L,
                    "refundedCount", 0L,
                    "failedCount", 0L
            );
        }
        BigDecimal totalVolume = transactions.stream()
                .map(tx -> exchangeRateService.convert(tx.getAmount(), tx.getCurrency(), baseCurrency))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = totalVolume.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        long settled = transactions.stream().filter(tx -> "SETTLED".equals(tx.getStatus().name())).count();
        long pending = transactions.stream().filter(tx -> "PENDING".equals(tx.getStatus().name())).count();
        long refunded = transactions.stream().filter(tx -> "REFUNDED".equals(tx.getStatus().name())).count();
        long failed = transactions.stream().filter(tx -> "FAILED".equals(tx.getStatus().name())).count();
        double successRate = (double) settled / total * 100;
        return Map.of(
                "totalTransactions", total,
                "totalVolume", totalVolume,
                "average", average,
                "successRate", successRate,
                "settledCount", settled,
                "pendingCount", pending,
                "refundedCount", refunded,
                "failedCount", failed
        );
    }

    public Map<String, Long> getStatusDistribution(LocalDate startDate, LocalDate endDate) {
        List<Transaction> transactions = getTransactionsBetween(startDate, endDate);
        return transactions.stream()
                .collect(Collectors.groupingBy(
                        tx -> tx.getStatus().name(),
                        Collectors.counting()
                ));
    }

    public List<Map<String, Object>> getTopMerchants(LocalDate startDate, LocalDate endDate, int limit) {
        List<Transaction> transactions = getTransactionsBetween(startDate, endDate);
        String baseCurrency = getBaseCurrency();
        return transactions.stream()
                .collect(Collectors.groupingBy(
                        Transaction::getMerchantId,
                        Collectors.summingDouble(tx -> exchangeRateService.convert(tx.getAmount(), tx.getCurrency(), baseCurrency).doubleValue())
                ))
                .entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(limit)
                .map(e -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("merchantId", e.getKey());
                    map.put("totalAmount", BigDecimal.valueOf(e.getValue()).setScale(2, RoundingMode.HALF_UP));
                    return map;
                })
                .collect(Collectors.toList());
    }

    public Map<LocalDate, Double> getDailySuccessRate(LocalDate startDate, LocalDate endDate) {
        List<Transaction> transactions = getTransactionsBetween(startDate, endDate);
        Map<LocalDate, List<Transaction>> byDay = transactions.stream()
                .collect(Collectors.groupingBy(tx -> tx.getCreatedAt().toLocalDate()));
        Map<LocalDate, Double> result = new LinkedHashMap<>();
        for (Map.Entry<LocalDate, List<Transaction>> entry : byDay.entrySet()) {
            long total = entry.getValue().size();
            long settled = entry.getValue().stream()
                    .filter(tx -> "SETTLED".equals(tx.getStatus().name()))
                    .count();
            double rate = total == 0 ? 0 : (double) settled / total * 100;
            result.put(entry.getKey(), rate);
        }
        return result;
    }
}