package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.OrganisationPerformanceDto;
import com.tbm.careerpathlearning.dto.OrganisationPerformanceDto.*;
import com.tbm.careerpathlearning.dto.TeamPerformanceDto.*;

import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.*;
import org.apache.poi.xddf.usermodel.*;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xssf.usermodel.*;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Presentation of an already authorised report; no repositories or performance calculations. */
final class OrganisationPerformanceReport {
    static final String SCORE_FORMAT = "0.00\"%\"";
    static final String PP_FORMAT = "+0.00\" pp\";-0.00\" pp\";0.00\" pp\"";
    private static final String[] COLOURS = {
        "2456A6", "087A6E", "7950C9", "B57600", "54758A", "A44866"
    };
    private final XSSFWorkbook book;
    private final OrganisationPerformanceDto report;
    private final OffsetDateTime generated;
    private final XSSFCellStyle body,
            header,
            title,
            section,
            note,
            score,
            count,
            date,
            pp,
            percentage,
            cardLabel,
            cardValue,
            cardScore;

    OrganisationPerformanceReport(
            XSSFWorkbook book, OrganisationPerformanceDto report, Clock clock) {
        this.book = book;
        this.report = report;
        this.generated = OffsetDateTime.now(clock);
        body = style(11, false, "24344D", null);
        body.setWrapText(true);
        header = style(11, true, "FFFFFF", "2456A6");
        header.setWrapText(true);
        header.setAlignment(HorizontalAlignment.CENTER);
        title = style(17, true, "17365D", null);
        section = style(12, true, "17365D", "EDF2F8");
        note = style(11, false, "52647B", null);
        note.setWrapText(true);
        score = number(SCORE_FORMAT);
        count = number("#,##0");
        date = number("dd mmm yyyy");
        pp = number(PP_FORMAT);
        percentage = number("0.0%");
        cardLabel = style(11, true, "52647B", "F1F5FA");
        cardLabel.setWrapText(true);
        cardValue = style(22, true, "2456A6", "F1F5FA");
        cardValue.setAlignment(HorizontalAlignment.LEFT);
        cardValue.setDataFormat(book.createDataFormat().getFormat("#,##0"));
        cardScore = book.createCellStyle();
        cardScore.cloneStyleFrom(cardValue);
        cardScore.setDataFormat(book.createDataFormat().getFormat(SCORE_FORMAT));
    }

    void build() {
        executive();
        departments();
        progress();
        trends();
        distribution();
        decisions();
        departmentTrends();
        book.setActiveSheet(0);
        book.setSelectedTab(0);
        book.getCreationHelper().createFormulaEvaluator().evaluateAll();
    }

    private void executive() {
        var s = sheet("Executive Summary", "Organisation Performance Analysis Report", 12);
        for (int c = 1; c <= 12; c++) s.setColumnWidth(c, 11 * 256);
        var summary = report.summary();
        card(
                s,
                6,
                1,
                "Participating Employees",
                summary.totalEmployees(),
                count,
                "Selected Review Period");
        card(
                s,
                6,
                5,
                "Employees with Final Scores",
                summary.employeesWithResults(),
                count,
                "Of " + summary.totalEmployees() + " participating employees");
        card(
                s,
                6,
                9,
                "Final Score Coverage",
                null,
                percentage,
                "Share of employees with a Final Score");
        var coverage = s.getRow(7).getCell(9);
        coverage.setCellFormula("IF(B8=0,\"Not Available\",F8/B8)");
        var coverageStyle = book.createCellStyle();
        coverageStyle.cloneStyleFrom(cardValue);
        coverageStyle.setDataFormat(percentage.getDataFormat());
        coverage.setCellStyle(coverageStyle);
        card(
                s,
                11,
                1,
                "Average KPI Performance Score",
                summary.kpi().score(),
                score,
                contributors(summary.kpi().contributingEmployees()));
        card(
                s,
                11,
                5,
                "Average Attitude Evaluation Score",
                summary.attitude().score(),
                score,
                contributors(summary.attitude().contributingEmployees()));
        card(
                s,
                11,
                9,
                "Average Final Appraisal Score",
                summary.finalScore().score(),
                score,
                contributors(summary.finalScore().contributingEmployees()));
        span(s, 17, 1, 12, "Key Observations", section, 25);
        String coverageText =
                summary.totalEmployees() == 0
                        ? "No employees are included in this Review Period."
                        : "Final Scores are available for "
                                + summary.employeesWithResults()
                                + " of "
                                + summary.totalEmployees()
                                + " employees. "
                                + (summary.employeesWithResults() < summary.totalEmployees()
                                        ? "The current average may change as more assessments are"
                                              + " completed."
                                        : "All participating employees have a Final Score.");
        span(s, 18, 1, 12, coverageText, body, 38);
        int tasks =
                report.departments().stream()
                        .mapToInt(
                                d -> {
                                    var p = d.assessmentProgress();
                                    return p.kpiSelfAssessments()
                                            + p.kpiSuperiorReviews()
                                            + p.attitudeSelfAssessments()
                                            + p.attitudeSuperiorReviews()
                                            + p.returnedIndividualPlans()
                                            + d.pendingHrReview();
                                })
                        .sum();
        span(
                s,
                20,
                1,
                12,
                tasks
                        + " outstanding tasks across "
                        + report.departments().size()
                        + " Departments. See Assessment Progress for the task breakdown; one"
                        + " employee may have several tasks.",
                body,
                38);
        span(
                s,
                22,
                1,
                12,
                "Year-on-year Final Score comparison: "
                        + comparison(report.yearOnYear())
                        + ". "
                        + comparisonReason(report.yearOnYear()),
                body,
                38);
        span(s, 25, 1, 12, "Explore the Report", section, 25);
        link(s, 26, 1, 4, "Department Performance");
        link(s, 26, 5, 8, "Assessment Progress");
        link(s, 26, 9, 12, "Performance Trends");
        link(s, 27, 1, 4, "Score Distribution");
        link(s, 27, 5, 8, "Appraisal Decisions");
        link(s, 27, 9, 12, "Department Trends");
        span(s, 29, 1, 12, "Report Context", section, 25);
        span(
                s,
                30,
                1,
                12,
                "Generated "
                        + generated.format(
                                DateTimeFormatter.ofPattern(
                                        "dd MMM yyyy, HH:mm XXX", Locale.ENGLISH))
                        + " | History: "
                        + report.historyYears()
                        + " years | Distribution: "
                        + metric(report.distribution().metric()),
                note,
                24);
        span(
                s,
                31,
                1,
                12,
                "Averages use each metric's completed results. Missing scores are excluded; zero is"
                    + " a valid score. Department search and chart selections do not filter this"
                    + " export.",
                note,
                36);
        span(
                s,
                33,
                1,
                12,
                "Current completed performance results are shown. Recorded appraisal"
                    + " recommendations and HR decisions remain unchanged.",
                note,
                30);
        context(
                s,
                35,
                "Review Period ID",
                report.period().id(),
                "Period Status",
                friendly(report.period().status().name()));
        context(
                s,
                36,
                "Annual KPI Calculation",
                report.period().consolidationMethod() == null
                        ? "Not Available"
                        : friendly(report.period().consolidationMethod().name()),
                "KPI Allocation",
                report.period().kpiWeight());
        s.getRow(36).getCell(10).setCellStyle(score);
        context(
                s,
                37,
                "Appraisal Privacy Minimum",
                report.appraisalDistribution().minimumGroupSize(),
                "YoY Comparison Code",
                report.yearOnYear().status().name());
        context(
                s,
                38,
                "Previous Review Period ID",
                report.yearOnYear().previousPeriodId() == null
                        ? "Not Available"
                        : report.yearOnYear().previousPeriodId(),
                "Final Score YoY Change",
                report.yearOnYear().percentagePoints() == null
                        ? "Not Comparable"
                        : report.yearOnYear().percentagePoints());
        if (report.yearOnYear().percentagePoints() != null)
            s.getRow(38).getCell(10).setCellStyle(pp);
        context(
                s,
                39,
                "Attitude Allocation",
                report.period().attitudeWeight(),
                "Generated At",
                generated.toString());
        s.getRow(39).getCell(4).setCellStyle(score);
        span(s, 34, 1, 12, "Report metadata (expand the grouped rows below)", note, 24);
        s.groupRow(35, 39);
        s.setRowGroupCollapsed(35, true);
        finish(s, 12, 40);
        s.getPrintSetup().setFitHeight((short) 1);
    }

    private void departments() {
        var s = sheet("Department Performance", "Department Performance", 12);
        span(
                s,
                4,
                1,
                12,
                "Each average uses its own contributor count. Small samples may not reflect the"
                    + " whole Department. Pending results are not plotted as zero.",
                note,
                35);
        int head = 6, r = head;
        String[] headers = {
            "Department",
            "Employees",
            "Employees with Final Scores",
            "KPI Score",
            "KPI Contributors",
            "Attitude Score",
            "Attitude Contributors",
            "Final Score",
            "Final Contributors",
            "Final YoY Change",
            "YoY Comparison",
            "Comparison Detail",
            "Department ID",
            "Comparison Code",
            "Previous Period ID"
        };
        headings(s, head, headers);
        for (var d : report.departments()) {
            var a = d.summary();
            values(
                    s,
                    ++r,
                    d.name(),
                    a.totalEmployees(),
                    a.employeesWithResults(),
                    a.kpi().score(),
                    a.kpi().contributingEmployees(),
                    a.attitude().score(),
                    a.attitude().contributingEmployees(),
                    a.finalScore().score(),
                    a.finalScore().contributingEmployees(),
                    d.yearOnYear().percentagePoints() == null
                            ? "Not Comparable"
                            : d.yearOnYear().percentagePoints(),
                    comparison(d.yearOnYear()),
                    comparisonReason(d.yearOnYear()),
                    d.departmentId() == null ? "Unassigned" : d.departmentId(),
                    d.yearOnYear().status().name(),
                    d.yearOnYear().previousPeriodId() == null
                            ? "Not Available"
                            : d.yearOnYear().previousPeriodId());
            formats(s, r, new int[] {4, 6, 8}, score);
            formats(s, r, new int[] {10}, pp);
        }
        table(s, "DepartmentPerformance", head, r, headers.length);
        s.setColumnWidth(1, 34 * 256);
        s.setColumnWidth(11, 22 * 256);
        s.setColumnWidth(12, 46 * 256);
        s.groupColumn(13, 15);
        s.setColumnGroupCollapsed(13, true);
        availableFormatting(s, head + 1, r, new int[] {4, 6, 8});
        int end = r + 4 + Math.max(17, report.departments().size() * 2 + 5);
        if (report.departments().stream().anyMatch(d -> d.summary().finalScore().score() != null))
            chart(
                    s,
                    "Available Final Scores by Department",
                    r + 3,
                    end,
                    s,
                    head + 1,
                    r,
                    1,
                    new int[] {8},
                    new String[] {"Final Appraisal Score"},
                    ChartTypes.BAR,
                    true,
                    false,
                    true);
        else
            span(
                    s,
                    r + 3,
                    1,
                    12,
                    "No Final Scores are available for a Department comparison yet.",
                    note,
                    32);
        finish(s, 12, end + 1);
    }

    private void progress() {
        var s = sheet("Assessment Progress", "Outstanding Assessment Tasks", 8);
        span(
                s,
                4,
                1,
                8,
                "Counts are outstanding tasks, not unique employees. An employee may have more than"
                    + " one task. Viewing these counts does not assign review authority.",
                note,
                36);
        int head = 6, r = head;
        String[] headers = {
            "Department",
            "Employees",
            "KPI Self-Assessments",
            "Superior KPI Reviews",
            "Attitude Self-Assessments",
            "Superior Attitude Reviews",
            "Returned Individual KPI Plans",
            "HR Reviews",
            "Department ID"
        };
        headings(s, head, headers);
        for (var d : report.departments()) {
            var p = d.assessmentProgress();
            values(
                    s,
                    ++r,
                    d.name(),
                    d.summary().totalEmployees(),
                    p.kpiSelfAssessments(),
                    p.kpiSuperiorReviews(),
                    p.attitudeSelfAssessments(),
                    p.attitudeSuperiorReviews(),
                    p.returnedIndividualPlans(),
                    d.pendingHrReview(),
                    d.departmentId() == null ? "Unassigned" : d.departmentId());
        }
        table(s, "AssessmentProgress", head, r, headers.length);
        s.setColumnWidth(1, 34 * 256);
        s.setColumnWidth(3, 22 * 256);
        s.setColumnWidth(5, 22 * 256);
        s.groupColumn(9, 9);
        s.setColumnGroupCollapsed(9, true);
        if (r > head) {
            var cf = s.getSheetConditionalFormatting();
            var rule = cf.createConditionalFormattingRule(ComparisonOperator.GT, "0");
            var fill = rule.createPatternFormatting();
            fill.setFillBackgroundColor(IndexedColors.LIGHT_YELLOW.index);
            fill.setFillPattern(PatternFormatting.SOLID_FOREGROUND);
            cf.addConditionalFormatting(
                    new CellRangeAddress[] {new CellRangeAddress(head + 1, r, 3, 8)}, rule);
        }
        int end = r + 4 + Math.max(19, report.departments().size() * 2 + 5);
        if (r > head)
            chart(
                    s,
                    "Outstanding Tasks by Department",
                    r + 3,
                    end,
                    s,
                    head + 1,
                    r,
                    1,
                    new int[] {3, 4, 5, 6, 7, 8},
                    new String[] {
                        "KPI Self-Assessment",
                        "Superior KPI Review",
                        "Attitude Self-Assessment",
                        "Superior Attitude Review",
                        "Returned KPI Plan",
                        "HR Review"
                    },
                    ChartTypes.BAR,
                    true,
                    true,
                    false);
        else span(s, r + 3, 1, 9, "No Departments are included in this Review Period.", note, 30);
        finish(s, 9, end + 1);
    }

    private void trends() {
        var s = sheet("Performance Trends", "Organisation Performance Trends", 11);
        span(
                s,
                4,
                1,
                11,
                "Periods follow their actual dates and may cover different lengths of time. Gaps"
                    + " mean results are unavailable; contributor counts show the coverage behind"
                    + " each average.",
                note,
                36);
        int head = 6, r = head;
        String[] headers = {
            "Review Period",
            "Start Date",
            "End Date",
            "Date Span",
            "Employees",
            "KPI Score",
            "KPI Contributors",
            "Attitude Score",
            "Attitude Contributors",
            "Final Score",
            "Final Contributors",
            "Period ID",
            "Chart Label"
        };
        headings(s, head, headers);
        for (var point : report.trend()) {
            var p = point.period();
            var a = point.summary();
            values(
                    s,
                    ++r,
                    p.name(),
                    p.startDate(),
                    p.endDate(),
                    dateSpan(p),
                    a.totalEmployees(),
                    a.kpi().score(),
                    a.kpi().contributingEmployees(),
                    a.attitude().score(),
                    a.attitude().contributingEmployees(),
                    a.finalScore().score(),
                    a.finalScore().contributingEmployees(),
                    p.id(),
                    chartLabel(p));
            formats(s, r, new int[] {6, 8, 10}, score);
        }
        table(s, "OrganisationHistory", head, r, headers.length);
        s.setColumnWidth(1, 46 * 256);
        s.setColumnWidth(4, 24 * 256);
        s.groupColumn(12, 13);
        s.setColumnGroupCollapsed(12, true);
        availableFormatting(s, head + 1, r, new int[] {6, 8, 10});
        if (report.trend().stream()
                .anyMatch(
                        p ->
                                p.summary().kpi().score() != null
                                        || p.summary().attitude().score() != null
                                        || p.summary().finalScore().score() != null))
            chart(
                    s,
                    "Organisation Scores by Review Period",
                    r + 3,
                    r + 28,
                    s,
                    head + 1,
                    r,
                    13,
                    new int[] {6, 8, 10},
                    new String[] {"KPI Performance", "Attitude Evaluation", "Final Appraisal"},
                    ChartTypes.LINE,
                    false,
                    false,
                    true);
        else span(s, r + 3, 1, 12, "No completed historical scores are available yet.", note, 30);
        finish(s, 11, r + 29);
    }

    private void departmentTrends() {
        var s = sheet("Department Trends", "Department Performance History", 11);
        span(
                s,
                4,
                1,
                11,
                "Final Score comparison by Review Period. Read each period's dates and contributor"
                    + " counts in the detailed history below before drawing comparisons.",
                note,
                36);
        var periods = new LinkedHashMap<Long, Period>();
        report.trend().forEach(p -> periods.put(p.period().id(), p.period()));
        var labels = new ArrayList<String>();
        labels.add("Department");
        periods.values().forEach(p -> labels.add(p.name()));
        headings(s, 6, labels.toArray(String[]::new));
        s.getRow(6).setHeightInPoints(72);
        int r = 6;
        for (var d : report.departmentTrends()) {
            var byId = new HashMap<Long, Average>();
            d.periods().forEach(p -> byId.put(p.period().id(), p.summary().finalScore()));
            var values = new ArrayList<Object>();
            values.add(d.name());
            for (var id : periods.keySet())
                values.add(byId.containsKey(id) ? byId.get(id).score() : "Not Available");
            values(s, ++r, values.toArray());
            for (int c = 2; c <= labels.size(); c++) s.getRow(r).getCell(c).setCellStyle(score);
        }
        for (int c = 2; c <= labels.size(); c++) s.setColumnWidth(c, 24 * 256);
        int head = r + 4;
        span(s, head - 1, 1, 11, "Detailed History and Contributor Counts", section, 25);
        String[] headers = {
            "Department",
            "Review Period",
            "Start Date",
            "End Date",
            "Employees",
            "KPI Score",
            "KPI Contributors",
            "Attitude Score",
            "Attitude Contributors",
            "Final Score",
            "Final Contributors",
            "Department ID",
            "Period ID"
        };
        headings(s, head, headers);
        r = head;
        for (var d : report.departmentTrends())
            for (var point : d.periods()) {
                var p = point.period();
                var a = point.summary();
                values(
                        s,
                        ++r,
                        d.name(),
                        p.name(),
                        p.startDate(),
                        p.endDate(),
                        a.totalEmployees(),
                        a.kpi().score(),
                        a.kpi().contributingEmployees(),
                        a.attitude().score(),
                        a.attitude().contributingEmployees(),
                        a.finalScore().score(),
                        a.finalScore().contributingEmployees(),
                        d.departmentId() == null ? "Unassigned" : d.departmentId(),
                        p.id());
                formats(s, r, new int[] {6, 8, 10}, score);
                s.getRow(r).setHeightInPoints(46);
            }
        table(s, "DepartmentHistory", head, r, headers.length);
        s.createFreezePane(2, 7);
        s.setColumnWidth(1, 34 * 256);
        s.setColumnWidth(2, 46 * 256);
        if (labels.size() < 12) {
            s.groupColumn(12, 13);
            s.setColumnGroupCollapsed(12, true);
        }
        availableFormatting(s, head + 1, r, new int[] {6, 8, 10});
        finish(s, Math.max(11, labels.size()), r + 1);
    }

    private void distribution() {
        var s = sheet("Score Distribution", "Employee Score Distribution", 9);
        var d = report.distribution();
        span(
                s,
                4,
                1,
                9,
                metric(d.metric())
                        + " | Employees with results: "
                        + d.employeesWithResults()
                        + " | Awaiting results: "
                        + d.missingResults(),
                note,
                28);
        span(
                s,
                5,
                1,
                9,
                "Scores are grouped before display rounding. For example, 69.5% is in 60%-69% (60%"
                    + " to less than 70%).",
                note,
                28);
        int head = 7, r = head;
        String[] headers = {"Score Range", "Employees", "Share of Available Results"};
        headings(s, head, headers);
        for (var b : d.bins()) {
            values(
                    s,
                    ++r,
                    bandLabel(b),
                    b.employees(),
                    d.employeesWithResults() == 0
                            ? "Not Available"
                            : (double) b.employees() / d.employeesWithResults());
            formats(s, r, new int[] {3}, percentage);
        }
        table(s, "ScoreDistribution", head, r, headers.length);
        s.setColumnWidth(1, 28 * 256);
        s.setColumnWidth(3, 26 * 256);
        if (d.employeesWithResults() > 0 && r > head)
            chart(
                    s,
                    metric(d.metric()) + " Distribution",
                    r + 3,
                    r + 23,
                    s,
                    head + 1,
                    r,
                    1,
                    new int[] {2},
                    new String[] {"Employees"},
                    ChartTypes.BAR,
                    false,
                    false,
                    false);
        else
            span(
                    s,
                    r + 3,
                    1,
                    3,
                    "No completed results are available for this score yet.",
                    note,
                    45);
        int definitions = r + 26;
        headings(
                s,
                definitions,
                "Score Metric",
                "Lower Bound (inclusive)",
                "Upper Bound",
                "Upper Bound Inclusive",
                "Employees with Results",
                "Awaiting Results");
        int last = definitions;
        for (var b : d.bins())
            values(
                    s,
                    ++last,
                    d.metric().name(),
                    b.lowerInclusive(),
                    b.upper(),
                    b.upperInclusive(),
                    d.employeesWithResults(),
                    d.missingResults());
        table(s, "ScoreBandDefinitions", definitions, last, 6);
        s.createFreezePane(2, head + 1);
        s.setRepeatingRows(new CellRangeAddress(head, head, -1, -1));
        finish(s, 12, last + 1);
    }

    private void decisions() {
        var s = sheet("Appraisal Decisions", "Appraisal Recommendation and Decision Summary", 12);
        var superior = report.appraisalDistribution().superiorRecommendations();
        var hr = report.appraisalDistribution().hrFinalDecisions();
        decisionNotice(s, 4, "Superior Recommendations", superior);
        decisionNotice(s, 8, "HR Final Decisions", hr);
        boolean showSuperior = superior.disclosure() == Disclosure.AVAILABLE,
                showHr = hr.disclosure() == Disclosure.AVAILABLE;
        if (showSuperior || showHr) {
            headings(s, 12, "Outcome", "Superior Recommendations", "HR Final Decisions");
            int r = 12;
            for (var decision :
                    com.tbm.careerpathlearning.enums.AnnualAppraisalRecommendation.values())
                values(
                        s,
                        ++r,
                        friendly(decision.name()),
                        decisionCount(superior, decision),
                        decisionCount(hr, decision));
            table(s, "AppraisalOutcomes", 12, r, 3);
            s.setColumnWidth(1, 34 * 256);
            s.setColumnWidth(2, 25 * 256);
            s.setColumnWidth(3, 25 * 256);
            var columns = new ArrayList<Integer>();
            var names = new ArrayList<String>();
            if (showSuperior) {
                columns.add(2);
                names.add("Superior Recommendations");
            }
            if (showHr) {
                columns.add(3);
                names.add("HR Final Decisions");
            }
            chart(
                    s,
                    "Recorded Appraisal Outcomes",
                    r + 3,
                    r + 23,
                    s,
                    13,
                    r,
                    1,
                    columns.stream().mapToInt(Integer::intValue).toArray(),
                    names.toArray(String[]::new),
                    ChartTypes.BAR,
                    true,
                    false,
                    false);
            finish(s, 12, r + 24);
        } else finish(s, 12, 13);
    }

    private Object decisionCount(
            DecisionSeries series,
            com.tbm.careerpathlearning.enums.AnnualAppraisalRecommendation decision) {
        if (series.disclosure() != Disclosure.AVAILABLE)
            return series.disclosure() == Disclosure.NO_DATA ? "No Data" : "Withheld";
        return series.counts().stream()
                .filter(c -> c.decision() == decision)
                .mapToInt(DecisionCount::count)
                .sum();
    }

    private void decisionNotice(XSSFSheet s, int row, String name, DecisionSeries series) {
        span(s, row, 1, 12, name, section, 25);
        String text =
                switch (series.disclosure()) {
                    case AVAILABLE ->
                            series.total() + " recorded outcomes. Counts are shown below.";
                    case NO_DATA -> "No appraisal data available.";
                    case SUPPRESSED ->
                            "Not enough data to show appraisal statistics safely. Counts are"
                                + " withheld when limited data could identify an employee's"
                                + " appraisal outcome.";
                };
        span(s, row + 1, 1, 12, text, note, 40);
    }

    private void chart(
            XSSFSheet target,
            String name,
            int top,
            int bottom,
            XSSFSheet source,
            int first,
            int last,
            int categoryColumn,
            int[] valueColumns,
            String[] names,
            ChartTypes type,
            boolean horizontal,
            boolean stacked,
            boolean scores) {
        var drawing = target.createDrawingPatriarch();
        var anchor =
                drawing.createAnchor(0, 0, 0, 0, 1, top, Math.min(12, visibleEnd(target)), bottom);
        var chart = drawing.createChart(anchor);
        chart.setTitleText(name);
        chart.setTitleOverlay(false);
        chart.displayBlanksAs(DisplayBlanks.GAP);
        chart.setPlotOnlyVisibleCells(false);
        var tx = chart.getCTChartSpace().addNewTxPr();
        tx.addNewBodyPr();
        tx.addNewLstStyle();
        var font = tx.addNewP().addNewPPr().addNewDefRPr();
        font.setSz(1100);
        font.addNewLatin().setTypeface("Arial");
        var category =
                chart.createCategoryAxis(horizontal ? AxisPosition.LEFT : AxisPosition.BOTTOM);
        var value = chart.createValueAxis(horizontal ? AxisPosition.BOTTOM : AxisPosition.LEFT);
        value.setMinimum(0);
        value.setCrossBetween(AxisCrossBetween.BETWEEN);
        value.setTitle(
                scores
                        ? "Score (%)"
                        : stacked
                                ? "Outstanding Tasks"
                                : target.getSheetName().equals("Appraisal Decisions")
                                        ? "Recorded Outcomes"
                                        : "Number of Employees");
        value.setNumberFormat(scores ? "0\"%\"" : "0");
        if (target.getSheetName().equals("Score Distribution")) category.setTitle("Score Range");
        if (scores) {
            value.setMaximum(100);
            value.setMajorUnit(20);
        } else {
            double maximum = 1;
            for (int r = first; r <= last; r++) {
                double total = 0;
                for (int c : valueColumns) {
                    var entry = source.getRow(r).getCell(c);
                    if (entry.getCellType() == CellType.NUMERIC)
                        total =
                                stacked
                                        ? total + entry.getNumericCellValue()
                                        : Math.max(total, entry.getNumericCellValue());
                }
                maximum = Math.max(maximum, total);
            }
            value.setMajorUnit(Math.max(1, Math.ceil(maximum / 6)));
            value.setMaximum(Math.ceil(maximum * 1.15));
        }
        category.crossAxis(value);
        value.crossAxis(category);
        if (horizontal) {
            category.setOrientation(AxisOrientation.MAX_MIN);
            category.setCrosses(AxisCrosses.MAX);
            value.setCrosses(AxisCrosses.AUTO_ZERO);
        }
        var data = chart.createData(type, category, value);
        if (data instanceof XDDFBarChartData bars) {
            bars.setBarDirection(horizontal ? BarDirection.BAR : BarDirection.COL);
            bars.setBarGrouping(stacked ? BarGrouping.STACKED : BarGrouping.CLUSTERED);
            bars.setGapWidth(55);
            // POI's bundled lite schemas omit the overlap union type; write its standard OOXML
            // attribute.
            if (stacked)
                try (var cursor =
                        chart.getCTChart()
                                .getPlotArea()
                                .getBarChartArray(0)
                                .addNewOverlap()
                                .newCursor()) {
                    cursor.setAttributeText(new javax.xml.namespace.QName("val"), "100");
                }
        }
        var categories =
                XDDFDataSourcesFactory.fromStringCellRange(
                        source, new CellRangeAddress(first, last, categoryColumn, categoryColumn));
        for (int i = 0; i < valueColumns.length; i++) {
            var values =
                    XDDFDataSourcesFactory.fromNumericCellRange(
                            source,
                            new CellRangeAddress(first, last, valueColumns[i], valueColumns[i]));
            values.setFormatCode(scores ? SCORE_FORMAT : "0");
            var series = data.addSeries(categories, values);
            series.setTitle(names[i], null);
            var shape = new XDDFShapeProperties();
            var fill =
                    new XDDFSolidFillProperties(XDDFColor.from(hex(COLOURS[i % COLOURS.length])));
            shape.setFillProperties(fill);
            var line = new XDDFLineProperties();
            line.setFillProperties(fill);
            line.setWidth(2.0);
            shape.setLineProperties(line);
            series.setShapeProperties(shape);
            if (series instanceof XDDFLineChartData.Series lines) {
                lines.setSmooth(false);
                lines.setMarkerStyle(MarkerStyle.CIRCLE);
            }
        }
        if (valueColumns.length > 1) chart.getOrAddLegend().setPosition(LegendPosition.BOTTOM);
        else chart.deleteLegend();
        chart.plot(data);
    }

    private int visibleEnd(XSSFSheet s) {
        return Math.max(4, s.getSheetName().equals("Assessment Progress") ? 9 : 12);
    }

    private XSSFSheet sheet(String name, String heading, int lastColumn) {
        var s = book.createSheet(name);
        s.setDisplayGridlines(false);
        s.setPrintGridlines(false);
        s.setDefaultRowHeightInPoints(21);
        s.setColumnWidth(0, 3 * 256);
        for (int c = 1; c <= Math.max(15, lastColumn); c++) s.setColumnWidth(c, 16 * 256);
        span(s, 1, 1, lastColumn, heading, title, 30);
        span(s, 2, 1, lastColumn, report.period().name(), body, 32);
        span(
                s,
                3,
                1,
                lastColumn,
                report.period()
                                .startDate()
                                .format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH))
                        + " to "
                        + report.period()
                                .endDate()
                                .format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)),
                note,
                22);
        if (!name.equals("Executive Summary")) {
            var link = book.getCreationHelper().createHyperlink(HyperlinkType.DOCUMENT);
            link.setAddress("'Executive Summary'!B2");
            s.getRow(1).getCell(1).setHyperlink(link);
        }
        s.setZoom(85);
        s.getPrintSetup().setLandscape(true);
        s.getPrintSetup().setPaperSize(PrintSetup.A3_PAPERSIZE);
        s.getPrintSetup().setFitWidth((short) 1);
        s.getPrintSetup().setFitHeight((short) 0);
        s.setFitToPage(true);
        s.setAutobreaks(true);
        s.setMargin(Sheet.LeftMargin, 0.25);
        s.setMargin(Sheet.RightMargin, 0.25);
        s.setMargin(Sheet.TopMargin, 0.35);
        s.setMargin(Sheet.BottomMargin, 0.35);
        s.getFooter().setLeft("APEX | " + name);
        s.getFooter().setRight("Page &P of &N");
        return s;
    }

    private void finish(XSSFSheet s, int lastColumn, int lastRow) {
        book.setPrintArea(book.getSheetIndex(s), 1, lastColumn, 0, lastRow);
    }

    private XSSFCell cell(XSSFSheet s, int row, int col, Object value, XSSFCellStyle style) {
        var r = s.getRow(row);
        if (r == null) r = s.createRow(row);
        var c = r.getCell(col);
        if (c == null) c = r.createCell(col);
        c.setCellStyle(style);
        if (value == null) c.setCellValue("Pending");
        else if (value instanceof Number n) c.setCellValue(n.doubleValue());
        else if (value instanceof Boolean b) c.setCellValue(b);
        else if (value instanceof LocalDate d) {
            c.setCellValue(d.atStartOfDay());
            c.setCellStyle(date);
        } else c.setCellValue(value.toString());
        return c;
    }

    private void values(XSSFSheet s, int row, Object... values) {
        for (int i = 0; i < values.length; i++)
            cell(s, row, i + 1, values[i], values[i] instanceof Number ? count : body);
        s.getRow(row).setHeightInPoints(40);
    }

    private void headings(XSSFSheet s, int row, String... headings) {
        for (int i = 0; i < headings.length; i++) cell(s, row, i + 1, headings[i], header);
        s.getRow(row).setHeightInPoints(48);
    }

    private void span(
            XSSFSheet s,
            int row,
            int first,
            int last,
            Object text,
            XSSFCellStyle style,
            float height) {
        for (int c = first; c <= last; c++) cell(s, row, c, c == first ? text : "", style);
        if (last > first) s.addMergedRegion(new CellRangeAddress(row, row, first, last));
        s.getRow(row).setHeightInPoints(height);
    }

    private void card(
            XSSFSheet s,
            int row,
            int col,
            String label,
            Object value,
            XSSFCellStyle format,
            String detail) {
        span(s, row, col, col + 3, label, cardLabel, 32);
        span(s, row + 1, col, col + 3, value, format == score ? cardScore : cardValue, 36);
        span(s, row + 2, col, col + 3, detail, cardLabel, 30);
    }

    private void context(XSSFSheet s, int row, String left, Object a, String right, Object b) {
        span(s, row, 1, 3, left, note, 30);
        span(s, row, 4, 6, a, body, 30);
        span(s, row, 7, 9, right, note, 30);
        span(s, row, 10, 12, b, body, 30);
    }

    private void link(XSSFSheet s, int row, int first, int last, String target) {
        span(s, row, first, last, target, body, 26);
        var link = book.getCreationHelper().createHyperlink(HyperlinkType.DOCUMENT);
        link.setAddress("'" + target + "'!B2");
        s.getRow(row).getCell(first).setHyperlink(link);
    }

    private void formats(XSSFSheet s, int row, int[] columns, XSSFCellStyle style) {
        for (int c : columns) s.getRow(row).getCell(c).setCellStyle(style);
    }

    private void table(XSSFSheet s, String name, int head, int last, int width) {
        if (last > head) {
            var t =
                    s.createTable(
                            new AreaReference(
                                    new CellReference(head, 1),
                                    new CellReference(last, width),
                                    SpreadsheetVersion.EXCEL2007));
            t.setName(name);
            t.setDisplayName(name);
            t.setStyleName("TableStyleMedium2");
            t.getCTTable().addNewAutoFilter().setRef(t.getCTTable().getRef());
        }
        s.createFreezePane(2, head + 1);
        s.setRepeatingRows(new CellRangeAddress(head, head, -1, -1));
    }

    private void availableFormatting(XSSFSheet s, int first, int last, int[] columns) {
        if (last < first) return;
        var cf = s.getSheetConditionalFormatting();
        for (int col : columns) {
            String ref = CellReference.convertNumToColString(col) + (first + 1);
            var available = cf.createConditionalFormattingRule("ISNUMBER(" + ref + ")");
            var fill = available.createPatternFormatting();
            fill.setFillBackgroundColor(new XSSFColor(hex("EDF3FB"), new DefaultIndexedColorMap()));
            fill.setFillPattern(PatternFormatting.SOLID_FOREGROUND);
            var pending = cf.createConditionalFormattingRule(ref + "=\"Pending\"");
            pending.createFontFormatting().setFontColorIndex(IndexedColors.GREY_50_PERCENT.index);
            cf.addConditionalFormatting(
                    new CellRangeAddress[] {new CellRangeAddress(first, last, col, col)},
                    available,
                    pending);
        }
    }

    private XSSFCellStyle style(int size, boolean bold, String colour, String background) {
        var s = book.createCellStyle();
        var font = book.createFont();
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) size);
        font.setBold(bold);
        font.setColor(new XSSFColor(hex(colour), new DefaultIndexedColorMap()));
        s.setFont(font);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        if (background != null) {
            s.setFillForegroundColor(new XSSFColor(hex(background), new DefaultIndexedColorMap()));
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        return s;
    }

    private XSSFCellStyle number(String format) {
        var s = book.createCellStyle();
        s.cloneStyleFrom(body);
        s.setAlignment(HorizontalAlignment.RIGHT);
        s.setDataFormat(book.createDataFormat().getFormat(format));
        return s;
    }

    private static byte[] hex(String value) {
        return HexFormat.of().parseHex(value);
    }

    private static String contributors(int count) {
        return "Based on " + count + " employee" + (count == 1 ? "" : "s") + " with results";
    }

    private static String friendly(String value) {
        return Arrays.stream(value.toLowerCase(Locale.ENGLISH).split("_"))
                .map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1))
                .reduce((a, b) -> a + " " + b)
                .orElse("");
    }

    private static String metric(Metric m) {
        return switch (m) {
            case KPI -> "KPI Performance Score";
            case ATTITUDE -> "Attitude Evaluation Score";
            case FINAL -> "Final Appraisal Score";
        };
    }

    private static String comparison(Change c) {
        return c.status() == Comparison.AVAILABLE && c.percentagePoints() != null
                ? "Available ("
                        + c.percentagePoints().setScale(2, java.math.RoundingMode.HALF_UP)
                        + " pp)"
                : "Not Comparable";
    }

    private static String comparisonReason(Change c) {
        return switch (c.status()) {
            case AVAILABLE -> "Compared with the compatible previous-year period.";
            case NO_PREVIOUS_PERIOD -> "No previous-year period is available.";
            case AMBIGUOUS_PREVIOUS_PERIOD -> "More than one previous-year period is available.";
            case INCOMPATIBLE_CONFIGURATION -> "Review schedules or scoring settings differ.";
            case NO_COMPARABLE_RESULTS -> "Both periods need completed Final Scores.";
        };
    }

    private static String dateSpan(Period p) {
        if (p.startDate().getDayOfYear() == 1
                && p.endDate().equals(p.startDate().plusYears(1).minusDays(1)))
            return "Full calendar year";
        if (p.startDate().getDayOfMonth() == 1
                && (p.startDate().getMonthValue() - 1) % 3 == 0
                && p.endDate().equals(p.startDate().plusMonths(3).minusDays(1)))
            return "Calendar quarter";
        return "Other date range";
    }

    private static String chartLabel(Period p) {
        return p.name().replaceAll("(.{20,32})\\s+", "$1\n")
                + "\n"
                + p.startDate()
                + " to "
                + p.endDate();
    }

    private static String bandLabel(Bin b) {
        return b.lowerInclusive() == 0 && !b.upperInclusive()
                ? "Below " + b.upper() + "%"
                : b.lowerInclusive()
                        + "%-"
                        + (b.upperInclusive() ? b.upper() : b.upper() - 1)
                        + "%";
    }
}
