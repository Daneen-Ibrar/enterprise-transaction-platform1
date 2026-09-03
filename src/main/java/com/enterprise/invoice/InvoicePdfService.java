package com.enterprise.invoice;

import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Element;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
public class InvoicePdfService {

    private static final Logger log = LoggerFactory.getLogger(InvoicePdfService.class);
    private final UserRepository userRepository;

    public InvoicePdfService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public byte[] generateInvoicePdf(Invoice invoice) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);
            document.open();

            // Header
            Font headerFont = new Font(Font.HELVETICA, 18, Font.BOLD);
            Font normalFont = new Font(Font.HELVETICA, 11);
            Font boldFont = new Font(Font.HELVETICA, 11, Font.BOLD);
            Font italicFont = new Font(Font.HELVETICA, 10, Font.ITALIC);

            Paragraph title = new Paragraph("INVOICE", headerFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            document.add(new Paragraph(" "));

            // Invoice details table (2 columns)
            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setSpacingAfter(10);

            table.addCell(createCell("Invoice #" + invoice.getId(), boldFont));
            table.addCell(createCell("Status: " + invoice.getStatus(), boldFont));

            table.addCell(createCell("Date: " + invoice.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE), normalFont));
            table.addCell(createCell("Currency: " + invoice.getCurrency(), normalFont));

            table.addCell(createCell("Description: " + invoice.getDescription(), normalFont));
            table.addCell(createCell("Risk Level: " + invoice.getRiskLevel(), normalFont));

            document.add(table);

            // ============================================================
            // ✅ TAX BREAKDOWN TABLE
            // ============================================================
            if (invoice.getTaxAmount() != null && invoice.getTaxAmount().compareTo(BigDecimal.ZERO) > 0) {
                PdfPTable taxTable = new PdfPTable(2);
                taxTable.setWidthPercentage(100);
                taxTable.setSpacingAfter(10);

                taxTable.addCell(createCell("Subtotal:", boldFont));
                taxTable.addCell(createCell(invoice.getCurrency() + " " + invoice.getAmount().toPlainString(), normalFont));

                taxTable.addCell(createCell(
                    "Tax (" + invoice.getTaxName() + " " + invoice.getTaxRate() + "%):", boldFont
                ));
                taxTable.addCell(createCell(
                    invoice.getCurrency() + " " + invoice.getTaxAmount().toPlainString(), normalFont
                ));

                taxTable.addCell(createCell("Total (with tax):", boldFont));
                taxTable.addCell(createCell(
                    invoice.getCurrency() + " " + invoice.getTotalWithTax().toPlainString(), boldFont
                ));

                if (invoice.getCustomerCountry() != null && !invoice.getCustomerCountry().isEmpty()) {
                    taxTable.addCell(createCell("Customer Country:", normalFont));
                    taxTable.addCell(createCell(invoice.getCustomerCountry(), normalFont));
                }

                if (invoice.isB2B()) {
                    taxTable.addCell(createCell("Business Customer:", normalFont));
                    taxTable.addCell(createCell("✅ B2B", normalFont));
                }

                document.add(taxTable);
            } else {
                // No tax - just show amount
                PdfPTable amountTable = new PdfPTable(2);
                amountTable.setWidthPercentage(100);
                amountTable.setSpacingAfter(10);

                amountTable.addCell(createCell("Amount:", boldFont));
                amountTable.addCell(createCell(invoice.getCurrency() + " " + invoice.getAmount().toPlainString(), normalFont));

                document.add(amountTable);
            }

            // Merchant & Customer info
            String merchantEmail = getUserEmail(invoice.getMerchantId()).orElse("Unknown Merchant");
            String customerEmail = invoice.getCustomerEmail();
            
            document.add(new Paragraph(" "));
            Paragraph merchantLine = new Paragraph("Merchant: " + merchantEmail, normalFont);
            Paragraph customerLine = new Paragraph("Customer: " + customerEmail, normalFont);
            document.add(merchantLine);
            document.add(customerLine);
            document.add(new Paragraph(" "));

            // Footer
            Paragraph footer = new Paragraph("This invoice is generated by the Enterprise Transaction Platform.", italicFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            document.add(footer);

            document.close();

        } catch (Exception e) {
            log.error("Failed to generate PDF for invoice {}", invoice.getId(), e);
            throw new RuntimeException("PDF generation failed", e);
        }

        return out.toByteArray();
    }

    private PdfPCell createCell(String content, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(content, font));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(4);
        return cell;
    }

    private Optional<String> getUserEmail(Long userId) {
        return userRepository.findById(userId).map(AppUser::getEmail);
    }
}