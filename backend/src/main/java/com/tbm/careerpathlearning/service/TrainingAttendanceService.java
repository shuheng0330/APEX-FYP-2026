package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.TrainingAttendanceRequest;
import com.tbm.careerpathlearning.dto.TrainingAttendanceSheetDto;
import com.tbm.careerpathlearning.dto.TrainingBatchResultDto;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Recording attendance at training sessions (FR-14). The session's trainer, or HR, records it from the start until 7
 * days after the session; after that, and once it is submitted, only HR can change it, giving a reason that is logged.
 * Every date and time is Malaysia time.
 */
public interface TrainingAttendanceService {

    /** The session's attendance, for its trainer, HR and the Super Admin. */
    TrainingAttendanceSheetDto sheet(Long sessionId, TrainingViewer viewer);

    /** Marks people Attended or Absent, with a quiz score if there was one, or clears a mark (FR-14-01 to FR-14-03). */
    TrainingAttendanceSheetDto record(Long sessionId, TrainingAttendanceRequest request, TrainingViewer viewer);

    /** Adds someone who attended without registering (decision A). */
    TrainingAttendanceSheetDto addWalkIn(Long sessionId, UUID staffId, String reason, TrainingViewer viewer);

    /** The final confirmation, once everyone is marked; only then does the session count as training (FR-14-09). */
    TrainingAttendanceSheetDto submit(Long sessionId, String reason, TrainingViewer viewer);

    /** Attaches a photo or PDF as evidence (FR-14-08): up to 5 files of 10 MB each. */
    TrainingAttendanceSheetDto addEvidence(Long sessionId, MultipartFile file, TrainingViewer viewer);

    TrainingAttendanceSheetDto removeEvidence(Long sessionId, Long evidenceId, TrainingViewer viewer);

    /** An evidence file, for whoever may see the session's attendance. */
    EvidenceFile evidenceFile(Long sessionId, Long evidenceId, TrainingViewer viewer);

    /**
     * The HRDC attendance template as an Excel workbook, listing everyone on the session with any marks so far
     * (FR-14-05). HR's copy has full IC or passport numbers, and is logged; the trainer's shows their last 4 characters.
     */
    TrainingDownload hrdcTemplate(Long sessionId, TrainingViewer viewer);

    /**
     * Applies a filled-in template, or a CSV with the same columns, in one go: rows are matched by email, then by
     * exact name; a blank Attended cell changes nothing; rows for people not on the session are listed and skipped
     * (FR-14-06, FR-14-07, decisions B1 and B2 of 28 Oct 2026). Any invalid row refuses the whole file.
     */
    TrainingBatchResultDto uploadBatch(Long sessionId, MultipartFile file, String reason, TrainingViewer viewer);

    record EvidenceFile(Path path, String fileName, String contentType) {
    }
}
