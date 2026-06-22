package com.priestess.oracle.controller;

import com.priestess.oracle.entity.ComplaintDocument;
import com.priestess.oracle.repository.ComplaintRepository;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/support/export")
@RequiredArgsConstructor
public class ComplaintExcelController {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ComplaintRepository complaintRepository;

    @GetMapping("/complaints")
    public ResponseEntity<byte[]> exportComplaints(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(required = false) String status) {

        log.info("[ComplaintExcelController] GET /api/support/export/complaints — userId={}, status={}", userId, status);

        try (Workbook workbook = new XSSFWorkbook()) {

            Sheet sheet = workbook.createSheet("Laporan Pengaduan");

            CellStyle headerStyle = createHeaderStyle(workbook);
            Row headerRow = sheet.createRow(0);
            String[] columns = {
                    "Complaint ID", "User ID", "Username", "Email",
                    "Invoice ID", "Pesan Asli", "Status",
                    "Kategori AI", "Prioritas AI", "Sentimen AI",
                    "Skor AI", "Saran Balasan AI", "Dibuat Pada"
            };
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 7000);
            }

            List<ComplaintDocument> complaints;
            if (status != null && !status.isBlank()) {
                complaints = complaintRepository.findByStatusOrderByCreatedAtDesc(status.toUpperCase());
                log.info("[ComplaintExcelController] Filter by status={}, ditemukan {} keluhan",
                        status, complaints.size());
            } else {
                complaints = complaintRepository.findAll();
                log.info("[ComplaintExcelController] Export semua keluhan: {} data", complaints.size());
            }

            int rowNum = 1;
            for (ComplaintDocument doc : complaints) {
                Row row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(doc.getComplaintId() != null ? doc.getComplaintId() : "-");
                row.createCell(1).setCellValue(doc.getUserId() != null ? doc.getUserId() : "-");
                row.createCell(2).setCellValue(doc.getUsername() != null ? doc.getUsername() : "-");
                row.createCell(3).setCellValue(doc.getEmail() != null ? doc.getEmail() : "-");
                row.createCell(4).setCellValue(doc.getInvoiceId() != null ? doc.getInvoiceId() : "-");
                row.createCell(5).setCellValue(doc.getRawMessage() != null ? doc.getRawMessage() : "-");
                row.createCell(6).setCellValue(doc.getStatus() != null ? doc.getStatus() : "-");

                if (doc.getAiAnalysis() != null) {
                    row.createCell(7).setCellValue(
                            doc.getAiAnalysis().getCategory() != null ? doc.getAiAnalysis().getCategory() : "-");
                    row.createCell(8).setCellValue(
                            doc.getAiAnalysis().getPriority() != null ? doc.getAiAnalysis().getPriority() : "-");
                    row.createCell(9).setCellValue(
                            doc.getAiAnalysis().getSentiment() != null ? doc.getAiAnalysis().getSentiment() : "-");
                    row.createCell(10).setCellValue(
                            doc.getAiAnalysis().getScore() != null ? doc.getAiAnalysis().getScore().doubleValue() : 0.0);
                    row.createCell(11).setCellValue(
                            doc.getAiAnalysis().getSuggestedReply() != null ? doc.getAiAnalysis().getSuggestedReply() : "-");
                } else {
                    row.createCell(7).setCellValue("Belum dianalisis");
                    row.createCell(8).setCellValue("-");
                    row.createCell(9).setCellValue("-");
                    row.createCell(10).setCellValue(0.0);
                    row.createCell(11).setCellValue("-");
                }

                row.createCell(12).setCellValue(
                        doc.getCreatedAt() != null ? doc.getCreatedAt().format(FORMATTER) : "-");
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            byte[] excelBytes = out.toByteArray();

            String suffix = (status != null && !status.isBlank()) ? "_" + status.toLowerCase() : "";
            String filename = "laporan_pengaduan" + suffix + "_" +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";

            log.info("[ComplaintExcelController] Export berhasil — {} baris, file: {}", complaints.size(), filename);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(excelBytes);

        } catch (IOException e) {
            log.error("[ComplaintExcelController] Error saat membuat file Excel: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/complaints/template")
    public ResponseEntity<byte[]> downloadTemplate() {
        log.info("[ComplaintExcelController] GET /api/support/export/complaints/template");

        try (Workbook workbook = new XSSFWorkbook()) {

            Sheet sheet = workbook.createSheet("Template Pengaduan");
            CellStyle headerStyle = createHeaderStyle(workbook);
            Row headerRow = sheet.createRow(0);

            String[] columns = {
                    "Complaint ID", "User ID", "Username", "Email",
                    "Invoice ID", "Pesan Asli", "Status",
                    "Kategori AI", "Prioritas AI", "Sentimen AI",
                    "Skor AI", "Saran Balasan AI", "Dibuat Pada"
            };
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 7000);
            }

            Row exampleRow = sheet.createRow(1);
            exampleRow.createCell(0).setCellValue("complaint-uuid");
            exampleRow.createCell(1).setCellValue("user-uuid");
            exampleRow.createCell(2).setCellValue("johndoe");
            exampleRow.createCell(3).setCellValue("johndoe@example.com");
            exampleRow.createCell(4).setCellValue("TRF-xxx");
            exampleRow.createCell(5).setCellValue("Transaksi saya gagal tapi saldo terpotong.");
            exampleRow.createCell(6).setCellValue("OPEN");
            exampleRow.createCell(7).setCellValue("TRANSACTION_DISPUTE");
            exampleRow.createCell(8).setCellValue("HIGH");
            exampleRow.createCell(9).setCellValue("NEGATIVE");
            exampleRow.createCell(10).setCellValue(0.85);
            exampleRow.createCell(11).setCellValue("Tim kami akan menginvestigasi dalam 1x24 jam.");
            exampleRow.createCell(12).setCellValue("2025-01-01 12:00:00");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            byte[] excelBytes = out.toByteArray();

            log.info("[ComplaintExcelController] Template berhasil digenerate.");

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"template_pengaduan_eop.xlsx\"")
                    .body(excelBytes);

        } catch (IOException e) {
            log.error("[ComplaintExcelController] Error saat membuat template: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.VIOLET.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        Font font = workbook.createFont();
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setBold(true);
        style.setFont(font);

        return style;
    }
}
