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
        try(var input=file.getInputStream()) {
            byte[] bytes=input.readNBytes((int)Math.min(maxBytes+1,Integer.MAX_VALUE));
            if(bytes.length==0 || bytes.length>maxBytes) throw new BadRequestException("Evidence exceeds the configured size limit");
            String type=verifiedType(bytes);
            String filename=safeFilename(file.getOriginalFilename());
            String key=UUID.randomUUID().toString();
            Files.createDirectories(root);
            Path target=path(key);
            try {Files.write(target,bytes,StandardOpenOption.CREATE_NEW);}
            catch(IOException failure) {Files.deleteIfExists(target);throw failure;}
            return new Stored(key,filename,type,bytes.length);
        } catch(BadRequestException e) {throw e;}
        catch(IOException e) {throw new IllegalStateException("Could not store assessment evidence",e);}
    }
    public Resource resource(String key) {
        var resource=new FileSystemResource(path(key));
        if(!resource.exists()) throw new BadRequestException("This evidence file is unavailable");
        return resource;
    }
    public void delete(String key) {
        try {Files.deleteIfExists(path(key));}
        catch(IOException e) {throw new IllegalStateException("Could not remove assessment evidence",e);}
    }
    private Path path(String key) {
        if(key==null || !key.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw new BadRequestException("Invalid evidence storage reference");
        return root.resolve(key);
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
