package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.TrainingSessionReportDto;

/** A session's report (FR-12-07), for the session's trainer, HR and the Super Admin. */
public interface TrainingReportService {

    /** For the screen. IC or passport numbers show in full for whoever records the session's attendance. */
    TrainingSessionReportDto report(Long sessionId, TrainingViewer viewer);

    /**
     * The same report as an Excel workbook (decision D, 28 Oct 2026), for HRDC claims. A download with IC or passport
     * numbers is logged.
     */
    TrainingDownload reportFile(Long sessionId, TrainingViewer viewer);
}
