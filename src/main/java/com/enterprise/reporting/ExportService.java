package com.enterprise.reporting;

import com.enterprise.transaction.Transaction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ExportService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final XmlMapper xmlMapper = new XmlMapper();

    public byte[] export(List<Transaction> transactions, String format, List<String> fields) throws Exception {
        // Map transactions to a list of maps containing only selected fields
        List<Map<String, Object>> data = transactions.stream()
                .map(tx -> extractFields(tx, fields))
                .collect(Collectors.toList());

        return switch (format.toLowerCase()) {
            case "json" -> objectMapper.writeValueAsBytes(data);
            case "xml" -> xmlMapper.writeValueAsBytes(data);
            case "csv" -> exportCsv(data);
            default -> throw new IllegalArgumentException("Unsupported format: " + format);
        };
    }

    private Map<String, Object> extractFields(Transaction tx, List<String> fields) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        for (String field : fields) {
            switch (field) {
                case "id" -> result.put("id", tx.getId());
                case "invoiceId" -> result.put("invoiceId", tx.getInvoiceId());
                case "customerId" -> result.put("customerId", tx.getCustomerId());
                case "merchantId" -> result.put("merchantId", tx.getMerchantId());
                case "amount" -> result.put("amount", tx.getAmount());
                case "currency" -> result.put("currency", tx.getCurrency());
                case "status" -> result.put("status", tx.getStatus());
                case "createdAt" -> result.put("createdAt", tx.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                case "updatedAt" -> result.put("updatedAt", tx.getUpdatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                default -> result.put(field, "N/A");
            }
        }
        return result;
    }

    private byte[] exportCsv(List<Map<String, Object>> data) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(baos)) {
            if (data.isEmpty()) return baos.toByteArray();
            // Write header
            String header = String.join(",", data.get(0).keySet());
            writer.println(header);
            // Write rows
            for (Map<String, Object> row : data) {
                String line = row.values().stream()
                        .map(v -> v == null ? "" : v.toString())
                        .collect(Collectors.joining(","));
                writer.println(line);
            }
        }
        return baos.toByteArray();
    }
}