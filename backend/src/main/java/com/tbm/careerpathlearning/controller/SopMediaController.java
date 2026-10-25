package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Image and video attachments for generated training material (FR-07-06).
 *
 * Trainers add an image or video block in the material editor; the file is stored
 * under the SOP media folder and the returned URL is embedded in the module's
 * content JSON as the block's "url".
 */
@RestController
@RequestMapping("/api/sop/media")
public class SopMediaController {

    /** Folder under the configured upload dir. */
    private static final String MEDIA_DIR = "sop-media";

    private static final Set<String> IMAGE_TYPES =
            Set.of("image/png", "image/jpeg", "image/gif", "image/webp", "image/svg+xml");
    private static final Set<String> VIDEO_TYPES =
            Set.of("video/mp4", "video/webm", "video/ogg", "video/quicktime");

    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;   // 10 MB
    private static final long MAX_VIDEO_BYTES = 200L * 1024 * 1024;  // 200 MB

    @Autowired
    private FileService fileService;

    /**
     * Stores one image or video and returns the URL to embed in the block.
     * The response shape matches what the material editor expects.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('CAN_MANAGE_TRAINING')")
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("No file was uploaded.");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        boolean isImage = IMAGE_TYPES.contains(contentType);
        boolean isVideo = VIDEO_TYPES.contains(contentType);
        if (!isImage && !isVideo) {
            throw new BadRequestException(
                    "Unsupported media type. Images: PNG, JPEG, GIF, WEBP, SVG. Videos: MP4, WEBM, OGG, MOV.");
        }
        long limit = isImage ? MAX_IMAGE_BYTES : MAX_VIDEO_BYTES;
        if (file.getSize() > limit) {
            throw new BadRequestException(
                    (isImage ? "Image" : "Video") + " is too large. Maximum " + (limit / (1024 * 1024)) + " MB.");
        }

        String stored = fileService.uploadFile(file, MEDIA_DIR);
        String filename = Path.of(stored).getFileName().toString();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("url", "/api/sop/media/" + filename);
        body.put("filename", file.getOriginalFilename());
        body.put("contentType", contentType);
        body.put("size", file.getSize());
        body.put("kind", isImage ? "image" : "video");
        return ResponseEntity.ok(body);
    }

    /**
     * Streams a stored media file. Left unauthenticated at method level so the
     * browser can load it directly from an <img>/<video> src, which does not send
     * the bearer token.
     */
    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> serve(@PathVariable String filename) {
        // Reject traversal: only a bare filename is ever valid here.
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new BadRequestException("Invalid media filename.");
        }
        try {
            Path path = fileService.getFilePath(MEDIA_DIR + "/" + filename);
            if (!Files.exists(path) || !Files.isReadable(path)) {
                return ResponseEntity.notFound().build();
            }
            String contentType = Files.probeContentType(path);
            Resource resource = new UrlResource(path.toUri());
            return ResponseEntity.ok()
                    .contentType(contentType != null
                            ? MediaType.parseMediaType(contentType)
                            : MediaType.APPLICATION_OCTET_STREAM)
                    // Inline so images render and video can seek rather than downloading.
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                    .body(resource);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Could not read media file: " + e.getMessage(), e);
        }
    }
}
