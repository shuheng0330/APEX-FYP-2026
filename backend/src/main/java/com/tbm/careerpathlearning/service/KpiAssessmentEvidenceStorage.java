package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.exception.BadRequestException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.file.*;
import java.util.UUID;

@Component
public class KpiAssessmentEvidenceStorage {
    public static final String PRIVATE_FOLDER=".kpi-assessment-evidence";
    private final Path root;
    private final long maxBytes;
    public record Stored(String key,String filename,String contentType,long size) {}

    public KpiAssessmentEvidenceStorage(@Value("${file.upload-dir}") String uploadDirectory,
            @Value("${assessment.evidence.max-bytes:10485760}") long maxBytes) {
        if(maxBytes<=0) throw new IllegalArgumentException("Evidence size limit must be positive");
        root=Path.of(uploadDirectory).toAbsolutePath().normalize().resolve(PRIVATE_FOLDER);
        this.maxBytes=maxBytes;
    }
    public Stored store(MultipartFile file) {
        if(file==null || file.isEmpty() || file.getSize()>maxBytes)
            throw new BadRequestException("Attach a nonempty PDF, PNG or JPEG within the evidence size limit");
        try {return store(file.getInputStream(),file.getOriginalFilename());}
        catch(BadRequestException e) {throw e;}
        catch(IOException e) {throw new IllegalStateException("Could not store assessment evidence",e);}
    }
    public Stored copy(String key,String originalFilename) {
        Path source=path(key);
        if(!Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS))
            throw new BadRequestException("This evidence file is unavailable");
        try {return store(Files.newInputStream(source,LinkOption.NOFOLLOW_LINKS),originalFilename);}
        catch(NoSuchFileException e) {throw new BadRequestException("This evidence file is unavailable");}
        catch(IOException e) {throw new IllegalStateException("Could not copy assessment evidence",e);}
    }
    private Stored store(InputStream input,String originalFilename) throws IOException {
        byte[] bytes;
        try(input) {bytes=input.readNBytes((int)Math.min(maxBytes,Integer.MAX_VALUE-1L)+1);}
        if(bytes.length==0 || bytes.length>maxBytes)
            throw new BadRequestException("Attach nonempty evidence within the configured size limit");
        String type=verifiedType(bytes);
        String filename=safeFilename(originalFilename);
        String key=UUID.randomUUID().toString();
        Path target=path(key);
        Files.createDirectories(root);
        // Never remove a pre-existing file if exclusive creation fails.
        boolean created=false;
        try {
            try(var output=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
                created=true;
                output.write(bytes);
            }
        } catch(IOException failure) {
            if(created) {
                try {Files.deleteIfExists(target);}
                catch(IOException cleanup) {failure.addSuppressed(cleanup);}
            }
            throw failure;
        }
        return new Stored(key,filename,type,bytes.length);
    }
    public Resource resource(String key) {
        Path source=path(key);
        if(!Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS))
            throw new BadRequestException("This evidence file is unavailable");
        return new FileSystemResource(source);
    }
    public void delete(String key) {
        try {Files.deleteIfExists(path(key));}
        catch(IOException e) {throw new IllegalStateException("Could not remove assessment evidence",e);}
    }
    private Path path(String key) {
        if(key==null || !key.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw new BadRequestException("Invalid evidence storage reference");
        Path target=root.resolve(key);
        if(Files.isSymbolicLink(root) || Files.isSymbolicLink(target))
            throw new BadRequestException("Invalid evidence storage reference");
        return target;
    }
    private String safeFilename(String original) {
        if(original==null || original.isBlank()) return "evidence";
        String name=original.replace('\\','/');name=name.substring(name.lastIndexOf('/')+1);
        name=name.replaceAll("[\\p{Cntrl}]","").strip();
        if(name.isBlank() || name.equals(".") || name.equals("..")) return "evidence";
        return name.substring(0,Math.min(name.length(),255));
    }
    private String verifiedType(byte[] bytes) throws IOException {
        if(bytes.length>=5 && new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-")) {
            try(var document=PDDocument.load(bytes)) {
                if(document.getNumberOfPages()==0) throw new BadRequestException("Attach a valid PDF with at least one page");
                return "application/pdf";
            } catch(IOException e) {throw new BadRequestException("Attach a valid PDF, PNG or JPEG");}
        }
        try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext()) throw new BadRequestException("Attach a valid PDF, PNG or JPEG");
            var reader=readers.next();
            try {
                reader.setInput(input);
                String format=reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                if(!format.equals("png") && !format.equals("jpeg")) throw new BadRequestException("Attach a PDF, PNG or JPEG");
                // Bound decompression independently of the compressed upload size.
                if((long)reader.getWidth(0)*reader.getHeight(0)>25000000L)
                    throw new BadRequestException("This image is too large to process safely");
                if(reader.read(0)==null) throw new BadRequestException("Attach a valid image");
                return format.equals("png")?"image/png":"image/jpeg";
            } catch(IOException e) {throw new BadRequestException("Attach a valid PDF, PNG or JPEG");}
            finally {reader.dispose();}
        }
    }
}
