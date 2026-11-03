package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.service.TrainingDownload;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

/** Sends a training file to save. It may hold personal data, so it is never cached. */
final class TrainingDownloads {

    private TrainingDownloads() {
    }

    static ResponseEntity<byte[]> of(TrainingDownload file) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(file.content());
    }
}
