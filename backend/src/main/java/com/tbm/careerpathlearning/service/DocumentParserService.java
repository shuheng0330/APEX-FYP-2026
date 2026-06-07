package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.exception.BadRequestException;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Extracts plain text from uploaded SOP documents (FR-08-04 parsing step).
 * Supports modern .docx (XWPF) and legacy .doc (HWPF).
 */
@Service
public class DocumentParserService {

    public String extractText(Path filePath, String originalFilename) {
        String name = originalFilename == null ? "" : originalFilename.toLowerCase();
        try (InputStream in = Files.newInputStream(filePath)) {
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
            }
            throw new BadRequestException("Unsupported file type. Please upload a .doc or .docx document.");
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse document: " + e.getMessage(), e);
        }
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
