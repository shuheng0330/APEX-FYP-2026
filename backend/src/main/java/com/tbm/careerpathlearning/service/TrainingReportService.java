package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.TrainingSessionReportDto;

/** A session's report (FR-12-07), for the session's trainer, HR and the Super Admin. */
public interface TrainingReportService {

    /** For the screen: IC or passport numbers show their last 4 characters. */
    TrainingSessionReportDto report(Long sessionId, TrainingViewer viewer);

    /**
     * The same report as an Excel workbook (decision D, 28 Oct 2026). HR's copy has full IC or passport numbers, for
     * HRDC claims, and its download is logged; anyone else's shows their last 4 characters.
     */
    TrainingDownload reportFile(Long sessionId, TrainingViewer viewer);
}
