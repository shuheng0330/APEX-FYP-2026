package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.enums.TrainingDelivery;
import com.tbm.careerpathlearning.enums.TrainingSessionKind;
import com.tbm.careerpathlearning.enums.TrainingSessionStatus;
import com.tbm.careerpathlearning.enums.TrainingType;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.model.TrainingProgram;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipInputStream;

/**
 * The Excel files of the training sessions: the HRDC attendance template (FR-14-05, FR-14-06) and the session report
 * (FR-12-07). Every value is written as text, so Excel keeps IC numbers' leading zeros and never runs a name as a
 * formula; uploads are read as Excel workbooks or CSV.
 */
final class TrainingSpreadsheets {

    static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    /** The largest attendance file accepted; a filled-in template is a few kilobytes. */
    static final long MAX_UPLOAD_BYTES = 2L * 1024 * 1024;
    /** Rows read from an upload, title and header rows included. */
    static final int MAX_ROWS = 1020;
    /** What an uploaded workbook may unpack to, so a small file can't expand to fill the memory. */
    private static final long MAX_UNPACKED_BYTES = 20L * 1024 * 1024;
    private static final int MAX_COLUMNS = 30;
    /** The hidden sheet naming the session a template was made for. */
    static final String ABOUT_SHEET = "About";

    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);

    private TrainingSpreadsheets() {
    }

    // --- labels, as the calendar shows them ----------------------------------------

    static String kind(TrainingSessionKind kind) {
        return kind == TrainingSessionKind.STAFF_SHARING ? "Staff Sharing" : "Training";
    }

    static String delivery(TrainingDelivery delivery) {
        return switch (delivery) {
            case PHYSICAL -> "Physical";
            case HYBRID -> "Hybrid";
            case ONLINE -> "Online";
        };
    }

    static String type(TrainingType type) {
        if (type == null) return "None";
        return switch (type) {
            case PRODUCT -> "Product Training";
            case SKILL_BASED -> "Skill-Based Training";
            case OTHER -> "Other";
        };
    }

    static String status(TrainingProgram s, LocalDateTime now) {
        if (s.getStatus() == TrainingSessionStatus.CANCELLED) return "Cancelled";
        if (s.getStatus() == TrainingSessionStatus.ARCHIVED) return "Archived";
        return switch (s.timing(now)) {
            case UPCOMING -> "Upcoming";
            case IN_PROGRESS -> "In progress";
            case ENDED -> "Ended";
        };
    }

    /** "Tue 20 Oct 2026, 09:00 to 12:00" */
    static String when(TrainingProgram s) {
        return s.getSessionDate().format(DAY) + ", " + s.getStartTime().format(TIME) + " to " + s.getEndTime().format(TIME);
    }

    /** A file name from the session's title and date, without characters Windows refuses. */
    static String fileName(String prefix, TrainingProgram s) {
        String title = s.getTitle() == null ? "Session" : s.getTitle().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "-").trim();
        if (title.length() > 80) title = title.substring(0, 80).trim();
        return prefix + " - " + title + " - " + s.getSessionDate() + ".xlsx";
    }

    // --- writing --------------------------------------------------------------------

    /** A workbook with the styles both files use. */
    static final class Book implements AutoCloseable {
        final XSSFWorkbook workbook = new XSSFWorkbook();
        final CellStyle text;
        /** For numbers, such as quiz scores, so a score typed in stays a number. */
        final CellStyle plain;
        final CellStyle header;
        final CellStyle title;
        final CellStyle label;
        final CellStyle note;

        Book() {
            short textFormat = workbook.createDataFormat().getFormat("@");
            text = workbook.createCellStyle();
            text.setDataFormat(textFormat);
            plain = workbook.createCellStyle();

            Font bold = workbook.createFont();
            bold.setBold(true);
            header = workbook.createCellStyle();
            header.setDataFormat(textFormat);
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setBorderBottom(BorderStyle.THIN);
            header.setWrapText(true);

            Font big = workbook.createFont();
            big.setBold(true);
            big.setFontHeightInPoints((short) 14);
            title = workbook.createCellStyle();
            title.setFont(big);

            label = workbook.createCellStyle();
            label.setDataFormat(textFormat);
            label.setFont(bold);

            Font grey = workbook.createFont();
            grey.setItalic(true);
            grey.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            note = workbook.createCellStyle();
            note.setFont(grey);
        }

        Sheet sheet(String name, int... widths) {
            Sheet sheet = workbook.createSheet(name);
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i] * 256);
            }
            return sheet;
        }

        /** Writes the values from the first column: numbers as numbers, the rest as text; null leaves a cell empty. */
        Row row(Sheet sheet, int index, CellStyle style, Object... values) {
            Row row = sheet.createRow(index);
            for (int i = 0; i < values.length; i++) {
                Cell cell = row.createCell(i);
                if (values[i] instanceof Number n) {
                    cell.setCellStyle(style == null ? plain : style);
                    cell.setCellValue(n.doubleValue());
                } else {
                    cell.setCellStyle(style == null ? text : style);
                    if (values[i] != null) {
                        cell.setCellValue(String.valueOf(values[i]));
                    }
                }
            }
            return row;
        }

        byte[] bytes() {
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                workbook.write(out);
                return out.toByteArray();
            } catch (IOException e) {
                throw new IllegalStateException("Could not write the Excel file", e);
            }
        }

        @Override
        public void close() {
            try {
                workbook.close();
            } catch (IOException ignored) {
                // nothing was opened from disk
            }
        }
    }

    // --- reading an upload ------------------------------------------------------------

    /** An uploaded attendance file: its rows as text, and the session a template was made for, if it says. */
    record Upload(List<List<String>> rows, String sessionKey) {
    }

    /** Reads an Excel workbook (.xlsx) or a CSV file, whichever the bytes are. */
    static Upload read(byte[] bytes) {
        if (bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K' && bytes[2] == 3 && bytes[3] == 4) {
            return workbook(bytes);
        }
        if (bytes.length >= 4 && (bytes[0] & 0xFF) == 0xD0 && (bytes[1] & 0xFF) == 0xCF
                && (bytes[2] & 0xFF) == 0x11 && (bytes[3] & 0xFF) == 0xE0) {
            throw new BadRequestException("This is an old Excel file (.xls). Save it as an Excel workbook (.xlsx) or CSV, "
                    + "then upload it again.");
        }
        return new Upload(csv(text(bytes)), null);
    }

    private static Upload workbook(byte[] bytes) {
        checkUnpackedSize(bytes);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = null;
            for (int i = 0; i < workbook.getNumberOfSheets() && sheet == null; i++) {
                if (!ABOUT_SHEET.equals(workbook.getSheetName(i))) {
                    sheet = workbook.getSheetAt(i);
                }
            }
            if (sheet == null) {
                throw new BadRequestException("This file has no attendance sheet. Download the template and fill that in.");
            }
            DataFormatter formatter = new DataFormatter(Locale.ENGLISH);
            if (sheet.getLastRowNum() >= MAX_ROWS) {
                throw tooManyRows();
            }
            List<List<String>> rows = new ArrayList<>();
            for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                List<String> cells = new ArrayList<>();
                if (row != null) {
                    int last = Math.min(row.getLastCellNum(), MAX_COLUMNS);
                    for (int c = 0; c < last; c++) {
                        cells.add(value(row.getCell(c), formatter));
                    }
                }
                rows.add(cells);
            }
            Sheet about = workbook.getSheet(ABOUT_SHEET);
            String key = null;
            if (about != null && about.getRow(0) != null) {
                key = value(about.getRow(0).getCell(1), formatter).trim();
            }
            return new Upload(rows, key == null || key.isEmpty() ? null : key);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("This Excel file can't be read. Save it again as an Excel workbook (.xlsx), "
                    + "or download a fresh template.");
        }
    }

    /** The cell as Excel shows it; a formula gives its last calculated value. */
    private static String value(Cell cell, DataFormatter formatter) {
        if (cell == null) return "";
        if (cell.getCellType() != CellType.FORMULA) {
            return formatter.formatCellValue(cell);
        }
        return switch (cell.getCachedFormulaResultType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> formatter.formatRawCellContents(cell.getNumericCellValue(),
                    cell.getCellStyle().getDataFormat(), cell.getCellStyle().getDataFormatString());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue()).toUpperCase(Locale.ROOT);
            default -> "";
        };
    }

    /** Refuses a workbook that unpacks to far more than an attendance list could. */
    private static void checkUnpackedSize(byte[] bytes) {
        long total = 0;
        byte[] buffer = new byte[8192];
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            while (zip.getNextEntry() != null) {
                int n;
                while ((n = zip.read(buffer)) > 0) {
                    total += n;
                    if (total > MAX_UNPACKED_BYTES) {
                        throw new BadRequestException("This Excel file is too large to be an attendance list.");
                    }
                }
            }
        } catch (IOException e) {
            throw new BadRequestException("This Excel file can't be read. Save it again as an Excel workbook (.xlsx), "
                    + "or download a fresh template.");
        }
    }

    /** UTF-8, with or without the mark Excel adds; otherwise the Windows encoding Excel uses for "CSV". */
    private static String text(byte[] bytes) {
        int start = bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF
                ? 3 : 0;
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, start, bytes.length - start)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, start, bytes.length - start, Charset.forName("windows-1252"));
        }
    }

    /** CSV as Excel writes it: quoted cells may hold commas, quotes ("") and line breaks; ";" where Excel uses it. */
    static List<List<String>> csv(String text) {
        char separator = separator(text);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cell.append(c);
                }
            } else if (c == '"' && cell.isEmpty()) {
                quoted = true;
            } else if (c == separator) {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
                if (rows.size() > MAX_ROWS) {
                    throw tooManyRows();
                }
            } else {
                cell.append(c);
            }
        }
        if (!cell.isEmpty() || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }

    private static char separator(String text) {
        int commas = 0;
        int semicolons = 0;
        int lines = 0;
        for (int i = 0; i < text.length() && lines < 10; i++) {
            char c = text.charAt(i);
            if (c == ',') commas++;
            else if (c == ';') semicolons++;
            else if (c == '\n') lines++;
        }
        return semicolons > commas ? ';' : ',';
    }

    private static BadRequestException tooManyRows() {
        return new BadRequestException("This file has more than 1,000 rows. Upload one session's attendance at a time.");
    }
}
