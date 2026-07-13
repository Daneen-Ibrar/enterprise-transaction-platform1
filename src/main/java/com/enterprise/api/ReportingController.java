package com.enterprise.api;

import com.enterprise.reporting.ReportingService;
import com.enterprise.reporting.PdfExportService;
import com.enterprise.reporting.ExcelExportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;   // <-- ADD THIS IMPORT
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

    public ReportingController(ReportingService reportingService,
                               PdfExportService pdfExportService,
                               ExcelExportService excelExportService) {
        this.reportingService = reportingService;
        this.pdfExportService = pdfExportService;
        this.excelExportService = excelExportService;
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

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        List<String> labels = volume.keySet().stream().sorted().map(d -> d.format(formatter)).collect(Collectors.toList());
        List<Long> volumeData = labels.stream().map(label -> volume.getOrDefault(LocalDate.parse(label, formatter), 0L)).collect(Collectors.toList());

        // avgData: convert BigDecimal to Double
        List<Double> avgData = labels.stream()
                .map(label -> avgAmount.getOrDefault(LocalDate.parse(label, formatter), BigDecimal.ZERO).doubleValue())
                .collect(Collectors.toList());

        List<String> statusLabels = new ArrayList<>(statusDist.keySet());
        List<Long> statusValues = statusLabels.stream().map(statusDist::get).collect(Collectors.toList());

        model.addAttribute("labels", labels);
        model.addAttribute("volumeData", volumeData);
        model.addAttribute("avgData", avgData);
        model.addAttribute("summary", summary);
        model.addAttribute("statusLabels", statusLabels);
        model.addAttribute("statusValues", statusValues);
        model.addAttribute("topMerchants", topMerchants);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);

        return "admin/reports/dashboard";
    }

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
}