package com.enterprise.reporting;

import com.enterprise.transaction.Transaction;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class PdfExportService {

    private final ReportingService reportingService;

    public PdfExportService(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    public ByteArrayOutputStream generateReport(LocalDate startDate, LocalDate endDate) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter writer = new PdfWriter(out);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            PdfFont boldFont = PdfFontFactory.createFont("Helvetica-Bold");
            document.add(new Paragraph("Transaction Report")
                    .setFont(boldFont)
                    .setFontSize(18)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(10));

            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            document.add(new Paragraph("From: " + startDate.format(dateFormatter) + "  To: " + endDate.format(dateFormatter))
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(20));

            Map<String, Object> summary = reportingService.getSummaryMetrics(startDate, endDate);
            // Table with 4 columns, each 25% width
            Table summaryTable = new Table(UnitValue.createPercentArray(new float[]{25, 25, 25, 25}));
            summaryTable.setWidth(UnitValue.createPercentValue(100));

            addSummaryCell(summaryTable, "Total Transactions", String.valueOf(summary.get("totalTransactions")));
            addSummaryCell(summaryTable, "Total Volume", "£" + summary.get("totalVolume").toString());
            addSummaryCell(summaryTable, "Average", "£" + summary.get("average").toString());
            addSummaryCell(summaryTable, "Success Rate", String.format("%.2f%%", summary.get("successRate")));

            document.add(summaryTable);
            document.add(new Paragraph("\n"));

            List<Transaction> transactions = reportingService.getTransactionsBetween(startDate, endDate);
            Table txTable = new Table(UnitValue.createPercentArray(new float[]{10, 10, 10, 10, 20, 20}));
            txTable.setWidth(UnitValue.createPercentValue(100));

            String[] headers = {"ID", "Invoice", "Customer", "Merchant", "Amount", "Status"};
            for (String h : headers) {
                txTable.addHeaderCell(new Cell().add(new Paragraph(h).setFont(boldFont)));
            }

            transactions.stream().limit(50).forEach(tx -> {
                txTable.addCell(tx.getId().toString());
                txTable.addCell(tx.getInvoiceId().toString());
                txTable.addCell(tx.getCustomerId().toString());
                txTable.addCell(tx.getMerchantId().toString());
                txTable.addCell("£" + tx.getAmount());
                txTable.addCell(tx.getStatus().name());
            });

            document.add(txTable);

            document.add(new Paragraph("Generated on " + LocalDate.now().format(dateFormatter))
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(10)
                    .setMarginTop(20));

            document.close();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate PDF", e);
        }
        return out;
    }

    private void addSummaryCell(Table table, String label, String value) {
        Cell cell = new Cell();
        cell.add(new Paragraph(label).setFontSize(10));
        cell.add(new Paragraph(value).setFontSize(14).setBold());
        table.addCell(cell);
    }
}