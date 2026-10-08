package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.service.impl.FileServiceImpl;
import org.apache.pdfbox.pdmodel.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class KpiAssessmentEvidenceStorageTest {
    @TempDir Path directory;
    byte[] pdf() throws IOException {try(var document=new PDDocument();var out=new ByteArrayOutputStream()) {
        document.addPage(new PDPage());document.save(out);return out.toByteArray();}}
    byte[] image(String format) throws IOException {try(var out=new ByteArrayOutputStream()) {
        ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),format,out);return out.toByteArray();}}
    @Test void verifiedPdfPngAndJpegAreStoredPrivatelyWithGeneratedNames() throws Exception {
        var storage=new KpiAssessmentEvidenceStorage(directory.toString(),10485760);
        for(var format:new String[]{"pdf","png","jpeg"}) {
            var bytes=format.equals("pdf")?pdf():image(format);
            var stored=storage.store(new MockMultipartFile("file","../../unsafe\r\nname."+format,"application/octet-stream",bytes));
            assertEquals(format.equals("pdf")?"application/pdf":"image/"+format,stored.contentType());
            assertFalse(stored.filename().contains("/"));assertFalse(stored.filename().contains("\r"));
            assertEquals(bytes.length,stored.size());assertTrue(storage.resource(stored.key()).exists());
            assertArrayEquals(bytes,storage.resource(stored.key()).getContentAsByteArray());
            storage.delete(stored.key());assertThrows(BadRequestException.class,()->storage.resource(stored.key()));
        }
    }
    @Test void invalidEmptyOversizedAndDisguisedFilesAreRejected() throws Exception {
        var storage=new KpiAssessmentEvidenceStorage(directory.toString(),100);
        assertThrows(BadRequestException.class,()->storage.store(new MockMultipartFile("file",new byte[0])));
        assertThrows(BadRequestException.class,()->storage.store(new MockMultipartFile("file",new byte[101])));
        assertThrows(BadRequestException.class,()->storage.store(new MockMultipartFile("file","fake.pdf","application/pdf","not pdf".getBytes())));
        assertThrows(BadRequestException.class,()->storage.store(new MockMultipartFile("file","fake.png","image/png","<svg/>".getBytes())));
        assertThrows(BadRequestException.class,()->storage.store(new MockMultipartFile("file","bad.pdf","application/pdf","%PDF-invalid".getBytes())));
        assertFalse(Files.exists(directory.resolve(KpiAssessmentEvidenceStorage.PRIVATE_FOLDER)));
    }
    @Test void legacyDownloadsCannotBypassPrivateStorageIncludingNormalizedTraversal() {
        var generic=new FileServiceImpl();ReflectionTestUtils.setField(generic,"FILE_UPLOAD_DIR",directory.toString());
        assertThrows(AccessDeniedException.class,()->generic.getFilePath(KpiAssessmentEvidenceStorage.PRIVATE_FOLDER+"/key"));
        assertThrows(AccessDeniedException.class,()->generic.getFilePath("other/../"+KpiAssessmentEvidenceStorage.PRIVATE_FOLDER+"/key"));
        assertThrows(AccessDeniedException.class,()->generic.getFilePath("../outside"));
        assertEquals(directory.resolve("normal/file.pdf"),generic.getFilePath("normal/file.pdf"));
    }
    @Test void storageReferencesCannotBePaths() {
        var storage=new KpiAssessmentEvidenceStorage(directory.toString(),100);
        assertThrows(BadRequestException.class,()->storage.resource("../secret"));
        assertThrows(BadRequestException.class,()->storage.delete("../secret"));
    }
}
