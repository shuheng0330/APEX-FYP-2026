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
    void managementSheetsKeepScoresCountsAndReferenceFieldsSeparate() throws Exception {
        try (var w = open(report(false))) {
            assertEquals(8, w.getNumberOfSheets());
            assertEquals("Executive Summary", w.getSheetAt(0).getSheetName());
            assertEquals("Report Data", w.getSheetAt(7).getSheetName());
            var summary = w.getSheetAt(0);
            assertEquals(
                    "Average Final Appraisal Score",
                    summary.getRow(6).getCell(5).getStringCellValue());
            assertEquals("Final Score Coverage", summary.getRow(6).getCell(9).getStringCellValue());
            assertEquals("Pending", summary.getRow(7).getCell(5).getStringCellValue());
            assertEquals(0, summary.getRow(7).getCell(9).getNumericCellValue());
            assertEquals(0, summary.getRow(12).getCell(1).getNumericCellValue());
            assertEquals(80, summary.getRow(12).getCell(5).getNumericCellValue());
            assertEquals(
                    "0.00\"%\"",
                    summary.getRow(12).getCell(5).getCellStyle().getDataFormatString());
            assertEquals(0, cell(w, "DepartmentPerformance", 0, "KPI Score").getNumericCellValue());
            assertEquals(
                    "0.00\"%\"",
                    cell(w, "DepartmentPerformance", 0, "KPI Score")
                            .getCellStyle()
                            .getDataFormatString());
            assertTrue(
                    cell(w, "DepartmentPerformance", 0, "Department")
                            .getStringCellValue()
                            .contains("Employees with results: KPI 1 | Attitude 2 | Final 0"));
            assertEquals(
                    "Pending",
                    cell(w, "DepartmentPerformance", 0, "Final Score").getStringCellValue());
            assertEquals(
                    6, w.getSheet("Department Performance").getTables().get(0).getColumnCount());
            assertEquals(
                    "Unassigned",
                    cell(w, "DepartmentReferences", 0, "Department ID").getStringCellValue());
            assertEquals(
                    2,
                    cell(w, "DepartmentReferences", 0, "Attitude Employees with Results")
                            .getNumericCellValue());
            assertEquals(
                    "NO_PREVIOUS_PERIOD",
                    cell(w, "DepartmentReferences", 0, "Comparison Code").getStringCellValue());
            for (var sheet : w) {
                assertNotNull(w.getPrintArea(w.getSheetIndex(sheet)));
                for (var row : sheet)
                    for (var c : row) {
                        assertNotEquals(CellType.ERROR, c.getCellType());
                        if (!sheet.getSheetName().equals("Report Data")
                                && c.getCellType() == CellType.STRING) {
                            assertFalse(c.getStringCellValue().contains("Contributors"));
                            assertFalse(
                                    Set.of(
                                                    "Department ID",
                                                    "Period ID",
                                                    "Comparison Code",
                                                    "Lower Bound (inclusive)",
                                                    "NO_PREVIOUS_PERIOD",
                                                    "INCOMPATIBLE_CONFIGURATION")
                                            .contains(c.getStringCellValue()));
                        }
                    }
            }
        }
    }

    @Test
    void pendingDepartmentsAreExcludedFromChartButValidZeroIsIncluded() throws Exception {
        var source = report(false);
        var average = new Average(BigDecimal.ZERO, 1);
        var available = new Summary(3, 1, average, average, average);
        var departments =
                List.of(
                        new Department(
                                1L,
                                "Available Zero",
                                available,
                                new Progress(1, 2, 3, 4, 5),
                                6,
                                source.yearOnYear()),
                        new Department(
                                2L,
                                "Pending Department",
                                source.summary(),
                                new Progress(0, 0, 0, 0, 0),
                                0,
                                source.yearOnYear()));
        var dto =
                new OrganisationPerformanceDto(
                        source.period(),
                        5,
                        source.summary(),
                        source.yearOnYear(),
                        source.trend(),
                        source.distribution(),
                        departments,
                        source.departmentTrends(),
                        source.appraisalDistribution());
        try (var w = open(dto)) {
            var chart =
                    w.getSheet("Department Performance").getDrawingPatriarch().getCharts().get(0);
            var series = chart.getCTChart().getPlotArea().getBarChartArray(0).getSerArray(0);
            assertEquals(1, series.getCat().getStrRef().getStrCache().sizeOfPtArray());
            var axes = chart.getCTChart().getPlotArea();
            assertEquals("autoZero", axes.getCatAxArray(0).getCrosses().getVal().toString());
            assertEquals("max", axes.getValAxArray(0).getCrosses().getVal().toString());
            assertFalse(axes.getValAxArray(0).getNumFmt().getSourceLinked());
            assertEquals(
                    "Available Zero",
                    series.getCat().getStrRef().getStrCache().getPtArray(0).getV());
            assertEquals(
                    0,
                    Double.parseDouble(
                            series.getVal().getNumRef().getNumCache().getPtArray(0).getV()));
            assertEquals(
                    "Pending",
                    cell(w, "DepartmentPerformance", 1, "Final Score").getStringCellValue());
            assertEquals(
                    21,
                    cell(w, "AssessmentProgress", 0, "Total Outstanding Tasks")
                            .getNumericCellValue());
            assertEquals(
                    21, cell(w, "TaskTotals", 0, "Total Outstanding Tasks").getNumericCellValue());
            var tasks =
                    w.getSheet("Assessment Progress")
                            .getDrawingPatriarch()
                            .getCharts()
                            .get(0)
                            .getCTChart()
                            .getPlotArea()
                            .getBarChartArray(0);
            assertEquals(1, tasks.sizeOfSerArray());
            assertEquals(
                    21,
                    Double.parseDouble(
                            tasks.getSerArray(0)
                                    .getVal()
                                    .getNumRef()
                                    .getNumCache()
                                    .getPtArray(0)
                                    .getV()));
            assertTrue(
                    w.getSheet("Assessment Progress")
                                    .getTables()
                                    .get(0)
                                    .getStartCellReference()
                                    .getRow()
                            > w.getSheet("Assessment Progress")
                                    .getDrawingPatriarch()
                                    .getCTDrawing()
                                    .getTwoCellAnchorArray(0)
                                    .getTo()
                                    .getRow());
        }
    }

    @Test
    void suppressedCountsNeverAppearInCellsOrChartCachesAndUseOneNotice() throws Exception {
        var source = report(true);
        var suppressed =
                new DecisionSeries(
                        Disclosure.SUPPRESSED,
                        987654,
                        List.of(
                                new DecisionCount(
                                        AnnualAppraisalRecommendation.PROMOTION, 987654)));
        byte[] bytes =
                exporter.write(
                        withDecisions(source, new DecisionDistribution(5, suppressed, suppressed)));
        try (var w = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var s = w.getSheet("Appraisal Decisions");
            assertNull(s.getDrawingPatriarch());
            long notices = 0;
            for (var row : s)
                for (var c : row) {
                    assertNotEquals(CellType.NUMERIC, c.getCellType());
                    if (c.getCellType() == CellType.STRING
                            && c.getStringCellValue().contains("to protect employee privacy"))
                        notices++;
                }
            assertEquals(1, notices);
        }
        try (var zip = new java.util.zip.ZipInputStream(new ByteArrayInputStream(bytes))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null)
                if (entry.getName().endsWith(".xml"))
                    assertFalse(
                            new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                                    .contains("987654"),
                            entry.getName());
        }
    }

    @Test
    void disclosedSeriesStayNumericAndSuppressedSeriesAreNotCharted() throws Exception {
        var source = report(true);
        var hidden = new DecisionSeries(Disclosure.SUPPRESSED, null, List.of());
        try (var w =
                open(
                        withDecisions(
                                source,
                                new DecisionDistribution(
                                        5,
                                        source.appraisalDistribution().superiorRecommendations(),
                                        hidden)))) {
            var s = w.getSheet("Appraisal Decisions");
            var bars =
                    s.getDrawingPatriarch()
                            .getCharts()
                            .get(0)
                            .getCTChart()
                            .getPlotArea()
                            .getBarChartArray(0);
            assertEquals(1, bars.sizeOfSerArray());
            assertEquals(
                    10,
                    bars.getSerArray(0).getVal().getNumRef().getNumCache().getPtList().stream()
                            .mapToDouble(p -> Double.parseDouble(p.getV()))
                            .sum());
            assertEquals(
                    "Withheld",
                    cell(w, "AppraisalOutcomes", 0, "HR Final Decisions").getStringCellValue());
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
    void exportedBandsAndHistogramCountLabelsPreserveDecimalGrouping() throws Exception {
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
        var distribution =
                PerformanceSummaryCalculator.distribution(population, data, Metric.FINAL);
        try (var w = open(report(true, distribution))) {
            for (int i = 0; i < 5; i++) {
                assertEquals(2, cell(w, "ScoreDistribution", i, "Employees").getNumericCellValue());
                assertEquals(
                        .2,
                        cell(w, "ScoreDistribution", i, "Share of Available Results")
                                .getNumericCellValue());
                assertEquals(
                        distribution.bins().get(i).lowerInclusive(),
                        cell(w, "ScoreBandDefinitions", i, "Lower Bound (inclusive)")
                                .getNumericCellValue());
                assertEquals(
                        distribution.bins().get(i).upper(),
                        cell(w, "ScoreBandDefinitions", i, "Upper Bound").getNumericCellValue());
            }
            var bars =
                    w.getSheet("Score Distribution")
                            .getDrawingPatriarch()
                            .getCharts()
                            .get(0)
                            .getCTChart()
                            .getPlotArea()
                            .getBarChartArray(0);
            assertTrue(bars.getDLbls().getShowVal().getVal());
            assertEquals("outEnd", bars.getDLbls().getDLblPos().getVal().toString());
            for (var pt : bars.getSerArray(0).getVal().getNumRef().getNumCache().getPtList())
                assertEquals(2, Double.parseDouble(pt.getV()));
        }
    }

    @Test
    void untrustedTextIsLiteralAndShortPeriodLabelsRetainFullHistory() throws Exception {
        try (var w = open(report(true))) {
            var name = cell(w, "DepartmentPerformance", 0, "Department");
            assertEquals(CellType.STRING, name.getCellType());
            assertTrue(name.getStringCellValue().startsWith("="));
            var chart =
                    w.getSheet("Performance Trends")
                            .getDrawingPatriarch()
                            .getCharts()
                            .get(0)
                            .getCTChart();
            assertEquals("gap", chart.getDispBlanksAs().getVal().toString());
            var lines = chart.getPlotArea().getLineChartArray(0);
            assertEquals(3, lines.sizeOfSerArray());
            assertEquals(
                    "2025 Annual",
                    lines.getSerArray(0).getCat().getStrRef().getStrCache().getPtArray(0).getV());
            assertEquals(
                    0, lines.getSerArray(2).getVal().getNumRef().getNumCache().sizeOfPtArray());
            assertEquals(
                    "Annual 2025",
                    cell(w, "OrganisationHistory", 0, "Review Period").getStringCellValue());
            assertTrue(
                    org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(
                            cell(w, "OrganisationHistory", 0, "Start Date")));
        }
    }

    @Test
    void missingMiddleHistoryPointIsAGapAndDifferentDatesHaveDistinctLabels() throws Exception {
        var source = report(false);
        var empty = new Average(null, 0);
        var points = new ArrayList<TrendPoint>();
        for (int i = 0; i < 3; i++) {
            var base = source.period();
            var p =
                    new Period(
                            (long) i + 1,
                            "2025 Review " + (i + 1),
                            base.startDate().plusMonths(i),
                            base.endDate().plusMonths(i),
                            base.status(),
                            base.frequencyPolicy(),
                            base.assessmentModel(),
                            base.consolidationMethod(),
                            base.kpiWeight(),
                            base.attitudeWeight());
            points.add(
                    new TrendPoint(
                            p, i == 1 ? new Summary(3, 0, empty, empty, empty) : source.summary()));
        }
        var dto =
                new OrganisationPerformanceDto(
                        source.period(),
                        5,
                        source.summary(),
                        source.yearOnYear(),
                        points,
                        source.distribution(),
                        source.departments(),
                        source.departmentTrends(),
                        source.appraisalDistribution());
        try (var w = open(dto)) {
            var series =
                    w.getSheet("Performance Trends")
                            .getDrawingPatriarch()
                            .getCharts()
                            .get(0)
                            .getCTChart()
                            .getPlotArea()
                            .getLineChartArray(0)
                            .getSerArray(0);
            var cache = series.getVal().getNumRef().getNumCache();
            assertEquals(2, cache.sizeOfPtArray());
            assertEquals(0, cache.getPtArray(0).getIdx());
            assertEquals(2, cache.getPtArray(1).getIdx());
            assertNotEquals(
                    series.getCat().getStrRef().getStrCache().getPtArray(0).getV(),
                    series.getCat().getStrRef().getStrCache().getPtArray(1).getV());
            var matrix = w.getSheet("Department Trends");
            assertEquals("Pending", matrix.getRow(7).getCell(2).getStringCellValue());
            assertEquals("Not Available", matrix.getRow(7).getCell(3).getStringCellValue());
        }
    }

    @Test
    void emptyPopulationHasNoMisleadingAveragesOrCharts() throws Exception {
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
            assertEquals("Pending", w.getSheetAt(0).getRow(7).getCell(5).getStringCellValue());
            for (var sheet : w)
                assertNull(((org.apache.poi.xssf.usermodel.XSSFSheet) sheet).getDrawingPatriarch());
        }
    }

    private XSSFWorkbook open(OrganisationPerformanceDto report) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(exporter.write(report)));
    }

    private org.apache.poi.ss.usermodel.Cell cell(
            XSSFWorkbook w, String tableName, int row, String column) {
        for (var s : w)
            for (var t : ((org.apache.poi.xssf.usermodel.XSSFSheet) s).getTables())
                if (tableName.equals(t.getName())) {
                    var header = s.getRow(t.getStartCellReference().getRow());
                    for (int c = t.getStartCellReference().getCol();
                            c <= t.getEndCellReference().getCol();
                            c++)
                        if (column.equals(header.getCell(c).getStringCellValue()))
                            return s.getRow(header.getRowNum() + 1 + row).getCell(c);
                }
        throw new AssertionError(tableName + ": " + column);
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
