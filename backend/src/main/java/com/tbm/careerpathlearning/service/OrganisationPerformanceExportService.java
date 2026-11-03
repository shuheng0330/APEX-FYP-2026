package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.OrganisationPerformanceDto;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;

/** An in-memory summary workbook. It never reads entities or writes files/records. */
@Component
public class OrganisationPerformanceExportService {
    private final Clock clock;

    public OrganisationPerformanceExportService(@Qualifier("annualKpiReviewClock") Clock clock) {
        this.clock = clock;
    }

    public byte[] write(OrganisationPerformanceDto report) throws IOException {
        try (var workbook = new XSSFWorkbook();
                var output = new ByteArrayOutputStream()) {
            new OrganisationPerformanceReport(workbook, report, clock).build();
            workbook.write(output);
            return output.toByteArray();
        }
    }
}
