package com.priestess.core.controller;

import com.priestess.core.entity.TransactionDocument;
import com.priestess.core.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/finance/export")
@RequiredArgsConstructor
public class ExcelController {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TransactionRepository transactionRepository;

    @GetMapping("/transactions")
    public ResponseEntity<byte[]> exportTransactions(
            @RequestHeader("X-User-Id") String userId) {

        log.info("[ExcelController] GET /api/finance/export/transactions — userId={}", userId);

        try (Workbook workbook = new XSSFWorkbook()) {

            Sheet sheet = workbook.createSheet("Riwayat Transaksi");

            CellStyle headerStyle = createHeaderStyle(workbook);
            Row headerRow = sheet.createRow(0);
            String[] columns = {
                    "Invoice ID", "Tipe Transaksi", "Status", "Nominal (Rp)",
                    "Sender User ID", "Sender Wallet ID",
                    "Recipient User ID", "Recipient Wallet ID",
                    "Catatan", "Dibuat Pada"
            };
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 6000);
            }

            List<TransactionDocument> transactions = transactionRepository.findAll();
            int rowNum = 1;

            for (TransactionDocument doc : transactions) {
                Row row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(doc.getInvoiceId() != null ? doc.getInvoiceId() : "-");
                row.createCell(1).setCellValue(doc.getTransactionType() != null ? doc.getTransactionType() : "-");
                row.createCell(2).setCellValue(doc.getStatus() != null ? doc.getStatus() : "-");
                row.createCell(3).setCellValue(
                        doc.getAmount() != null ? doc.getAmount().doubleValue() : 0.0);

                if (doc.getSender() != null) {
                    row.createCell(4).setCellValue(
                            doc.getSender().getUserId() != null ? doc.getSender().getUserId() : "-");
                    row.createCell(5).setCellValue(
                            doc.getSender().getWalletId() != null ? doc.getSender().getWalletId() : "-");
                } else {
                    row.createCell(4).setCellValue("-");
                    row.createCell(5).setCellValue("-");
                }

                if (doc.getRecipient() != null) {
                    row.createCell(6).setCellValue(
                            doc.getRecipient().getUserId() != null ? doc.getRecipient().getUserId() : "-");
                    row.createCell(7).setCellValue(
                            doc.getRecipient().getWalletId() != null ? doc.getRecipient().getWalletId() : "-");
                } else {
                    row.createCell(6).setCellValue("-");
                    row.createCell(7).setCellValue("-");
                }

                row.createCell(8).setCellValue(doc.getNote() != null ? doc.getNote() : "-");
                row.createCell(9).setCellValue(
                        doc.getCreatedAt() != null ? doc.getCreatedAt().format(FORMATTER) : "-");
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            byte[] excelBytes = out.toByteArray();

            String filename = "transaksi_eop_" +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";

            log.info("[ExcelController] Export berhasil — {} baris data, file: {}", transactions.size(), filename);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(excelBytes);

        } catch (IOException e) {
            log.error("[ExcelController] Error saat membuat file Excel: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/transactions/template")
    public ResponseEntity<byte[]> downloadTemplate() {
        log.info("[ExcelController] GET /api/finance/export/transactions/template");

        try (Workbook workbook = new XSSFWorkbook()) {

            Sheet sheet = workbook.createSheet("Template Import Transaksi");
            CellStyle headerStyle = createHeaderStyle(workbook);
            Row headerRow = sheet.createRow(0);

            String[] columns = {
                    "Invoice ID", "Tipe Transaksi", "Status", "Nominal (Rp)",
                    "Sender User ID", "Sender Wallet ID",
                    "Recipient User ID", "Recipient Wallet ID",
                    "Catatan", "Dibuat Pada"
            };
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 6000);
            }

            Row exampleRow = sheet.createRow(1);
            exampleRow.createCell(0).setCellValue("TRF-xxxxxxxx-xxxx");
            exampleRow.createCell(1).setCellValue("TRANSFER");
            exampleRow.createCell(2).setCellValue("SUCCESS");
            exampleRow.createCell(3).setCellValue(50000.00);
            exampleRow.createCell(4).setCellValue("uuid-sender-user-id");
            exampleRow.createCell(5).setCellValue("uuid-sender-wallet-id");
            exampleRow.createCell(6).setCellValue("uuid-recipient-user-id");
            exampleRow.createCell(7).setCellValue("uuid-recipient-wallet-id");
            exampleRow.createCell(8).setCellValue("Transfer kepada teman");
            exampleRow.createCell(9).setCellValue("2025-01-01 12:00:00");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            byte[] excelBytes = out.toByteArray();

            log.info("[ExcelController] Template berhasil digenerate.");

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"template_transaksi_eop.xlsx\"")
                    .body(excelBytes);

        } catch (IOException e) {
            log.error("[ExcelController] Error saat membuat template: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        Font font = workbook.createFont();
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setBold(true);
        style.setFont(font);

        return style;
    }
}
