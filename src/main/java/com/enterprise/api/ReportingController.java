package com.enterprise.api;

import com.enterprise.reporting.ReportingService;
import com.enterprise.transaction.TransactionRepository;   // <-- ADDED
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
public class ReportingController {

    private final ReportingService reportingService;
    private final TransactionRepository transactionRepository;   // <-- ADDED

    public ReportingController(ReportingService reportingService,
                               TransactionRepository transactionRepository) {   // <-- ADDED
        this.reportingService = reportingService;
        this.transactionRepository = transactionRepository;
    }

    @GetMapping
    public String dashboard(Model model) {
        var volume = reportingService.getDailyTransactionVolume(30);
        var avgAmount = reportingService.getAverageDailyAmount(30);
        var successRate = reportingService.getSuccessRate();
        var topMerchants = reportingService.getTopMerchants(5);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        List<String> labels = volume.keySet().stream()
                .sorted()
                .map(date -> date.format(formatter))
                .collect(Collectors.toList());

        List<Long> volumeData = labels.stream()
                .map(label -> volume.getOrDefault(LocalDate.parse(label, formatter), 0L))
                .collect(Collectors.toList());

        List<Double> avgData = labels.stream()
                .map(label -> avgAmount.getOrDefault(LocalDate.parse(label, formatter), BigDecimal.ZERO).doubleValue())
                .collect(Collectors.toList());

        model.addAttribute("labels", labels);
        model.addAttribute("volumeData", volumeData);
        model.addAttribute("avgData", avgData);
        model.addAttribute("successRate", successRate);
        model.addAttribute("topMerchants", topMerchants);

        return "admin/reports/dashboard";
    }

    @GetMapping("/data/transactions/csv")
    @ResponseBody
    public String exportTransactionsCSV() {
        var volume = reportingService.getDailyTransactionVolume(30);
        StringBuilder csv = new StringBuilder("Date,Volume\n");
        volume.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> csv.append(e.getKey()).append(",").append(e.getValue()).append("\n"));
        return csv.toString();
    }

    // Debug endpoint – to see raw data
    @GetMapping("/debug/volume")
    @ResponseBody
    public Map<String, Object> debugVolume() {
        var volume = reportingService.getDailyTransactionVolume(30);
        var avgAmount = reportingService.getAverageDailyAmount(30);
        return Map.of(
                "volume", volume,
                "avgAmount", avgAmount,
                "totalTransactions", transactionRepository.count()
        );
    }
}