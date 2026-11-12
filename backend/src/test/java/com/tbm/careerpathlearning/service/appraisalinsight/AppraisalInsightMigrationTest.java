package com.tbm.careerpathlearning.service.appraisalinsight;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

/** Static migration safety check; PostgreSQL trigger execution remains an explicitly gated UAT check. */
class AppraisalInsightMigrationTest {
    @Test void migrationIsAdditiveSyntheticOnlyAndDoesNotRewriteScoresOrGrantPermissions() throws Exception {
        String sql = new ClassPathResource("db/migration/annual-kpi/V52__synthetic_appraisal_insight.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        assertTrue(sql.contains("data_mode = 'SYNTHETIC_ONLY'"));
        assertTrue(sql.contains("UNIQUE (owner_id, request_key)")); assertTrue(sql.contains("UNIQUE (active_key)"));
        assertTrue(sql.contains("OLD.status IN ('COMPLETED','FAILED')"));
        assertTrue(sql.contains("BEFORE UPDATE OR DELETE ON appraisal_insight_generation"));
        assertFalse(sql.matches("(?is).*\\b(?:UPDATE|ALTER\\s+TABLE|DROP\\s+TABLE|INSERT\\s+INTO)\\s+"
                + "(?:annual_appraisal|annual_appraisal_activity|staff|role_authority|kpi_assessment|attitude_assessment)\\b.*"));
        assertFalse(sql.contains("appraisal_id"));
    }
}
