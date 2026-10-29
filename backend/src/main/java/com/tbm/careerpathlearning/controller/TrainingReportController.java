package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.service.TrainingReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A session's report (FR-12-07) on screen, and as an Excel workbook, for its trainer, HR and the Super Admin. */
@RestController
@RequestMapping("/api/trainings")
public class TrainingReportController {

    @Autowired
    private TrainingReportService reports;

    @GetMapping("/{id}/report")
    public ResponseEntity<?> report(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(reports.report(id, TrainingViewers.of(authentication)));
    }

    @GetMapping("/{id}/report/file")
    public ResponseEntity<byte[]> reportFile(@PathVariable Long id, Authentication authentication) {
        return TrainingDownloads.of(reports.reportFile(id, TrainingViewers.of(authentication)));
    }
}
