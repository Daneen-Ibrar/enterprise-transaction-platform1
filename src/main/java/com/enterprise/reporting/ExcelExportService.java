package com.enterprise.reporting;

import com.enterprise.transaction.Transaction;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class ExcelExportService {

    private final ReportingService reportingService;

    public ExcelExportService(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    public ByteArrayOutputStream generateReport(LocalDate startDate, LocalDate endDate, Long tenantId) {  // 👈 add param
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Workbook workbook = new XSSFWorkbook()) {
            // Sheet 1: Summary
            Sheet summarySheet = workbook.createSheet("Summary");
            int rowNum = 0;
            Row row = summarySheet.createRow(rowNum++);
            row.createCell(0).setCellValue("Metric");
            row.createCell(1).setCellValue("Value");

            Map<String, Object> summary = reportingService.getSummaryMetrics(startDate, endDate, tenantId);  // 👈 pass tenantId
            Object[][] data = {
                    {"Date Range", startDate + " to " + endDate},
                    {"Total Transactions", summary.get("totalTransactions")},
                    {"Total Volume (£)", summary.get("totalVolume")},
                    {"Average Amount (£)", summary.get("average")},
                    {"Success Rate (%)", summary.get("successRate")},
                    {"Settled", summary.get("settledCount")},
                    {"Pending", summary.get("pendingCount")},
                    {"Refunded", summary.get("refundedCount")},
                    {"Failed", summary.get("failedCount")}
            };

            for (Object[] pair : data) {
                Row r = summarySheet.createRow(rowNum++);
                r.createCell(0).setCellValue(pair[0].toString());
                r.createCell(1).setCellValue(pair[1].toString());
            }

            // Sheet 2: Transactions
            Sheet txSheet = workbook.createSheet("Transactions");
            Row header = txSheet.createRow(0);
            String[] columns = {"ID", "Invoice", "Customer", "Merchant", "Amount", "Currency", "Status", "Created"};
            for (int i = 0; i < columns.length; i++) {
                header.createCell(i).setCellValue(columns[i]);
            }

            List<Transaction> transactions = reportingService.getTransactionsBetween(startDate, endDate, tenantId);  // 👈 pass tenantId
            int txRow = 1;
            for (Transaction tx : transactions) {
                Row r = txSheet.createRow(txRow++);
                r.createCell(0).setCellValue(tx.getId());
                r.createCell(1).setCellValue(tx.getInvoiceId());
                r.createCell(2).setCellValue(tx.getCustomerId());
                r.createCell(3).setCellValue(tx.getMerchantId());
                r.createCell(4).setCellValue(tx.getAmount().doubleValue());
                r.createCell(5).setCellValue(tx.getCurrency());
                r.createCell(6).setCellValue(tx.getStatus().name());
                r.createCell(7).setCellValue(tx.getCreatedAt().toString());
            }

            for (int i = 0; i < columns.length; i++) {
                txSheet.autoSizeColumn(i);
            }

            workbook.write(out);
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate Excel", e);
        }
        return out;
    }
}