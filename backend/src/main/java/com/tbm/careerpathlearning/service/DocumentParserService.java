package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.exception.BadRequestException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextParagraph;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * Extracts plain text from uploaded SOP documents (FR-08-04 parsing step).
 *
 * Supported: .docx/.doc (Word), .pdf, .pptx/.ppt (PowerPoint) and .xlsx/.xls (Excel), FR-06-01, and .txt
 * (plain text, FR-06-08). Slides and sheets are flattened to headed text
 * blocks so the generation prompts see the same shape as a Word document.
 */
@Service
public class DocumentParserService {

    /** Accepted extensions, surfaced in the error message and mirrored by the upload UI (FR-06-01, FR-06-08). */
    public static final String SUPPORTED = ".doc, .docx, .pdf, .ppt, .pptx, .xls, .xlsx, .txt";
    private static final List<String> EXTENSIONS = List.of(".doc", ".docx", ".pdf", ".ppt", ".pptx", ".xls", ".xlsx", ".txt");

    /** Whether the parser reads this type of file, judged by its name. */
    public static boolean isSupported(String filename) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        return EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    /** Letters and digits in the text: how much of it a person could read (FR-06-12). */
    public static int readableCharacters(String text) {
        return text == null ? 0 : (int) text.codePoints().filter(Character::isLetterOrDigit).count();
    }

    public String extractText(Path filePath, String originalFilename) {
        try (InputStream in = Files.newInputStream(filePath)) {
            return extractText(in, originalFilename);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse document: " + e.getMessage(), e);
        }
    }

    /** The document's text, read from a stream, such as an upload that isn't stored yet. */
    public String extractText(InputStream in, String originalFilename) {
        String name = originalFilename == null ? "" : originalFilename.toLowerCase();
        try {
            if (name.endsWith(".docx")) {
                try (XWPFDocument doc = new XWPFDocument(in);
                     XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
                    return clean(extractor.getText());
                }
            } else if (name.endsWith(".doc")) {
                try (HWPFDocument doc = new HWPFDocument(in);
                     WordExtractor extractor = new WordExtractor(doc)) {
                    return clean(extractor.getText());
                }
            } else if (name.endsWith(".pdf")) {
                try (PDDocument pdf = PDDocument.load(in)) {
                    return clean(new PDFTextStripper().getText(pdf));
                }
            } else if (name.endsWith(".pptx")) {
                try (XMLSlideShow ppt = new XMLSlideShow(in)) {
                    return clean(extractPptx(ppt));
                }
            } else if (name.endsWith(".ppt")) {
                try (HSLFSlideShow ppt = new HSLFSlideShow(in)) {
                    return clean(extractPpt(ppt));
                }
            } else if (name.endsWith(".xlsx")) {
                try (Workbook wb = new XSSFWorkbook(in)) {
                    return clean(extractWorkbook(wb));
                }
            } else if (name.endsWith(".xls")) {
                try (Workbook wb = new HSSFWorkbook(in)) {
                    return clean(extractWorkbook(wb));
                }
            } else if (name.endsWith(".txt")) {
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                return clean(text.startsWith("\uFEFF") ? text.substring(1) : text); // a Windows editor's byte-order mark
            }
            throw new BadRequestException("Unsupported file type. Please upload one of: " + SUPPORTED);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse document: " + e.getMessage(), e);
        }
    }

    /** One "Slide N" heading per slide, then each text shape's content. */
    private String extractPptx(XMLSlideShow ppt) {
        StringBuilder out = new StringBuilder();
        int index = 1;
        for (XSLFSlide slide : ppt.getSlides()) {
            out.append("Slide ").append(index++);
            String title = slide.getTitle();
            if (title != null && !title.isBlank()) {
                out.append(": ").append(title.trim());
            }
            out.append("\n");
            for (XSLFShape shape : slide.getShapes()) {
                if (shape instanceof XSLFTextShape text) {
                    String body = text.getText();
                    // The title is already in the heading - don't repeat it.
                    if (body != null && !body.isBlank() && !body.trim().equals(title)) {
                        out.append(body.trim()).append("\n");
                    }
                }
            }
            out.append("\n");
        }
        return out.toString();
    }

    private String extractPpt(HSLFSlideShow ppt) {
        StringBuilder out = new StringBuilder();
        int index = 1;
        for (HSLFSlide slide : ppt.getSlides()) {
            out.append("Slide ").append(index++).append("\n");
            for (java.util.List<HSLFTextParagraph> paragraphs : slide.getTextParagraphs()) {
                String body = HSLFTextParagraph.getText(paragraphs);
                if (body != null && !body.isBlank()) {
                    out.append(body.trim()).append("\n");
                }
            }
            out.append("\n");
        }
        return out.toString();
    }

    /**
     * Flattens each sheet to tab-separated rows under a sheet heading. Formatted
     * values are used so dates and percentages read as the author wrote them.
     */
    private String extractWorkbook(Workbook wb) {
        DataFormatter formatter = new DataFormatter();
        StringBuilder out = new StringBuilder();
        for (int s = 0; s < wb.getNumberOfSheets(); s++) {
            Sheet sheet = wb.getSheetAt(s);
            out.append("Sheet: ").append(sheet.getSheetName()).append("\n");
            for (Row row : sheet) {
                StringJoiner cells = new StringJoiner("\t");
                boolean any = false;
                for (Cell cell : row) {
                    String value = formatter.formatCellValue(cell).trim();
                    cells.add(value);
                    if (!value.isEmpty()) {
                        any = true;
                    }
                }
                if (any) {
                    out.append(cells).append("\n");
                }
            }
            out.append("\n");
        }
        return out.toString();
    }

    private String clean(String text) {
        if (text == null) {
            return "";
        }
        // Collapse excessive blank lines while preserving paragraph breaks.
        return text.replaceAll("\\r\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
