package com.enterprise.api;

import com.enterprise.reporting.ExportRequest;
import com.enterprise.reporting.ExportService;
import com.enterprise.reporting.ReportingService;
import com.enterprise.reporting.PdfExportService;
import com.enterprise.reporting.ExcelExportService;
import com.enterprise.transaction.Transaction;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
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
    private final PdfExportService pdfExportService;
    private final ExcelExportService excelExportService;
    private final ExportService exportService; // NEW

    public ReportingController(ReportingService reportingService,
                               PdfExportService pdfExportService,
                               ExcelExportService excelExportService,
                               ExportService exportService) {
        this.reportingService = reportingService;
        this.pdfExportService = pdfExportService;
        this.excelExportService = excelExportService;
        this.exportService = exportService;
    }

    @GetMapping
    public String dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Model model) {

        if (startDate == null) startDate = LocalDate.now().minusDays(30);
        if (endDate == null) endDate = LocalDate.now();
        if (startDate.isAfter(endDate)) {
            LocalDate temp = startDate;
            startDate = endDate;
            endDate = temp;
        }

        var volume = reportingService.getDailyVolume(startDate, endDate);
        var avgAmount = reportingService.getDailyAverage(startDate, endDate);
        var summary = reportingService.getSummaryMetrics(startDate, endDate);
        var statusDist = reportingService.getStatusDistribution(startDate, endDate);
        var topMerchants = reportingService.getTopMerchants(startDate, endDate, 5);
        var successRate = reportingService.getDailySuccessRate(startDate, endDate);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        List<String> labels = volume.keySet().stream().sorted().map(d -> d.format(formatter)).collect(Collectors.toList());
        List<Long> volumeData = labels.stream().map(label -> volume.getOrDefault(LocalDate.parse(label, formatter), 0L)).collect(Collectors.toList());
        List<Double> avgData = labels.stream()
                .map(label -> avgAmount.getOrDefault(LocalDate.parse(label, formatter), BigDecimal.ZERO).doubleValue())
                .collect(Collectors.toList());

        List<String> statusLabels = new ArrayList<>(statusDist.keySet());
        List<Long> statusValues = statusLabels.stream().map(statusDist::get).collect(Collectors.toList());

        List<String> merchantLabels = topMerchants.stream()
                .map(m -> "Merchant " + m.get("merchantId"))
                .collect(Collectors.toList());
        List<Double> merchantValues = topMerchants.stream()
                .map(m -> ((BigDecimal) m.get("totalAmount")).doubleValue())
                .collect(Collectors.toList());

        List<Double> successRateValues = labels.stream()
                .map(label -> successRate.getOrDefault(LocalDate.parse(label, formatter), 0.0))
                .collect(Collectors.toList());

        model.addAttribute("labels", labels);
        model.addAttribute("volumeData", volumeData);
        model.addAttribute("avgData", avgData);
        model.addAttribute("summary", summary);
        model.addAttribute("statusLabels", statusLabels);
        model.addAttribute("statusValues", statusValues);
        model.addAttribute("topMerchants", topMerchants);
        model.addAttribute("merchantLabels", merchantLabels);
        model.addAttribute("merchantValues", merchantValues);
        model.addAttribute("successRateData", successRateValues);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);

        return "admin/reports/dashboard";
    }

    // ===== CSV EXPORT =====
    @GetMapping("/data/transactions/csv")
    @ResponseBody
    public ResponseEntity<String> exportCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        if (startDate == null) startDate = LocalDate.now().minusDays(30);
        if (endDate == null) endDate = LocalDate.now();
        if (startDate.isAfter(endDate)) {
            LocalDate temp = startDate;
            startDate = endDate;
            endDate = temp;
        }

        List<Transaction> transactions = reportingService.getTransactionsBetween(startDate, endDate);
        StringBuilder csv = new StringBuilder();
        csv.append("ID,Invoice,Customer,Merchant,Amount,Currency,Status,Created\n");
        for (Transaction tx : transactions) {
            csv.append(tx.getId()).append(",")
               .append(tx.getInvoiceId()).append(",")
               .append(tx.getCustomerId()).append(",")
               .append(tx.getMerchantId()).append(",")
               .append(tx.getAmount()).append(",")
               .append(tx.getCurrency()).append(",")
               .append(tx.getStatus().name()).append(",")
               .append(tx.getCreatedAt()).append("\n");
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=transactions_" + startDate + "_to_" + endDate + ".csv")
                .contentType(MediaType.TEXT_PLAIN)
                .body(csv.toString());
    }

    // ===== PDF EXPORT =====
    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        if (startDate == null) startDate = LocalDate.now().minusDays(30);
        if (endDate == null) endDate = LocalDate.now();
        if (startDate.isAfter(endDate)) {
            LocalDate temp = startDate;
            startDate = endDate;
            endDate = temp;
        }

        ByteArrayOutputStream pdfStream = pdfExportService.generateReport(startDate, endDate);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report_" + startDate + "_to_" + endDate + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfStream.toByteArray());
    }

    // ===== EXCEL EXPORT =====
    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        if (startDate == null) startDate = LocalDate.now().minusDays(30);
        if (endDate == null) endDate = LocalDate.now();
        if (startDate.isAfter(endDate)) {
            LocalDate temp = startDate;
            startDate = endDate;
            endDate = temp;
        }

        ByteArrayOutputStream excelStream = excelExportService.generateReport(startDate, endDate);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report_" + startDate + "_to_" + endDate + ".xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excelStream.toByteArray());
    }

    // ===== ADVANCED EXPORT (NEW) =====
    @PostMapping("/export/advanced")
    public ResponseEntity<byte[]> exportAdvanced(@RequestBody ExportRequest request) {
        LocalDate startDate = LocalDate.parse(request.getStartDate());
        LocalDate endDate = LocalDate.parse(request.getEndDate());

        List<Transaction> transactions = reportingService.getTransactionsBetween(startDate, endDate);

        try {
            byte[] data = exportService.export(transactions, request.getFormat(), request.getFields());
            String contentType = switch (request.getFormat().toLowerCase()) {
                case "json" -> "application/json";
                case "xml" -> "application/xml";
                case "csv" -> "text/csv";
                default -> "application/octet-stream";
            };
            String filename = "transactions." + request.getFormat().toLowerCase();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(data);
        } catch (Exception e) {
            throw new RuntimeException("Export failed", e);
        }
    }
}