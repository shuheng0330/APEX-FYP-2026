package com.tbm.careerpathlearning.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextBox;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The filter on text as the parser really extracts it from each kind of upload: Word tables separate cells with tabs,
 * PDFs wrap lines, spreadsheets flatten rows (FR-06-01, FR-06-08, FR-06-17). A .doc file can't be written from scratch
 * here, so it isn't covered; it reaches the filter as plain text in the same way.
 */
class SensitiveDataFilterDocumentsTest {

    private final DocumentParserService parser = new DocumentParserService();

    private String maskedTextOf(byte[] file, String filename) {
        String text = parser.extractText(new ByteArrayInputStream(file), filename);
        assertFalse(text.isBlank(), filename + " should have text");
        return SensitiveDataFilter.mask(text).text();
    }

    private static void assertMasked(String text) {
        assertAll(text,
                () -> assertFalse(text.contains("Ahmad bin Ali"), "name"),
                () -> assertFalse(text.contains("901301-00-1234"), "labelled IC number"),
                () -> assertFalse(text.contains("900101-14-5678"), "IC number"),
                () -> assertFalse(text.contains("A12345678"), "passport number"),
                () -> assertFalse(text.contains("3456789") || text.contains("345 6789"), "mobile number"),
                () -> assertFalse(text.contains("someone@example.com"), "email address"),
                () -> assertFalse(text.contains("1234567890"), "bank account"),
                () -> assertTrue(text.contains("Count the float"), "the SOP's own words stay"),
                () -> assertTrue(text.contains("finance@tbm.net"), "company address stays"));
    }

    private static final String[] LINES = {"Count the float at the start of every shift.", "Name: Ahmad bin Ali", "IC No: 901301-00-1234",
            "Backup IC 900101-14-5678 and passport A12345678.", "Call 012-345 6789 or write to someone@example.com or finance@tbm.net.",
            "Account No: 1234567890"};

    @Test
    @DisplayName(".txt, with a Windows byte-order mark and line endings")
    void textFile() {
        byte[] file = ("﻿" + String.join("\r\n", LINES)).getBytes(StandardCharsets.UTF_8);
        assertMasked(maskedTextOf(file, "sop.txt"));
    }

    @Test
    @DisplayName(".docx, in paragraphs and in table cells")
    void wordDocument() throws IOException {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText(LINES[0]);
            doc.createParagraph().createRun().setText(LINES[3]);
            doc.createParagraph().createRun().setText(LINES[4]);
            XWPFTable table = doc.createTable(3, 2);
            table.getRow(0).getCell(0).setText("Name:");
            table.getRow(0).getCell(1).setText("Ahmad bin Ali");
            table.getRow(1).getCell(0).setText("IC No");
            table.getRow(1).getCell(1).setText("901301-00-1234");
            table.getRow(2).getCell(0).setText("Account No:");
            table.getRow(2).getCell(1).setText("1234567890");
            doc.write(out);
            String text = maskedTextOf(out.toByteArray(), "sop.docx");
            assertMasked(text);
            assertTrue(text.contains("Name:\t[NAME]"), text);
            assertTrue(text.contains("IC No\t[IC_NUMBER]"), text);
        }
    }

    private static byte[] workbook(Workbook book) throws IOException {
        try (book; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet("Staff");
            int r = 0;
            sheet.createRow(r++).createCell(0).setCellValue(LINES[0]);
            for (String[] cells : new String[][] {{"Name:", "Ahmad bin Ali"}, {"IC No", "901301-00-1234"}, {"Backup IC", "900101-14-5678"},
                    {"Passport", "A12345678"}, {"Mobile", "012-345 6789"}, {"Email", "someone@example.com"}, {"Contact", "finance@tbm.net"},
                    {"Account No:", "1234567890"}}) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(cells[0]);
                row.createCell(1).setCellValue(cells[1]);
            }
            sheet.createRow(r).createCell(0).setCellValue(900101145678d); // an IC number typed as a number
            book.write(out);
            return out.toByteArray();
        }
    }

    @Test
    @DisplayName(".xlsx and .xls, with cells flattened to tab-separated rows")
    void spreadsheets() throws IOException {
        for (String name : new String[] {"sop.xlsx", "sop.xls"}) {
            byte[] file = workbook(name.endsWith("x") ? new XSSFWorkbook() : new HSSFWorkbook());
            String text = maskedTextOf(file, name);
            assertMasked(text);
            assertFalse(text.contains("900101145678"), "an IC number stored as a number: " + text);
        }
    }

    @Test
    @DisplayName(".pptx, in a slide's text box")
    void newPowerPoint() throws IOException {
        try (XMLSlideShow show = new XMLSlideShow(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSLFSlide slide = show.createSlide();
            XSLFTextBox box = slide.createTextBox();
            box.clearText();
            for (String line : LINES) {
                box.addNewTextParagraph().addNewTextRun().setText(line);
            }
            show.write(out);
            assertMasked(maskedTextOf(out.toByteArray(), "sop.pptx"));
        }
    }

    @Test
    @DisplayName(".ppt, in a slide's text box")
    void oldPowerPoint() throws IOException {
        try (HSLFSlideShow show = new HSLFSlideShow(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            HSLFSlide slide = show.createSlide();
            HSLFTextBox box = slide.createTextBox();
            box.setText(String.join("\n", LINES));
            show.write(out);
            assertMasked(maskedTextOf(out.toByteArray(), "sop.ppt"));
        }
    }

    @Test
    @DisplayName(".pdf, with numbers wrapped across lines")
    void pdf() throws IOException {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            pdf.addPage(page);
            try (PDPageContentStream text = new PDPageContentStream(pdf, page)) {
                text.beginText();
                text.setFont(PDType1Font.HELVETICA, 11);
                text.setLeading(14);
                text.newLineAtOffset(50, 700);
                for (String line : new String[] {LINES[0], LINES[1], LINES[2], "Backup IC 900101-14-", "5678 and passport A12345678.",
                        "Call 012-345", "6789 or write to someone@example.com or finance@tbm.net.", LINES[5]}) {
                    text.showText(line);
                    text.newLine();
                }
                text.endText();
            }
            pdf.save(out);
            assertMasked(maskedTextOf(out.toByteArray(), "sop.pdf"));
        }
    }
}
