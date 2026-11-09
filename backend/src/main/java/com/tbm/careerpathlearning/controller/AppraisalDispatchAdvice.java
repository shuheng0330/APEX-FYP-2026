package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.aspect.MetadataOnlyLogging;
import com.tbm.careerpathlearning.service.appraisalinsight.GeminiDispatchGate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.*;

@RestControllerAdvice(assignableTypes=AppraisalInsightController.class) @MetadataOnlyLogging
public class AppraisalDispatchAdvice {
    public record Notice(String reason,String message,OffsetDateTime serverTime,OffsetDateTime retryAt) {}
    @ExceptionHandler(GeminiDispatchGate.Rejected.class)
    public ResponseEntity<Notice> refused(GeminiDispatchGate.Rejected failure) {
        var now=OffsetDateTime.now(ZoneOffset.UTC);boolean missing="NOT_CONFIGURED".equals(failure.code());
        var response=ResponseEntity.status(missing?400:429);
        if (!missing) response.header("Retry-After",Long.toString(failure.retryAt()==null?5:Math.max(1,Duration.between(now,failure.retryAt()).toSeconds())));
        return response.body(new Notice(failure.code(),missing?"AI generation coordination is not configured":"AI generation is busy or temporarily rate limited",now,failure.retryAt()));
    }
}
