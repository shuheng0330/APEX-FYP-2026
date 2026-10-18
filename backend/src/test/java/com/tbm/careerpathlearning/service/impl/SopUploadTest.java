package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.SopDocumentDto;
import com.tbm.careerpathlearning.enums.SopCourseStatus;
import com.tbm.careerpathlearning.enums.SopGenerationStatus;
import com.tbm.careerpathlearning.enums.TrainingAuditTarget;
import com.tbm.careerpathlearning.exception.ForbiddenRequestException;
import com.tbm.careerpathlearning.model.SopDocument;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.service.DocumentParserService;
import com.tbm.careerpathlearning.service.FileService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** What an upload accepts: file types, size, readable PDFs, and revised SOPs as new versions (FR-06-01, FR-06-06, FR-06-08, FR-06-12). */
class SopUploadTest {

    private static final String TEXT = "Count the float in the cash drawer at the start of every shift. "
            + "Record the count in the cash log before serving any guest. Never leave the drawer open when you step away. "
            + "Check every note above RM 50 with the UV lamp. Report any shortage to the supervisor immediately.";

    /** Keeps uploads in memory and remembers what was stored. */
    static class StubFiles implements FileService {
        final List<String> stored = new ArrayList<>();

        @Override
        public String uploadFile(MultipartFile file, String folderName) {
            String path = folderName + "/" + file.getOriginalFilename();
            stored.add(path);
            return path;
        }

        @Override
        public void deleteFile(String filePath) {
            stored.remove(filePath);
        }

        @Override
        public Path getFilePath(String relativePath) {
            return Path.of(relativePath);
        }
    }

    private SopTestStore store;
    private SopServiceImpl courses;
    private StubFiles files;
    private Staff trainer;

    @BeforeEach
    void setUp() {
        store = new SopTestStore();
        courses = store.wire(new SopServiceImpl());
        files = new StubFiles();
        ReflectionTestUtils.setField(courses, "fileService", files);
        ReflectionTestUtils.setField(courses, "documentParserService", new DocumentParserService());
        trainer = store.person("Tan Wei Ming", null);
        TransactionSynchronizationManager.initSynchronization(); // as inside the upload's transaction
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    @DisplayName("Plain-text files are accepted with the other formats, and anything else is refused, naming them all (FR-06-01, FR-06-08)")
    void fileTypes() {
        SopDocumentDto txt = upload(file("cash.txt", TEXT.getBytes(StandardCharsets.UTF_8)), null);

        assertEquals(SopGenerationStatus.PENDING, txt.getGenerationStatus());
        assertEquals(List.of("sop/cash.txt"), files.stored);
        assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size(), "generation is queued once the upload commits");
        assertTrue(DocumentParserService.isSupported("Slides.PPTX") && DocumentParserService.isSupported("rates.xls"));
        assertRefused(".doc, .docx, .pdf, .ppt, .pptx, .xls, .xlsx, .txt", () -> upload(file("cash.csv", new byte[]{1}), null));
        assertEquals(1, files.stored.size(), "a refused file isn't stored");
    }

    @Test
    @DisplayName("A file over 20 MB is refused with its size, before it's stored (FR-06-12)")
    void sizeLimit() {
        MockMultipartFile big = new MockMultipartFile("file", "manual.docx", "application/octet-stream", new byte[]{1}) {
            @Override
            public long getSize() {
                return 21L * 1024 * 1024;
            }
        };

        assertRefused("This file is 21.0 MB. SOP files can be up to 20 MB.", () -> upload(big, null));
        assertTrue(files.stored.isEmpty());
    }

    @Test
    @DisplayName("A PDF without readable text, such as a scan, is refused before it's stored; one with text keeps it for generation (FR-06-12, decision G)")
    void scannedPdf() throws IOException {
        assertRefused("no readable text, so it looks like a scanned image", () -> upload(file("scan.pdf", pdf(null)), null));
        assertRefused("no readable text", () -> upload(file("short.pdf", pdf("Page 1 of 1")), null));
        assertTrue(files.stored.isEmpty());

        SopDocumentDto ok = upload(file("cash.pdf", pdf(TEXT)), null);

        assertTrue(store.docs.get(ok.getId()).getExtractedText().contains("Record the count in the cash log"));
        assertEquals(List.of("sop/cash.pdf"), files.stored);
    }

    @Test
    @DisplayName("A damaged PDF is refused with a message, not an error")
    void damagedPdf() {
        assertRefused("This PDF can't be opened", () -> upload(file("broken.pdf", "not a PDF".getBytes(StandardCharsets.UTF_8)), null));
        assertTrue(files.stored.isEmpty());
    }

    @Test
    @DisplayName("A revised SOP uploaded as a new version becomes the course's next draft, keeping its title and settings (FR-06-06)")
    void newVersion() {
        SopDocument live = store.doc(1L, "Cash Handling", trainer.getId());
        live.setPassMark(80);
        live.setDepartmentTag("Front Office");
        live.setVersion("1.0");

        SopDocumentDto draft = courses.upload(file("cash_v2.txt", TEXT.getBytes(StandardCharsets.UTF_8)), " ", "2.0", "",
                trainer.getId(), 1L);

        assertEquals(SopCourseStatus.DRAFT, draft.getCourseStatus());
        assertEquals(2, draft.getVersionNo());
        assertEquals(1L, draft.getCourseId());
        assertEquals("Cash Handling", draft.getTitle(), "a blank title keeps the course's");
        assertEquals("2.0", draft.getVersion());
        assertEquals(80, draft.getPassMark());
        assertEquals("Front Office", store.docs.get(draft.getId()).getDepartmentTag(), "a blank department keeps the course's");
        assertEquals(SopGenerationStatus.PENDING, draft.getGenerationStatus());
        assertTrue(store.docs.get(1L).isLive(), "staff keep the live version");
        assertEquals("Draft v2 started from a revised SOP, cash_v2.txt.",
                store.audit().history(TrainingAuditTarget.COURSE, "1").get(0).getDetail());

        assertThrows(ForbiddenRequestException.class, () -> courses.upload(file("x.txt", TEXT.getBytes(StandardCharsets.UTF_8)),
                null, null, null, UUID.randomUUID(), 1L), "only the course's owner can");
        assertRefused("Draft v2 of this course is in progress", () -> courses.upload(file("cash_v3.txt",
                TEXT.getBytes(StandardCharsets.UTF_8)), null, null, null, trainer.getId(), 1L));
    }

    private SopDocumentDto upload(MultipartFile file, Long newVersionOf) {
        return courses.upload(file, "Cash Handling", "1.0", "Front Office", trainer.getId(), newVersionOf);
    }

    private static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content);
    }

    /** A one-page PDF with this text, or with no text at all, like a scanned image. */
    private static byte[] pdf(String text) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            if (text != null) {
                try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                    content.beginText();
                    content.setFont(PDType1Font.HELVETICA, 9);
                    content.newLineAtOffset(40, 750);
                    for (String sentence : text.split("(?<=\\.) ")) {
                        content.showText(sentence);
                        content.newLineAtOffset(0, -14);
                    }
                    content.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static void assertRefused(String expected, Executable action) {
        RuntimeException e = assertThrows(RuntimeException.class, action);
        assertTrue(e.getMessage().contains(expected), e.getMessage());
    }
}
