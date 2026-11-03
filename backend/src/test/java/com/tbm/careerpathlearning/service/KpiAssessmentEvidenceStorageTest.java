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
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.zip.CRC32;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KpiAssessmentEvidenceStorageTest {
    @TempDir Path directory;
    KpiAssessmentEvidenceStorage storage() {return new KpiAssessmentEvidenceStorage(directory.toString(),10485760);}
    FileServiceImpl generic() {
        var service=new FileServiceImpl();
        ReflectionTestUtils.setField(service,"FILE_UPLOAD_DIR",directory.toString());
        return service;
    }
    Path privateRoot() {return directory.resolve(KpiAssessmentEvidenceStorage.PRIVATE_FOLDER);}
    String seed(byte[] bytes) throws IOException {
        String key=UUID.randomUUID().toString();
        Files.createDirectories(privateRoot());Files.write(privateRoot().resolve(key),bytes);return key;
    }
    long privateFileCount() throws IOException {
        if(!Files.exists(privateRoot())) return 0;
        try(var files=Files.list(privateRoot())) {return files.count();}
    }
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
        assertThrows(BadRequestException.class,()->storage.copy("../secret","evidence.pdf"));
    }
    @Test void copiesEachAllowedTypeToAnIndependentPrivateFileWithVerifiedMetadata() throws Exception {
        var storage=storage();
        for(var format:new String[]{"pdf","png","jpeg"}) {
            byte[] bytes=format.equals("pdf")?pdf():image(format);
            var original=storage.store(new MockMultipartFile("file","original."+format,"application/octet-stream",bytes));
            var copy=storage.copy(original.key(),"..\\unsafe/path\r\n."+format);
            assertNotEquals(original.key(),copy.key());
            assertEquals(copy.key(),UUID.fromString(copy.key()).toString());
            assertEquals("path."+format,copy.filename());
            assertEquals(original.contentType(),copy.contentType());assertEquals(bytes.length,copy.size());
            assertTrue(Files.isRegularFile(privateRoot().resolve(copy.key())));
            assertArrayEquals(bytes,storage.resource(copy.key()).getContentAsByteArray());
            assertFalse(Files.isSameFile(privateRoot().resolve(original.key()),privateRoot().resolve(copy.key())));
            storage.delete(copy.key());
            assertArrayEquals(bytes,storage.resource(original.key()).getContentAsByteArray());
        }
    }
    @Test void deletingOriginalDoesNotAffectCopy() throws Exception {
        var storage=storage();byte[] bytes=pdf();String key=seed(bytes);
        var copy=storage.copy(key,"report.pdf");storage.delete(key);
        assertArrayEquals(bytes,storage.resource(copy.key()).getContentAsByteArray());
    }
    @Test void copyUsesTheSameFilenameFallbackAndLengthLimitAsUpload() throws Exception {
        var storage=storage();String key=seed(pdf());
        for(String name:new String[]{null,"","..","..\\..","\r\n"})
            assertEquals("evidence",storage.copy(key,name).filename());
        assertEquals(255,storage.copy(key,"a".repeat(300)+".pdf").filename().length());
    }
    @Test void missingOrDirectoryCopySourceFailsClearlyWithoutCreatingFiles() throws Exception {
        var storage=storage();String missing=UUID.randomUUID().toString();
        var failure=assertThrows(BadRequestException.class,()->storage.copy(missing,"missing.pdf"));
        assertEquals("This evidence file is unavailable",failure.getMessage());
        assertFalse(Files.exists(privateRoot()));
        Files.createDirectories(privateRoot().resolve(missing));
        assertThrows(BadRequestException.class,()->storage.copy(missing,"directory.pdf"));
        assertEquals(1,privateFileCount());
    }
    @Test void copyRevalidatesEmptyCorruptDisguisedAndUnsupportedContentsWithoutChangingSource() throws Exception {
        var storage=storage();
        for(byte[] bytes:new byte[][]{new byte[0],"not a pdf".getBytes(),"%PDF-invalid".getBytes(),
                "<svg/>".getBytes(),image("gif")}) {
            String key=seed(bytes);long before=privateFileCount();
            assertThrows(BadRequestException.class,()->storage.copy(key,"looks-valid.pdf"));
            assertEquals(before,privateFileCount());assertArrayEquals(bytes,Files.readAllBytes(privateRoot().resolve(key)));
        }
    }
    @Test void zeroPagePdfIsRejectedForUploadAndCopy() throws Exception {
        byte[] bytes;
        try(var document=new PDDocument();var out=new ByteArrayOutputStream()) {document.save(out);bytes=out.toByteArray();}
        String key=seed(bytes);var storage=storage();
        assertThrows(BadRequestException.class,()->storage.copy(key,"empty.pdf"));
        assertThrows(BadRequestException.class,()->storage.store(new MockMultipartFile("file",bytes)));
        assertEquals(1,privateFileCount());
    }
    @Test void copyHonoursConfiguredLimitIncludingExactBoundary() throws Exception {
        byte[] bytes=pdf();String key=seed(bytes);
        var tooSmall=new KpiAssessmentEvidenceStorage(directory.toString(),bytes.length-1);
        assertThrows(BadRequestException.class,()->tooSmall.copy(key,"report.pdf"));
        assertEquals(1,privateFileCount());
        var exact=new KpiAssessmentEvidenceStorage(directory.toString(),bytes.length);
        assertEquals(bytes.length,exact.copy(key,"report.pdf").size());
        assertArrayEquals(bytes,Files.readAllBytes(privateRoot().resolve(key)));
    }
    @Test void tenMiBLimitRejectsOversizedCopyWithoutCreatingAnotherFile() throws Exception {
        byte[] bytes=new byte[10485761];String key=seed(bytes);
        assertThrows(BadRequestException.class,()->storage().copy(key,"report.pdf"));
        assertEquals(1,privateFileCount());assertEquals(bytes.length,Files.size(privateRoot().resolve(key)));
    }
    @Test void uploadCannotBypassLimitWithUnderreportedSize() throws Exception {
        var file=new MockMultipartFile("file","report.pdf","application/pdf",pdf()) {
            @Override public long getSize() {return 1;}
        };
        var storage=new KpiAssessmentEvidenceStorage(directory.toString(),100);
        assertThrows(BadRequestException.class,()->storage.store(file));
        assertEquals(0,privateFileCount());
    }
    @Test void imageDecompressionLimitAppliesToUploadAndCopy() throws Exception {
        byte[] bytes=image("png");
        ByteBuffer.wrap(bytes).putInt(16,5001).putInt(20,5000);
        var crc=new CRC32();crc.update(bytes,12,17);ByteBuffer.wrap(bytes).putInt(29,(int)crc.getValue());
        String key=seed(bytes);var storage=storage();
        for(var operation:new org.junit.jupiter.api.function.Executable[]{
                ()->storage.store(new MockMultipartFile("file","large.png","image/png",bytes)),
                ()->storage.copy(key,"large.png")}) {
            assertEquals("This image is too large to process safely",
                    assertThrows(BadRequestException.class,operation).getMessage());
        }
        assertEquals(1,privateFileCount());
    }
    @Test void failedCopyWriteRemovesPartialDestinationAndPreservesOriginal() throws Exception {
        byte[] bytes=pdf();String key=seed(bytes);var storage=storage();
        try(var files=mockStatic(Files.class,CALLS_REAL_METHODS)) {
            files.when(()->Files.newOutputStream(any(Path.class),eq(StandardOpenOption.CREATE_NEW),eq(StandardOpenOption.WRITE)))
                    .thenAnswer(invocation->new FilterOutputStream((OutputStream)invocation.callRealMethod()) {
                        @Override public void write(byte[] contents) throws IOException {
                            out.write(contents,0,Math.min(10,contents.length));throw new IOException("Disk full");
                        }
                    });
            var failure=assertThrows(IllegalStateException.class,()->storage.copy(key,"report.pdf"));
            assertEquals("Could not copy assessment evidence",failure.getMessage());
            assertEquals("Disk full",failure.getCause().getMessage());
        }
        assertEquals(1,privateFileCount());assertArrayEquals(bytes,Files.readAllBytes(privateRoot().resolve(key)));
    }
    @Test void failedUploadWriteUsesSameCleanupPath() throws Exception {
        byte[] bytes=pdf();var storage=storage();
        try(var files=mockStatic(Files.class,CALLS_REAL_METHODS)) {
            files.when(()->Files.newOutputStream(any(Path.class),eq(StandardOpenOption.CREATE_NEW),eq(StandardOpenOption.WRITE)))
                    .thenAnswer(invocation->new FilterOutputStream((OutputStream)invocation.callRealMethod()) {
                        @Override public void write(byte[] contents) throws IOException {
                            out.write(contents,0,10);throw new IOException("Disk full");
                        }
                    });
            assertThrows(IllegalStateException.class,()->storage.store(new MockMultipartFile("file",bytes)));
        }
        assertEquals(0,privateFileCount());
    }
    @Test void failingInputCloseDoesNotLeaveAnUnreferencedUpload() throws Exception {
        byte[] bytes=pdf();var storage=storage();
        var file=new MockMultipartFile("file","report.pdf","application/pdf",bytes) {
            @Override public InputStream getInputStream() {
                return new ByteArrayInputStream(bytes) {
                    @Override public void close() throws IOException {throw new IOException("Input close failed");}
                };
            }
        };
        assertThrows(IllegalStateException.class,()->storage.store(file));
        assertEquals(0,privateFileCount());
    }
    @Test void exclusiveCreateFailureDoesNotDeleteAnExistingDestination() throws Exception {
        byte[] bytes=pdf();String key=seed(bytes);var storage=storage();
        try(var files=mockStatic(Files.class,CALLS_REAL_METHODS)) {
            files.when(()->Files.newOutputStream(any(Path.class),eq(StandardOpenOption.CREATE_NEW),eq(StandardOpenOption.WRITE)))
                    .thenAnswer(invocation->{
                        Path target=invocation.getArgument(0);Files.write(target,new byte[]{42});
                        throw new FileAlreadyExistsException(target.toString());
                    });
            assertThrows(IllegalStateException.class,()->storage.copy(key,"report.pdf"));
        }
        assertEquals(2,privateFileCount());assertArrayEquals(bytes,Files.readAllBytes(privateRoot().resolve(key)));
        try(var files=Files.list(privateRoot())) {
            Path collision=files.filter(path->!path.getFileName().toString().equals(key)).findFirst().orElseThrow();
            assertArrayEquals(new byte[]{42},Files.readAllBytes(collision));
        }
    }
    @Test void genericUploadReadAndDeleteRejectPrivateAndOutsidePathsWithoutMutatingFiles() throws Exception {
        var generic=generic();String key=seed(pdf());byte[] original=Files.readAllBytes(privateRoot().resolve(key));
        var upload=new MockMultipartFile("file","normal.pdf","application/pdf",pdf());
        for(String folder:new String[]{KpiAssessmentEvidenceStorage.PRIVATE_FOLDER,
                "other/../"+KpiAssessmentEvidenceStorage.PRIVATE_FOLDER,"../outside",privateRoot().toString(),
                directory.getParent().resolve("outside").toString()}) {
            assertThrows(AccessDeniedException.class,()->generic.uploadFile(upload,folder));
            String target=folder+"/"+key;
            assertThrows(AccessDeniedException.class,()->generic.getFilePath(target));
            assertThrows(AccessDeniedException.class,()->generic.deleteFile(target));
        }
        assertEquals(1,privateFileCount());assertArrayEquals(original,Files.readAllBytes(privateRoot().resolve(key)));
        assertFalse(Files.exists(directory.resolve("other")));
    }
    @Test void genericNormalFilesStillSupportUploadReadAndDelete() throws Exception {
        var generic=generic();byte[] bytes=pdf();
        String key=generic.uploadFile(new MockMultipartFile("file","report.pdf","application/pdf",bytes),"normal/subfolder");
        assertTrue(key.startsWith("normal/subfolder/"));assertTrue(key.endsWith("-report.pdf"));
        assertArrayEquals(bytes,Files.readAllBytes(generic.getFilePath(key)));
        generic.deleteFile(key);assertFalse(Files.exists(generic.getFilePath(key)));
    }
    @Test void genericUploadRejectsFilenameTraversalOutsideTheUploadRoot() throws Exception {
        var file=new MockMultipartFile("file","/../../outside.pdf","application/pdf",pdf());
        assertThrows(AccessDeniedException.class,()->generic().uploadFile(file,""));
        try(var entries=Files.list(directory)) {assertEquals(0,entries.count());}
    }
    void symlink(Path link,Path target) throws IOException {
        try {Files.createSymbolicLink(link,target);}
        catch(UnsupportedOperationException | IOException | SecurityException unavailable) {
            Assumptions.assumeTrue(false,"Symbolic links unavailable: "+unavailable.getMessage());
        }
    }
    @Test void symlinkCannotExposePrivateEvidenceThroughGenericFileOperations() throws Exception {
        String key=seed(pdf());var generic=generic();Path link=directory.resolve("alias");
        symlink(link,privateRoot());
        assertThrows(AccessDeniedException.class,()->generic.getFilePath("alias/"+key));
        assertThrows(AccessDeniedException.class,()->generic.deleteFile("alias/"+key));
        assertThrows(AccessDeniedException.class,()->generic.uploadFile(new MockMultipartFile("file",pdf()),"alias"));
        assertEquals(1,privateFileCount());
    }
    @Test void evidenceSourceSymlinkIsRefusedWithoutTouchingItsTarget() throws Exception {
        var storage=storage();String key=seed(pdf());Path source=privateRoot().resolve(UUID.randomUUID().toString());
        symlink(source,privateRoot().resolve(key));
        String reference=source.getFileName().toString();
        assertThrows(BadRequestException.class,()->storage.copy(reference,"report.pdf"));
        assertThrows(BadRequestException.class,()->storage.resource(reference));
        assertThrows(BadRequestException.class,()->storage.delete(reference));
        assertTrue(Files.exists(privateRoot().resolve(key)));assertEquals(2,privateFileCount());
    }
}
