package com.tbm.careerpathlearning.service;

import static org.junit.jupiter.api.Assertions.*;

import com.tbm.careerpathlearning.dto.OrganisationPerformanceDto;
import com.tbm.careerpathlearning.dto.OrganisationPerformanceDto.*;
import com.tbm.careerpathlearning.dto.StaffPerformanceDto.Scores;
import com.tbm.careerpathlearning.dto.TeamPerformanceDto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.model.ReviewPeriodParticipant;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

class OrganisationPerformanceExportServiceTest {
    final OrganisationPerformanceExportService exporter =
            new OrganisationPerformanceExportService(
                    Clock.fixed(Instant.parse("2026-10-29T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void reportPreservesTypedScoresCoverageDatesAndIndependentContributors() throws Exception {
        try (var w = open(report(false))) {
            assertEquals(7, w.getNumberOfSheets());
            assertEquals("Executive Summary", w.getSheetAt(0).getSheetName());
            var s = w.getSheetAt(0);
            assertEquals(3, s.getRow(7).getCell(1).getNumericCellValue());
            assertEquals(0, s.getRow(7).getCell(9).getNumericCellValue());
            assertEquals(
                    "0.00%",
                    new org.apache.poi.ss.usermodel.DataFormatter()
                            .formatCellValue(s.getRow(12).getCell(1)));
            assertEquals(80, s.getRow(12).getCell(5).getNumericCellValue());
            assertEquals("0.00\"%\"", s.getRow(12).getCell(5).getCellStyle().getDataFormatString());
            assertEquals("Pending", s.getRow(12).getCell(9).getStringCellValue());
            assertTrue(s.getRow(13).getCell(1).getStringCellValue().contains("1 employee"));
            assertTrue(s.getRow(13).getCell(5).getStringCellValue().contains("2 employees"));
            var d = w.getSheet("Department Performance");
            assertEquals(0, d.getRow(7).getCell(4).getNumericCellValue());
            assertEquals("Pending", d.getRow(7).getCell(8).getStringCellValue());
            assertEquals("Unassigned", d.getRow(7).getCell(13).getStringCellValue());
            assertEquals("Not Comparable", d.getRow(7).getCell(11).getStringCellValue());
            assertEquals("NO_PREVIOUS_PERIOD", d.getRow(7).getCell(14).getStringCellValue());
            assertTrue(
                    org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(
                            w.getSheet("Performance Trends").getRow(7).getCell(2)));
            assertNotNull(d.getPaneInformation());
            assertFalse(d.getTables().isEmpty());
            assertTrue(d.getTables().get(0).getCTTable().isSetAutoFilter());
            for (var sheet : w) {
                assertNotNull(w.getPrintArea(w.getSheetIndex(sheet)));
                for (var row : sheet)
                    for (var c : row) {
                        assertNotEquals(CellType.ERROR, c.getCellType());
                        assertFalse(
                                Set.of(
                                                "Employee ID",
                                                "Staff Name",
                                                "Justification",
                                                "Override Reason")
                                        .contains(c.toString()));
                        if (c.getCellType() == CellType.FORMULA)
                            assertEquals("IF(B8=0,\"Not Available\",F8/B8)", c.getCellFormula());
                    }
            }
        }
    }

    @Test
    void suppressedCountsAreNeverWrittenEvenIfSuppliedIncludingChartCaches() throws Exception {
        var source = report(true);
        var suppressed =
                new DecisionSeries(
                        Disclosure.SUPPRESSED,
                        987654,
                        List.of(
                                new DecisionCount(
                                        AnnualAppraisalRecommendation.PROMOTION, 987654)));
        var payload = withDecisions(source, new DecisionDistribution(5, suppressed, suppressed));
        byte[] bytes = exporter.write(payload);
        try (var w = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var s = w.getSheet("Appraisal Decisions");
            assertTrue(s.getRow(5).getCell(1).getStringCellValue().contains("withheld"));
            assertNull(s.getDrawingPatriarch());
            for (var row : s) for (var c : row) assertNotEquals(CellType.NUMERIC, c.getCellType());
        }
        try (var zip = new java.util.zip.ZipInputStream(new ByteArrayInputStream(bytes))) {
            java.util.zip.ZipEntry e;
            while ((e = zip.getNextEntry()) != null)
                if (e.getName().endsWith(".xml"))
                    assertFalse(
                            new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                                    .contains("987654"),
                            e.getName());
        }
    }

    @Test
    void disclosedOutcomesHaveLinkedChartAndUnavailableSeriesAreExcluded() throws Exception {
        var source = report(true);
        var suppressed = new DecisionSeries(Disclosure.SUPPRESSED, null, List.of());
        try (var w =
                open(
                        withDecisions(
                                source,
                                new DecisionDistribution(
                                        5,
                                        source.appraisalDistribution().superiorRecommendations(),
                                        suppressed)))) {
            var s = w.getSheet("Appraisal Decisions");
            var chart = s.getDrawingPatriarch().getCharts().get(0);
            var bar = chart.getCTChart().getPlotArea().getBarChartArray(0);
            assertEquals(1, bar.sizeOfSerArray());
            var cache = bar.getSerArray(0).getVal().getNumRef();
            assertEquals("'Appraisal Decisions'!$C$14:$C$17", cache.getF());
            assertEquals(
                    10,
                    cache.getNumCache().getPtList().stream()
                            .mapToDouble(p -> Double.parseDouble(p.getV()))
                            .sum());
            assertEquals("Withheld", s.getRow(13).getCell(3).getStringCellValue());
        }
    }

    @Test
    void noDataHasDistinctEmptyStateAndNoChart() throws Exception {
        var none = new DecisionSeries(Disclosure.NO_DATA, 0, List.of());
        try (var w = open(withDecisions(report(false), new DecisionDistribution(5, none, none)))) {
            assertEquals(
                    "No appraisal data available.",
                    w.getSheet("Appraisal Decisions").getRow(5).getCell(1).getStringCellValue());
            assertNull(w.getSheet("Appraisal Decisions").getDrawingPatriarch());
        }
    }

    @Test
    void untrustedNamesStayLiteralAndChartCachesKeepMissingScoresAsGaps() throws Exception {
        try (var w = open(report(true))) {
            var name = w.getSheet("Department Performance").getRow(7).getCell(1);
            assertEquals(CellType.STRING, name.getCellType());
            assertTrue(name.getStringCellValue().startsWith("="));
            var chart =
                    w.getSheet("Performance Trends")
                            .getDrawingPatriarch()
                            .getCharts()
                            .get(0)
                            .getCTChart();
            assertEquals("gap", chart.getDispBlanksAs().getVal().toString());
            var line = chart.getPlotArea().getLineChartArray(0);
            assertEquals(3, line.sizeOfSerArray());
            assertEquals(
                    "0.0",
                    line.getSerArray(0).getVal().getNumRef().getNumCache().getPtArray(0).getV());
            assertEquals(0, line.getSerArray(2).getVal().getNumRef().getNumCache().sizeOfPtArray());
            assertEquals(
                    "'Performance Trends'!$K$8",
                    line.getSerArray(2).getVal().getNumRef().getF());
            assertTrue(
                    line.getSerArray(0)
                            .getCat()
                            .getStrRef()
                            .getStrCache()
                            .getPtArray(0)
                            .getV()
                            .contains("Annual 2025"));
            var progress =
                    w.getSheet("Assessment Progress")
                            .getDrawingPatriarch()
                            .getCharts()
                            .get(0)
                            .getCTChart()
                            .getPlotArea()
                            .getBarChartArray(0);
            assertEquals("stacked", progress.getGrouping().getVal().toString());
            assertEquals(6, progress.sizeOfSerArray());
        }
    }

    @Test
    void exportedBandsAndChartCountsMatchUnroundedDashboardGrouping() throws Exception {
        var population = new ArrayList<ReviewPeriodParticipant>();
        var scores = new HashMap<Long, Scores>();
        for (String raw :
                List.of(
                        "19.5", "59.9999", "60", "69.9999", "70", "79.9999", "80", "89.9999", "90",
                        "100")) {
            var p = new ReviewPeriodParticipant();
            p.setId((long) population.size() + 1);
            population.add(p);
            var value = new BigDecimal(raw);
            scores.put(p.getId(), new Scores(value, value, value, null, null, null));
        }
        var data =
                new TeamPerformanceResults.Data(
                        scores, Map.of(), Map.of(), Map.of(), List.of(), List.of());
        var dist = PerformanceSummaryCalculator.distribution(population, data, Metric.FINAL);
        try (var w = open(report(true, dist))) {
            var s = w.getSheet("Score Distribution");
            assertEquals("Below 60%", s.getRow(8).getCell(1).getStringCellValue());
            assertEquals("90%-100%", s.getRow(12).getCell(1).getStringCellValue());
            for (int i = 0; i < 5; i++) {
                assertEquals(2, s.getRow(8 + i).getCell(2).getNumericCellValue());
                assertEquals(.2, s.getRow(8 + i).getCell(3).getNumericCellValue());
                var raw = s.getRow(39 + i);
                var b = dist.bins().get(i);
                assertEquals(b.lowerInclusive(), raw.getCell(2).getNumericCellValue());
                assertEquals(b.upper(), raw.getCell(3).getNumericCellValue());
                assertEquals(b.upperInclusive(), raw.getCell(4).getBooleanCellValue());
                assertEquals(10, raw.getCell(5).getNumericCellValue());
            }
            var series =
                    s.getDrawingPatriarch()
                            .getCharts()
                            .get(0)
                            .getCTChart()
                            .getPlotArea()
                            .getBarChartArray(0)
                            .getSerArray(0);
            assertEquals("'Score Distribution'!$C$9:$C$13", series.getVal().getNumRef().getF());
            for (var pt : series.getVal().getNumRef().getNumCache().getPtList())
                assertEquals(2, Double.parseDouble(pt.getV()));
        }
    }

    @Test
    void emptyPopulationDoesNotCreateMisleadingAveragesOrDivideByZero() throws Exception {
        var source = report(false);
        var empty = new Average(null, 0);
        var none = new DecisionSeries(Disclosure.NO_DATA, 0, List.of());
        var dto =
                new OrganisationPerformanceDto(
                        source.period(),
                        3,
                        new Summary(0, 0, empty, empty, empty),
                        source.yearOnYear(),
                        List.of(),
                        new Distribution(Metric.KPI, List.of(), 0, 0),
                        List.of(),
                        List.of(),
                        new DecisionDistribution(5, none, none));
        try (var w = open(dto)) {
            assertEquals(
                    "Not Available", w.getSheetAt(0).getRow(7).getCell(9).getStringCellValue());
            assertEquals("Pending", w.getSheetAt(0).getRow(12).getCell(1).getStringCellValue());
            for (var sheet : w)
                assertNull(((org.apache.poi.xssf.usermodel.XSSFSheet) sheet).getDrawingPatriarch());
        }
    }

    @Test
    void missingMiddleHistoryPointRemainsAGapAndPeriodNamesArePreserved() throws Exception {
        var source = report(false);
        var empty = new Average(null, 0);
        var points = new ArrayList<TrendPoint>();
        for (int i = 0; i < 3; i++) {
            var base = source.period();
            var p = new Period((long) i + 1, "2025 Review " + (i + 1),
                    base.startDate().plusMonths(i), base.endDate().plusMonths(i),
                    base.status(), base.frequencyPolicy(), base.assessmentModel(),
                    base.consolidationMethod(), base.kpiWeight(), base.attitudeWeight());
            points.add(new TrendPoint(p, i == 1 ? new Summary(3, 0, empty, empty, empty) : source.summary()));
        }
        var dto = new OrganisationPerformanceDto(source.period(), 5, source.summary(), source.yearOnYear(),
                points, source.distribution(), source.departments(), source.departmentTrends(), source.appraisalDistribution());
        try (var w = open(dto)) {
            var line = w.getSheet("Performance Trends").getDrawingPatriarch().getCharts().get(0)
                    .getCTChart().getPlotArea().getLineChartArray(0);
            var cache = line.getSerArray(0).getVal().getNumRef().getNumCache();
            assertEquals(2, cache.sizeOfPtArray());
            assertEquals(0, cache.getPtArray(0).getIdx());
            assertEquals(2, cache.getPtArray(1).getIdx());
            assertEquals(0, Double.parseDouble(cache.getPtArray(0).getV()));
            assertTrue(line.getSerArray(0).getCat().getStrRef().getStrCache().getPtArray(1).getV().contains("2025 Review 2"));
        }
    }

    private XSSFWorkbook open(OrganisationPerformanceDto report) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(exporter.write(report)));
    }

    private static OrganisationPerformanceDto withDecisions(
            OrganisationPerformanceDto source, DecisionDistribution decisions) {
        return new OrganisationPerformanceDto(
                source.period(),
                source.historyYears(),
                source.summary(),
                source.yearOnYear(),
                source.trend(),
                source.distribution(),
                source.departments(),
                source.departmentTrends(),
                decisions);
    }

    static OrganisationPerformanceDto report(boolean available) {
        return report(
                available,
                new Distribution(
                        Metric.FINAL,
                        List.of(
                                new Bin(0, 60, false, 0),
                                new Bin(60, 70, false, 0),
                                new Bin(70, 80, false, 0),
                                new Bin(80, 90, false, 0),
                                new Bin(90, 100, true, 0)),
                        0,
                        3));
    }

    static OrganisationPerformanceDto report(boolean available, Distribution distribution) {
        var period =
                new Period(
                        1L,
                        "Annual 2025",
                        LocalDate.of(2025, 1, 1),
                        LocalDate.of(2025, 12, 31),
                        AnnualKpiReviewPeriodStatus.OPEN,
                        KpiFrequencyPolicy.DEPARTMENT_BASED,
                        KpiAssessmentModel.CENTRAL_RESULTS,
                        AnnualKpiConsolidationMethod.AVERAGE,
                        new BigDecimal("50"),
                        new BigDecimal("50"));
        var summary =
                new Summary(
                        3,
                        0,
                        new Average(BigDecimal.ZERO, 1),
                        new Average(new BigDecimal("80"), 2),
                        new Average(null, 0));
        var change = new Change(null, Comparison.NO_PREVIOUS_PERIOD, null);
        var point = new TrendPoint(period, summary);
        var series =
                available
                        ? new DecisionSeries(
                                Disclosure.AVAILABLE,
                                10,
                                List.of(
                                        new DecisionCount(
                                                AnnualAppraisalRecommendation.PROMOTION, 5),
                                        new DecisionCount(
                                                AnnualAppraisalRecommendation.SALARY_INCREMENT, 5)))
                        : new DecisionSeries(Disclosure.SUPPRESSED, null, List.of());
        return new OrganisationPerformanceDto(
                period,
                5,
                summary,
                change,
                List.of(point),
                distribution,
                List.of(
                        new Department(
                                null,
                                "=HYPERLINK(\"invalid\")",
                                summary,
                                new Progress(2, 1, 1, 1, 0),
                                1,
                                change)),
                List.of(new DepartmentTrend(null, "Unassigned Department", List.of(point))),
                new DecisionDistribution(5, series, series));
    }
}
