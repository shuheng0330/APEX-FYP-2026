package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.GeneratedSopContent.ContentSection;
import com.tbm.careerpathlearning.dto.GeneratedSopContent.GeneratedModule;
import com.tbm.careerpathlearning.dto.GeneratedSopContent.GeneratedQuestion;
import com.tbm.careerpathlearning.dto.GeneratedSopContent.KeyTerm;
import com.tbm.careerpathlearning.dto.GeneratedSopPlan.PlannedModule;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline fallback for SOP generation, used when the AI is unreachable, out of quota or
 * returns something unusable.
 */
@Service
public class LocalSopGenerator {

    /** Questions every module quiz should have, from the AI or written here (FR-06-13). */
    public static final int QUIZ_SIZE = 5;

    /** Same bound as the AI pipeline. */
    private static final int MAX_MODULES = 25;
    /** Modules shorter than this (in characters), or too short for a full quiz, are folded into a neighbour. */
    private static final int MIN_MODULE_CHARS = 200;
    /** Top-level sections longer than this are split at their sub-sections. */
    private static final int SPLIT_MODULE_CHARS = 2500;
    /** Text without usable headings is cut into parts of about this size. */
    private static final int PART_CHARS = 2000;
    private static final int MAX_OBJECTIVES = 4;
    private static final int MAX_KEY_TERMS = 8;
    /**
     * Alignment cost of leaving a planned module empty. Higher than a strong title match (about
     * 3-15): the AI plans every module from real content, so empties should be a last resort.
     */
    private static final double SKIP_COST = 10;

    private static final String PARAGRAPH = "paragraph";
    private static final String LIST = "list";
    private static final String TABLE = "table";
    private static final String DETAILS_HEADING = "Document Details";

    // --- line patterns (matched against whole, trimmed lines) ----------------

    /** Page headers and footers left by PDF extraction: "1 | P a g e", "Page 2 of 4", "- 3 -". */
    private static final Pattern PAGE_NOISE = Pattern.compile(
            "(?i)\\d+\\s*\\|\\s*p\\s*a\\s*g\\s*e|page\\s+\\d+(\\s+of\\s+\\d+)?|[-–]?\\s*\\d{1,3}\\s*[-–]?");
    /** "4.1 Handling Cash", "1-2. Company-Arranged Delivery" */
    private static final Pattern MULTI_NUMBER = Pattern.compile("(\\d{1,2}(?:[.-]\\d{1,2}){1,3})\\.?\\s+(\\S.*)");
    /** "1. Objective" */
    private static final Pattern SINGLE_NUMBER = Pattern.compile("(\\d{1,2})[.)]\\s+(\\S.*)");
    /** "A. Objective", "IV. Scope" */
    private static final Pattern LETTER = Pattern.compile("([A-Z]|[IVX]{1,4})[.)]\\s+(\\S.*)");
    /** "Section 4: Handling Cash", "Part B - Scope", "Slide 3: Title" */
    private static final Pattern NAMED = Pattern.compile(
            "(?i)(?:section|part|chapter|article|appendix|annex|slide)\\s+(\\d{1,3}|[a-z])\\b\\s*[:.)\\-–—]?\\s*(.*)");
    /** One heading per spreadsheet tab, as written by DocumentParserService. */
    private static final Pattern SHEET = Pattern.compile("Sheet:\\s*(\\S.*)");
    private static final Pattern KNOWN_HEADING = Pattern.compile(
            "(?i)(objectives?|purpose|scope|responsibilities|roles and responsibilities|definitions?|glossary|"
                    + "abbreviations|procedures?|references?|policy|overview|introduction|background|records?|"
                    + "documentation|revision history)\\s*:?");
    /** A list marker and the item text after it; "o" is how PDFs render Word's sub-bullets. */
    private static final Pattern LIST_ITEM = Pattern.compile(
            "(?:[•·▪■□◦●○►▶➢➤✓✔*\\uF0B7\\uF0A7\\uF076\\uF0D8\\uF0FC]|[-–—](?=\\s)|o(?=\\s+[A-Z“\"(])"
                    + "|\\d{1,2}(?:[.-]\\d{1,2}){1,3}[.)]?(?=\\s)|\\d{1,3}[.)]|[a-z][.)]|\\((?:[a-z]|\\d{1,2}|[ivx]{1,4})\\)"
                    + "|(?i:step)\\s*\\d{1,3}\\s*[:.)\\-–]?)\\s*(\\S.*)");
    /** "Cash Drawer Setup: At the beginning of each shift ..." */
    private static final Pattern INLINE_LABEL = Pattern.compile("([A-Z][^:]{1,60}):\\s+(\\S.*)");
    /** Several "Key: value" pairs on one line: "Version: 1   Effective Date: 10 February 2024". */
    private static final Pattern KEY_SPLIT = Pattern.compile("(?:\\t+| {2,})(?=[A-Z][A-Za-z ]{1,25}:)");
    private static final Pattern KEY_VALUE = Pattern.compile("([A-Z][A-Za-z /&()-]{1,30}):\\s*(\\S.*)");

    // --- content patterns ----------------------------------------------------

    /** Wording that makes a sentence a firm rule wherever it appears. */
    private static final Pattern STRICT_RULE = Pattern.compile(
            "(?i)\\b(never|must not|cannot|can't|do not|don't|should not|may not|shall not|prohibited|"
                    + "not permitted|not allowed|forbidden|strictly|under no circumstances|warning|caution|danger|"
                    + "disciplinary|termination|zero tolerance|no refund|at all times|mandatory)\\b");
    /** Wording of an obligation: a rule under a rules-type heading, otherwise often just a step. */
    private static final Pattern DUTY = Pattern.compile(
            "(?i)\\b(must|shall|always|only|required|maximum|minimum|immediately|avoid|ensure)\\b");
    private static final Pattern RULE_HEADING = Pattern.compile(
            "(?i)rule|safety|polic|etiquette|security|conduct|compliance|integrity|prohibit|warning|precaution|hazard");
    /** Planned module titles that stand for the SOP's opening sections. */
    private static final Pattern INTRO_TITLE = Pattern.compile(
            "(?i)\\b(introduction|intro|overview|about|background|getting started|purpose)\\b");
    private static final String INTRO_WORDS =
            "objective purpose scope applicable responsibilities definitions introduction overview background references roles";
    private static final Pattern INTRO = Pattern.compile(
            "(?i)objective|purpose|scope|applica|introduction|overview|background|responsibilit|\\broles?\\b|"
                    + "definition|glossary|abbreviation|reference");
    private static final Pattern RESPONSIBILITIES = Pattern.compile("(?i)responsibilit|\\broles?\\b|accountabilit");
    private static final Pattern DEFINITIONS = Pattern.compile("(?i)definition|glossary|terminolog|abbreviation|key terms");
    /** "Float - the cash a drawer starts with" or "Float: the cash ..." */
    private static final Pattern DEFINITION = Pattern.compile("([A-Z][\\w /()&-]{1,40}?)\\s*(?::|\\s[-–—])\\s+(\\S.{5,})");
    /** "property management system (PMS)" */
    private static final Pattern ACRONYM = Pattern.compile(
            "((?:[A-Za-z][\\w-]*\\s+){0,5}[A-Za-z][\\w-]*)\\s+\\(([A-Z]{2,6})\\)");
    private static final Pattern CURRENCY = Pattern.compile(
            "(?i)\\b(?:RM|USD|SGD|MYR)\\s?(\\d[\\d,]*(?:\\.\\d+)?)|\\$\\s?(\\d[\\d,]*(?:\\.\\d+)?)");
    private static final Pattern MEASURE = Pattern.compile(
            "(?i)\\b(\\d+(?:\\.\\d+)?)(\\s?(?:%|percent|hours?|hrs?|minutes?|mins?|seconds?|secs?|days?|weeks?|"
                    + "months?|years?|km/h|km|kg|cm|mm|m|°c|degrees|times))(?![a-z])");
    private static final Pattern TIME_UNIT = Pattern.compile(
            "(?i)\\s?(hour|hr|minute|min|second|sec|day|week|month|year)s?");
    /** Section references in an AI plan: "Section 4.1", "4.2", "Section D". */
    private static final Pattern SECTION_REF = Pattern.compile(
            "(?i)\\b(\\d{1,2}(?:\\.\\d{1,2})+)\\b|\\bsection\\s+(\\d{1,2}|[a-z])\\b");

    private static final Set<String> ABBREVIATIONS = Set.of(
            "no", "mr", "mrs", "ms", "dr", "st", "eg", "ie", "etc", "approx", "fig", "ref", "vs", "inc", "ltd", "co");
    private static final Set<String> SMALL_WORDS = Set.of(
            "a", "an", "the", "and", "or", "of", "to", "for", "in", "on", "at", "by", "with", "via", "per", "as",
            "from", "vs");
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "with", "that", "this", "from", "are", "all", "any", "each", "into", "its", "their",
            "they", "them", "will", "shall", "should", "must", "may", "can", "not", "has", "have", "had", "been",
            "was", "were", "which", "who", "when", "where", "what", "how", "also", "such", "than", "then", "there",
            "these", "those", "our", "your", "you", "per", "via", "sop", "module", "section", "cover", "covers",
            "including", "include", "includes", "etc", "every", "other", "under", "over", "before", "after",
            "during", "within", "without", "only", "more", "most", "some", "both", "upon", "about", "detail",
            "details", "full", "fully", "specific");

    /** Ways to turn a rule into a false statement, tried in order after changing a figure. */
    private static final List<Reversal> REVERSALS = List.of(
            new Reversal("must not", "must"), new Reversal("must", "must not"),
            new Reversal("should not", "should"), new Reversal("should", "should not"),
            new Reversal("never", "always"), new Reversal("always", "never"),
            new Reversal("cannot", "can"), new Reversal("will not", "will"), new Reversal("will", "will not"),
            new Reversal("is required", "is not required"), new Reversal("are required", "are not required"),
            new Reversal("do not", ""),
            new Reversal("at the beginning of", "at the end of"), new Reversal("at the end of", "at the beginning of"),
            new Reversal("before", "after"), new Reversal("after", "before"),
            new Reversal("immediately", "at the end of the week"));

    // --- public API ----------------------------------------------------------

    /** Builds every module from the SOP text alone, for when the AI could not plan the document. */
    public List<GeneratedModule> generate(String sopTitle, String sopText) {
        Parsed doc = parseWithFallback(sopText);
        List<Built> built = new ArrayList<>();
        for (Unit unit : group(doc, sopTitle == null || sopTitle.isBlank() ? "SOP" : sopTitle)) {
            built.add(build(unit.title(), unit.leaves));
        }
        addPlacementQuestions(built);
        List<GeneratedModule> modules = new ArrayList<>();
        built.forEach(b -> modules.add(b.module()));
        return modules;
    }

    /**
     * Builds offline modules that follow the AI's plan (same titles and order), for when the AI
     * fails part-way through. Entry i is null when no SOP text matched planned module i.
     */
    public List<GeneratedModule> generateForPlan(String sopText, List<PlannedModule> plan) {
        Parsed doc = parseWithFallback(sopText);
        List<Leaf> leaves = leaves(doc, false);
        if (leaves.size() < plan.size()) {
            leaves = leaves(doc, true);
        }
        List<List<Leaf>> groups = align(leaves, plan);
        List<Built> built = new ArrayList<>();
        for (int k = 0; k < plan.size(); k++) {
            List<Leaf> group = groups.get(k);
            String title = plan.get(k).getTitle();
            if (title == null || title.isBlank()) {
                title = group.isEmpty() || group.get(0).section().heading == null
                        ? "Module " + (k + 1) : group.get(0).section().heading;
            }
            built.add(group.isEmpty() ? null : build(title, group));
        }
        addPlacementQuestions(built.stream().filter(Objects::nonNull).toList());
        List<GeneratedModule> modules = new ArrayList<>();
        built.forEach(b -> modules.add(b == null ? null : b.module()));
        return modules;
    }

    /**
     * Tops a quiz up to {@code target} questions with ones written from the module's own content,
     * for when the AI returned fewer. The given questions stay first, and sentences they already
     * ask about are not reused.
     */
    public List<GeneratedQuestion> topUpQuiz(List<ContentSection> sections, List<GeneratedQuestion> quiz,
                                             String title, int target) {
        List<GeneratedQuestion> result = new ArrayList<>(quiz == null ? List.of() : quiz);
        if (result.size() >= target || sections == null) {
            return result;
        }
        String heading = title == null || title.isBlank() ? "this module" : title;
        List<Fact> facts = new ArrayList<>();
        for (ContentSection s : sections) {
            String under = s.getHeading() == null || s.getHeading().isBlank() ? heading : s.getHeading();
            if (s.getBody() != null) {
                addFacts(facts, s.getBody(), under, true, false);
            }
            if (s.getItems() != null) {
                s.getItems().forEach(item -> addFacts(facts, item, under, false, false));
            }
        }
        Set<String> used = new HashSet<>();
        for (Fact f : facts) {
            if (result.stream().anyMatch(q -> asks(q, f.sentence()))) {
                used.add(f.sentence());
            }
        }
        result.addAll(quiz(facts, heading, used, target - result.size()));
        return result;
    }

    /** True when most of a sentence's words already appear in a question, its answer or explanation. */
    private static boolean asks(GeneratedQuestion q, String sentence) {
        Set<String> sentenceWords = words(sentence);
        if (sentenceWords.isEmpty()) {
            return false;
        }
        Set<String> questionWords = words(Objects.toString(q.getQuestion(), "") + " " + Objects.toString(q.getAnswer(), "")
                + " " + Objects.toString(q.getExplanation(), ""));
        long shared = sentenceWords.stream().filter(questionWords::contains).count();
        return shared * 10 >= sentenceWords.size() * 6L;
    }

    // --- parsing -------------------------------------------------------------

    private Parsed parseWithFallback(String sopText) {
        Parsed doc = parse(sopText == null ? "" : sopText, false);
        // No numbered or named headings: treat short title-case lines as the headings instead.
        return doc.sections.isEmpty() ? parse(sopText == null ? "" : sopText, true) : doc;
    }

    /**
     * Reads the extracted text line by line into sections (one per heading) holding paragraphs,
     * lists and tables. Handles what PDF extraction leaves behind: lines wrapped mid-sentence,
     * page footers, sub-bullets rendered as "o", and labels such as "1. Cash Drawer Setup:".
     */
    private Parsed parse(String sopText, boolean promoteLabels) {
        List<String> lines = new ArrayList<>();
        for (String raw : sopText.split("\n")) {
            String line = raw.replace('\u00A0', ' ').replace('\u2011', '-').replace("\u200B", "").strip();
            for (String part : KEY_SPLIT.split(line)) {
                lines.add(part.replaceAll(" {2,}", " ").strip());
            }
        }
        boolean wrapped = isWrapped(lines);

        Parsed doc = new Parsed();
        Section current = doc.body;
        Block block = null;       // the block a following line may continue
        String label = null;      // a label waiting for the block it introduces
        boolean paused = false;   // a blank line since the last text (often a page break)
        boolean inQuote = false;  // inside a quoted message template
        String topScheme = null;  // numbering scheme of the first heading: "digit" or "letter"

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isEmpty()) {
                paused = true;
                continue;
            }
            if (PAGE_NOISE.matcher(line).matches()) {
                continue;
            }
            boolean quoted = inQuote;
            inQuote = quoteOpenAfter(inQuote, line);

            if (!quoted) {
                Heading h = heading(line, lines, i);
                if (h == null && promoteLabels && isPlainLabel(line, lines, i)) {
                    h = new Heading(null, stripColon(line), 1, null);
                }
                if (h != null) {
                    if (topScheme == null) {
                        topScheme = h.scheme();
                    }
                    // e.g. "1." headings inside a document whose top level is "A.", "B." ...
                    int level = h.level() + (h.scheme() != null && !h.scheme().equals(topScheme) ? 1 : 0);
                    flushLabel(current, label);
                    label = null;
                    current = new Section(h.number(), h.text(), level);
                    doc.sections.add(current);
                    block = null;
                    paused = false;
                    continue;
                }
            }
            if (current == doc.body) {
                doc.preamble.add(line);
            }
            Matcher item = LIST_ITEM.matcher(line);
            boolean marked = item.matches();
            boolean continuation = !marked && block != null && label == null && !paused
                    && !TABLE.equals(block.type) && continues(block.tail(), line, wrapped);
            if (!quoted) {
                if (isTableRow(line)) {
                    if (block == null || !TABLE.equals(block.type) || label != null) {
                        block = add(current, new Block(TABLE, label));
                        label = null;
                    }
                    block.rows.add(cells(line));
                    paused = false;
                    continue;
                }
                String lbl = continuation ? null : label(line, lines, i);
                if (lbl != null) {
                    flushLabel(current, label);
                    label = lbl;
                    block = null;
                    paused = false;
                    continue;
                }
                if (marked) {
                    if (block == null || !LIST.equals(block.type) || label != null) {
                        block = add(current, new Block(LIST, label));
                        label = null;
                    }
                    block.items.add(new StringBuilder(item.group(1)));
                    paused = false;
                    continue;
                }
                Matcher inline = INLINE_LABEL.matcher(line);
                if (!continuation && label == null && inline.matches() && isInlineLabel(inline.group(1))) {
                    block = add(current, new Block(PARAGRAPH, inline.group(1).strip()));
                    block.text.append(inline.group(2));
                    paused = false;
                    continue;
                }
            }
            // Plain text: continue a wrapped line or quoted template, or start a paragraph.
            boolean joins;
            if (block == null || label != null || TABLE.equals(block.type)) {
                joins = false;
            } else if (quoted) {
                joins = block.template; // a template stays whole, blank lines included
            } else {
                joins = paused ? startsLowercase(line) : continuation;
            }
            if (joins && quoted && paused) {
                block.text.append("\n\n").append(line);
            } else if (joins) {
                join(block.tail(), line, quoted);
            } else {
                block = add(current, new Block(PARAGRAPH, label));
                block.template = quoted || line.startsWith("“") || line.startsWith("\"");
                label = null;
                block.text.append(line);
            }
            paused = false;
        }
        flushLabel(current, label);
        return doc;
    }

    /** A section heading, or null. Numbered lines in a run ("1. Greet", "2. Ask") are a list instead. */
    private static Heading heading(String line, List<String> lines, int i) {
        Matcher m = MULTI_NUMBER.matcher(line);
        if (m.matches() && isHeadingText(m.group(2))) {
            return new Heading(m.group(1), m.group(2), m.group(1).split("[.-]").length, "digit");
        }
        m = SINGLE_NUMBER.matcher(line);
        if (m.matches() && isHeadingText(m.group(2)) && !inNumberedRun(lines, i, Integer.parseInt(m.group(1)))) {
            return new Heading(m.group(1), m.group(2), 1, "digit");
        }
        m = LETTER.matcher(line);
        if (m.matches() && isHeadingText(m.group(2))) {
            return new Heading(m.group(1), m.group(2), 1, "letter");
        }
        m = NAMED.matcher(line);
        if (m.matches() && (m.group(2).isEmpty() || isHeadingText(m.group(2)))) {
            return new Heading(m.group(1), m.group(2).isEmpty() ? line : m.group(2), 1, null);
        }
        m = SHEET.matcher(line);
        if (m.matches()) {
            return new Heading(null, m.group(1).strip(), 1, null);
        }
        if (KNOWN_HEADING.matcher(line).matches() || isAllCaps(line)) {
            return new Heading(null, stripColon(line), 1, null);
        }
        return null;
    }

    /** A sub-heading inside a section: "1. Cash Drawer Setup:", or a short title before a list. */
    private static String label(String line, List<String> lines, int i) {
        Matcher item = LIST_ITEM.matcher(line);
        boolean marked = item.matches();
        String text = marked ? item.group(1) : line;
        if (text.endsWith(":") && isLabelText(stripColon(text))) {
            return stripColon(text);
        }
        if (marked && isShortTitle(text) && isListItem(nextContent(lines, i))) {
            return text;
        }
        if (!marked && isPlainLabel(line, lines, i)) {
            return line;
        }
        return null;
    }

    private static boolean isPlainLabel(String line, List<String> lines, int i) {
        return isShortTitle(line) && !INLINE_LABEL.matcher(line).matches() && nextContent(lines, i) != null;
    }

    /** "1. Greet the customer" followed by "2. Ask for the receipt": list items, not headings. */
    private static boolean inNumberedRun(List<String> lines, int i, int n) {
        return isNumberedTitle(nextContent(lines, i), n + 1) || isNumberedTitle(previousContent(lines, i), n - 1);
    }

    private static boolean isNumberedTitle(String line, int n) {
        if (line == null) {
            return false;
        }
        Matcher m = SINGLE_NUMBER.matcher(line);
        return m.matches() && Integer.parseInt(m.group(1)) == n && isHeadingText(m.group(2));
    }

    // --- grouping into modules -----------------------------------------------

    /**
     * Groups sections into modules the way the AI tends to: opening sections (objective, scope,
     * responsibilities) together, then one module per top-level section, with long sections
     * split at their sub-sections and very short ones folded into a neighbour.
     */
    private static List<Unit> group(Parsed doc, String sopTitle) {
        List<Unit> units = new ArrayList<>();
        if (doc.sections.isEmpty()) {
            units.addAll(parts(sopTitle, leaves(doc, false)));
        } else {
            int top = doc.sections.stream().mapToInt(s -> s.level).min().orElse(1);
            List<List<Section>> groups = new ArrayList<>();
            for (Section s : doc.sections) {
                if (s.level <= top || groups.isEmpty()) {
                    groups.add(new ArrayList<>());
                }
                groups.get(groups.size() - 1).add(s);
            }
            int intro = 0;
            while (intro < groups.size() && isIntro(groups.get(intro))) {
                intro++;
            }
            if (intro >= 2) {
                Unit unit = new Unit(null);
                for (List<Section> g : groups.subList(0, intro)) {
                    unit.names.add(g.get(0).heading);
                    g.forEach(s -> addSection(unit, s));
                }
                units.add(unit);
            } else {
                intro = 0;
            }
            for (List<Section> g : groups.subList(intro, groups.size())) {
                units.addAll(split(g));
            }
            units.removeIf(u -> u.leaves.isEmpty());
            foldSmall(units);
        }
        while (units.size() > MAX_MODULES) {
            mergeSmallestPair(units);
        }
        Leaf details = details(doc);
        if (details != null) {
            if (units.isEmpty()) {
                units.add(new Unit(DETAILS_HEADING));
            }
            units.get(0).leaves.add(0, details);
        }
        return units;
    }

    private static boolean isIntro(List<Section> group) {
        return INTRO.matcher(group.get(0).heading).find()
                && group.stream().mapToInt(Section::chars).sum() < SPLIT_MODULE_CHARS;
    }

    /** One module for a top-level section, or one per sub-section when it is long. */
    private static List<Unit> split(List<Section> group) {
        Section head = group.get(0);
        int chars = group.stream().mapToInt(Section::chars).sum();
        int childLevel = group.stream().skip(1).mapToInt(s -> s.level).min().orElse(Integer.MAX_VALUE);
        long children = group.stream().skip(1).filter(s -> s.level == childLevel).count();
        if (chars > SPLIT_MODULE_CHARS && children >= 2) {
            List<Unit> units = new ArrayList<>();
            Unit unit = new Unit(null);
            for (Section s : group) {
                if (s != head && s.level == childLevel) {
                    if (!unit.names.isEmpty()) {
                        units.add(unit);
                        unit = new Unit(null);
                    }
                    unit.names.add(s.heading);
                }
                addSection(unit, s);
            }
            units.add(unit);
            return units;
        }
        if (chars > 2 * SPLIT_MODULE_CHARS) {
            List<Leaf> leaves = new ArrayList<>();
            group.forEach(s -> leaves.addAll(sectionLeaves(s, false)));
            return parts(head.heading, leaves);
        }
        Unit unit = new Unit(head.heading);
        group.forEach(s -> addSection(unit, s));
        return List.of(unit);
    }

    /** Cuts leaves into modules of about PART_CHARS, titled "Name (Part n)". */
    private static List<Unit> parts(String name, List<Leaf> leaves) {
        List<Unit> units = new ArrayList<>();
        Unit unit = null;
        for (Leaf leaf : leaves) {
            if (unit == null || (!unit.leaves.isEmpty() && unit.chars() + leaf.chars() > PART_CHARS)) {
                unit = new Unit(null);
                units.add(unit);
            }
            unit.leaves.add(leaf);
        }
        for (int i = 0; i < units.size(); i++) {
            units.get(i).names.add(units.size() == 1 ? name : name + " (Part " + (i + 1) + ")");
        }
        return units;
    }

    /** Folds each module too short to support a full quiz into a neighbour (FR-06-13). */
    private static void foldSmall(List<Unit> units) {
        int i = 0;
        while (i < units.size() && units.size() > 1) {
            Unit unit = units.get(i);
            if (unit.chars() >= MIN_MODULE_CHARS && supportsQuiz(unit)) {
                i++;
                continue;
            }
            if (i > 0) {
                units.get(i - 1).append(unit);
            } else {
                units.get(1).prepend(unit);
            }
            units.remove(i);
        }
    }

    /** Whether the module's own text gives a quiz of QUIZ_SIZE questions. */
    private static boolean supportsQuiz(Unit unit) {
        return build(unit.title(), unit.leaves).module().getQuiz().size() >= QUIZ_SIZE;
    }

    private static void mergeSmallestPair(List<Unit> units) {
        int best = 0;
        for (int i = 1; i < units.size() - 1; i++) {
            if (units.get(i).chars() + units.get(i + 1).chars() < units.get(best).chars() + units.get(best + 1).chars()) {
                best = i;
            }
        }
        units.get(best).append(units.remove(best + 1));
    }

    private static void addSection(Unit unit, Section section) {
        if (!section.blocks.isEmpty()) {
            unit.leaves.add(new Leaf(section, section.blocks));
        }
    }

    /** The document as leaves in reading order: one per labelled run of blocks, or one per block. */
    private static List<Leaf> leaves(Parsed doc, boolean perBlock) {
        List<Leaf> leaves = new ArrayList<>();
        Leaf details = details(doc);
        if (details != null) {
            leaves.add(details);
        }
        for (Section section : doc.sections.isEmpty() ? List.of(doc.body) : doc.sections) {
            leaves.addAll(sectionLeaves(section, perBlock));
        }
        return leaves;
    }

    private static List<Leaf> sectionLeaves(Section section, boolean perBlock) {
        List<Leaf> leaves = new ArrayList<>();
        List<Block> run = new ArrayList<>();
        for (Block b : section.blocks) {
            if (!run.isEmpty() && (perBlock || b.label != null)) {
                leaves.add(new Leaf(section, run));
                run = new ArrayList<>();
            }
            run.add(b);
        }
        if (!run.isEmpty()) {
            leaves.add(new Leaf(section, run));
        }
        return leaves;
    }

    /** What precedes the first heading: a details table ("Version: 1" ...) and any real intro text. */
    private static Leaf details(Parsed doc) {
        if (doc.sections.isEmpty()) {
            return null;
        }
        Section section = new Section(null, DETAILS_HEADING, 1);
        Block table = new Block(TABLE, null);
        table.rows.add(List.of("Item", "Detail"));
        for (String line : doc.preamble) {
            Matcher kv = KEY_VALUE.matcher(line);
            if (kv.matches() && wordCount(kv.group(1)) <= 4) {
                table.rows.add(List.of(kv.group(1).strip(), kv.group(2).strip()));
            }
        }
        if (table.rows.size() >= 3) {
            section.blocks.add(table);
        }
        for (Block b : doc.body.blocks) {
            if (b.label == null && (LIST.equals(b.type) || wordCount(b.text) >= 12)) {
                section.blocks.add(b);
            }
        }
        return section.blocks.isEmpty() ? null : new Leaf(section, section.blocks);
    }

    // --- following an AI plan ------------------------------------------------

    /**
     * Assigns leaves to the planned modules in reading order, so that each module gets the
     * leaves sharing the most words with its title and "covers" text. Dynamic programming over
     * all ordered assignments; a planned module that matches nothing may stay empty, at a cost.
     */
    private static List<List<Leaf>> align(List<Leaf> leaves, List<PlannedModule> plan) {
        int n = leaves.size();
        int m = plan.size();
        List<List<Leaf>> groups = new ArrayList<>();
        for (int k = 0; k < m; k++) {
            groups.add(new ArrayList<>());
        }
        if (n == 0 || m == 0) {
            return groups;
        }

        List<Set<String>> planWords = new ArrayList<>();
        List<Set<String>> planRefs = new ArrayList<>();
        Map<String, Integer> spread = new HashMap<>(); // how many planned modules use each word
        for (PlannedModule p : plan) {
            String title = Objects.toString(p.getTitle(), "");
            String text = title + " " + Objects.toString(p.getCovers(), "");
            Set<String> w = words(INTRO_TITLE.matcher(title).find() ? text + " " + INTRO_WORDS : text);
            w.forEach(word -> spread.merge(word, 1, Integer::sum));
            planWords.add(w);
            planRefs.add(sectionRefs(text));
        }
        double[][] score = new double[n][m];
        for (int j = 0; j < n; j++) {
            Leaf leaf = leaves.get(j);
            Set<String> head = words(Objects.toString(leaf.section().heading, "") + " " + labels(leaf));
            Set<String> body = words(leafText(leaf));
            body.removeAll(head);
            String number = leaf.section().number == null ? null : leaf.section().number.toLowerCase(Locale.ROOT);
            for (int k = 0; k < m; k++) {
                // Headings count most; words shared by many planned modules (the SOP's subject) count least.
                double s = 0;
                for (String w : head) {
                    s += planWords.get(k).contains(w) ? 3.0 / spread.get(w) : 0;
                }
                for (String w : body) {
                    s += planWords.get(k).contains(w) ? 0.5 / spread.get(w) : 0;
                }
                if (number != null && planRefs.get(k).contains(number)) {
                    s += 6;
                }
                // Slight preference for proportional positions when the wording says little.
                double pj = n == 1 ? 0.5 : (double) j / (n - 1);
                double pk = m == 1 ? 0.5 : (double) k / (m - 1);
                score[j][k] = s + (1 - Math.abs(pj - pk));
            }
        }

        // best[j][k]: leaves 0..j placed in order with leaf j in module k; every module passed
        // over without a leaf costs SKIP_COST.
        double[][] best = new double[n][m];
        int[][] previous = new int[n][m];
        for (int k = 0; k < m; k++) {
            best[0][k] = score[0][k] - SKIP_COST * k;
        }
        for (int j = 1; j < n; j++) {
            for (int k = 0; k < m; k++) {
                double top = Double.NEGATIVE_INFINITY;
                for (int p = 0; p <= k; p++) {
                    double value = best[j - 1][p] - (p < k ? SKIP_COST * (k - p - 1) : 0);
                    if (value > top) {
                        top = value;
                        previous[j][k] = p;
                    }
                }
                best[j][k] = top + score[j][k];
            }
        }
        int k = 0;
        double top = Double.NEGATIVE_INFINITY;
        for (int c = 0; c < m; c++) {
            double value = best[n - 1][c] - SKIP_COST * (m - 1 - c);
            if (value > top) {
                top = value;
                k = c;
            }
        }
        for (int j = n - 1; j >= 0; j--) {
            groups.get(k).add(0, leaves.get(j));
            if (j > 0) {
                k = previous[j][k];
            }
        }
        return groups;
    }

    private static Set<String> words(String text) {
        Set<String> words = new HashSet<>();
        for (String w : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (w.length() >= 3 && !STOP_WORDS.contains(w)) {
                words.add(stem(w));
            }
        }
        return words;
    }

    /** Crude stemming so "arranged"/"arrange" and "payments"/"payment" match. */
    private static String stem(String w) {
        if (w.length() > 4 && w.endsWith("s") && !w.endsWith("ss")) {
            w = w.substring(0, w.length() - 1);
        }
        if (w.length() > 6 && w.endsWith("ing")) {
            w = w.substring(0, w.length() - 3);
        } else if (w.length() > 5 && w.endsWith("ed")) {
            w = w.substring(0, w.length() - 2);
        }
        return w.length() > 4 && w.endsWith("e") ? w.substring(0, w.length() - 1) : w;
    }

    private static Set<String> sectionRefs(String text) {
        Set<String> refs = new HashSet<>();
        Matcher m = SECTION_REF.matcher(text);
        while (m.find()) {
            refs.add((m.group(1) != null ? m.group(1) : m.group(2)).toLowerCase(Locale.ROOT));
        }
        return refs;
    }

    private static String labels(Leaf leaf) {
        StringBuilder sb = new StringBuilder();
        leaf.blocks().stream().map(b -> b.label).filter(Objects::nonNull).forEach(l -> sb.append(l).append(' '));
        return sb.toString();
    }

    private static String leafText(Leaf leaf) {
        StringBuilder sb = new StringBuilder();
        for (Block b : leaf.blocks()) {
            sb.append(b.text).append(' ');
            b.items.forEach(item -> sb.append(item).append(' '));
            b.rows.forEach(row -> sb.append(String.join(" ", row)).append(' '));
        }
        return sb.toString();
    }

    // --- building a module ---------------------------------------------------

    private static Built build(String title, List<Leaf> leaves) {
        List<ContentSection> sections = new ArrayList<>();
        Map<String, String> targets = new LinkedHashMap<>(); // heading -> block type, for objectives
        List<Fact> facts = new ArrayList<>();
        List<KeyTerm> terms = new ArrayList<>();
        List<List<String>> roleTable = new ArrayList<>(); // role -> responsibility rows, for quiz questions
        boolean multiSection = leaves.stream().map(Leaf::section).distinct().count() > 1;

        Section previous = null;
        for (Leaf leaf : leaves) {
            Section section = leaf.section();
            String sectionHeading = section != previous && section.heading != null
                    && !section.heading.equalsIgnoreCase(title) ? section.heading : null;
            previous = section;
            boolean definitions = section.heading != null && DEFINITIONS.matcher(section.heading).find();
            boolean roles = section.heading != null && RESPONSIBILITIES.matcher(section.heading).find();
            List<List<String>> roleRows = new ArrayList<>();
            for (Block block : leaf.blocks()) {
                List<String[]> pairs = definitions || roles ? pairs(block) : null;
                if (pairs != null && definitions) {
                    pairs.forEach(p -> terms.add(term(p[0], p[1])));
                    continue;
                }
                if (pairs != null) {
                    pairs.forEach(p -> roleRows.add(List.of(p[0], p[1])));
                    continue;
                }
                String heading = block.label;
                if (sectionHeading != null) {
                    heading = heading == null ? sectionHeading
                            : multiSection ? sectionHeading + " - " + heading : heading;
                    sectionHeading = null;
                }
                String target = block.label != null ? block.label : Objects.requireNonNullElse(section.heading, title);
                render(block, heading, target, sections, targets, facts);
            }
            if (!roleRows.isEmpty()) {
                ContentSection table = section(TABLE, sectionHeading);
                table.setHeaders(List.of("Role", "Responsibility"));
                table.setRows(roleRows);
                roleTable.addAll(roleRows);
                sections.add(table);
                target(targets, Objects.requireNonNullElse(section.heading, title), "roles");
            }
        }

        Set<String> seenTerms = new HashSet<>();
        terms.forEach(t -> seenTerms.add(t.getTerm().toLowerCase(Locale.ROOT)));
        facts.forEach(f -> addAcronyms(f.sentence(), terms, seenTerms));

        Set<String> used = new HashSet<>();
        GeneratedModule module = new GeneratedModule();
        module.setTitle(title);
        module.setSummary(summary(leaves, facts, multiSection));
        module.setLearningObjectives(objectives(targets, title));
        module.setTools(new ArrayList<>());
        module.setSections(sections);
        module.setKeyTerms(new ArrayList<>(terms.subList(0, Math.min(terms.size(), MAX_KEY_TERMS))));
        List<GeneratedQuestion> quiz = quiz(facts, title, used, QUIZ_SIZE);
        quiz.addAll(roleQuestions(roleTable, QUIZ_SIZE - quiz.size()));
        module.setQuiz(quiz);
        return new Built(module, facts, used);
    }

    private static void render(Block block, String heading, String target, List<ContentSection> out,
                               Map<String, String> targets, List<Fact> facts) {
        if (TABLE.equals(block.type)) {
            int width = block.rows.stream().mapToInt(List::size).max().orElse(0);
            if (block.rows.size() < 2) {
                out.add(paragraph(heading, String.join(" | ", block.rows.isEmpty() ? List.of() : block.rows.get(0))));
                return;
            }
            ContentSection table = section(TABLE, heading);
            table.setHeaders(pad(block.rows.get(0), width));
            table.setRows(block.rows.subList(1, block.rows.size()).stream().map(r -> pad(r, width)).toList());
            out.add(table);
            target(targets, target, TABLE);
            return;
        }
        if (LIST.equals(block.type)) {
            List<String> items = block.items.stream().map(i -> i.toString().strip()).filter(i -> !i.isEmpty()).toList();
            items.forEach(item -> addFacts(facts, item, target, false, false));
            if (items.size() == 1) {
                out.add(paragraph(heading, items.get(0)));
                target(targets, target, PARAGRAPH);
                return;
            }
            String type = isRules(items, target) ? "warnings" : "steps";
            out.add(list(type, heading, items));
            target(targets, target, type);
            return;
        }
        String text = block.text.toString().strip();
        if (block.template) {
            out.add(paragraph(heading, text)); // a message template: keep it exactly as written
            addFacts(facts, text, target, false, true);
            target(targets, target, PARAGRAPH);
            return;
        }
        List<String> sentences = sentences(text);
        addFacts(facts, text, target, true, false);
        if (sentences.size() >= 3 && isRules(sentences, target)) {
            out.add(list("warnings", heading, sentences));
            target(targets, target, "warnings");
            return;
        }
        // "Before each shift, inspect the forklift: check the tyres, test the horn, ..." -> steps
        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);
            List<String> steps = colonList(sentence);
            if (steps != null) {
                String before = String.join(" ", sentences.subList(0, i));
                String intro = sentence.substring(0, sentence.indexOf(": ") + 1);
                String after = String.join(" ", sentences.subList(i + 1, sentences.size()));
                out.add(paragraph(heading, (before + " " + intro).strip()));
                out.add(list("steps", null, steps));
                if (!after.isBlank()) {
                    out.add(paragraph(null, after));
                }
                target(targets, target, "steps");
                return;
            }
        }
        out.add(paragraph(heading, text));
        target(targets, target, PARAGRAPH);
    }

    /** "Label: text" pairs when the whole block is made of them (role lists, definitions), else null. */
    private static List<String[]> pairs(Block block) {
        List<String[]> pairs = new ArrayList<>();
        if (PARAGRAPH.equals(block.type)) {
            if (block.label != null) {
                pairs.add(new String[] {block.label, block.text.toString().strip()});
                return pairs;
            }
            Matcher d = DEFINITION.matcher(block.text);
            if (!d.matches() || !isInlineLabel(d.group(1))) {
                return null;
            }
            pairs.add(new String[] {d.group(1).strip(), d.group(2).strip()});
            return pairs;
        }
        if (LIST.equals(block.type)) {
            for (StringBuilder item : block.items) {
                Matcher d = DEFINITION.matcher(item);
                if (!d.matches() || !isInlineLabel(d.group(1))) {
                    return null;
                }
                pairs.add(new String[] {d.group(1).strip(), d.group(2).strip()});
            }
            return pairs.isEmpty() ? null : pairs;
        }
        return null;
    }

    private static String summary(List<Leaf> leaves, List<Fact> facts, boolean multiSection) {
        List<String> covered = (multiSection
                ? leaves.stream().map(l -> l.section().heading)
                : leaves.stream().flatMap(l -> l.blocks().stream()).map(b -> b.label))
                .filter(h -> h != null && !h.equals(DETAILS_HEADING)).distinct().toList();
        StringBuilder summary = new StringBuilder();
        if (!covered.isEmpty()) {
            summary.append("This module covers ").append(joinNatural(covered)).append('.');
        }
        // Open with the SOP's own first sentence of prose (list fragments read badly as a lead).
        facts.stream().filter(Fact::prose).map(Fact::sentence)
                .filter(s -> wordCount(s) >= 6 && s.length() <= 250)
                .findFirst()
                .ifPresent(lead -> summary.append(summary.isEmpty() ? "" : " ").append(lead));
        return summary.toString();
    }

    private static List<String> objectives(Map<String, String> targets, String title) {
        List<String> objectives = new ArrayList<>();
        for (Map.Entry<String, String> t : targets.entrySet()) {
            String heading = t.getKey();
            objectives.add(switch (t.getValue()) {
                case "steps" -> "Follow the steps for " + heading;
                case "warnings" -> "Apply the rules for " + heading;
                case "roles" -> "Identify who is responsible for each part of this SOP";
                case TABLE -> "Use the reference details in " + heading;
                default -> heading.matches("(?i)objectives?|purpose|scope|overview|background")
                        ? "Explain the " + heading.toLowerCase(Locale.ROOT) + " of this SOP"
                        : "Explain the key points of " + heading;
            });
            if (objectives.size() == MAX_OBJECTIVES) {
                break;
            }
        }
        if (objectives.isEmpty()) {
            objectives.add("Explain the key points of " + title);
        }
        return objectives;
    }

    /** Records what a heading teaches, keeping the most active kind of block seen under it. */
    private static void target(Map<String, String> targets, String heading, String type) {
        if (heading == null || heading.equals(DETAILS_HEADING)) {
            return;
        }
        List<String> rank = List.of(PARAGRAPH, TABLE, "roles", "warnings", "steps");
        String current = targets.get(heading);
        if (current == null || rank.indexOf(type) > rank.indexOf(current)) {
            targets.put(heading, type);
        }
    }

    // --- quiz ----------------------------------------------------------------

    /**
     * Up to {@code target} questions written from the module's own sentences, in rounds of a
     * figure from the SOP as multiple choice, a rule reversed (false) and a rule as written
     * (true). Sentences used go into {@code used}, so nothing is asked twice.
     */
    private static List<GeneratedQuestion> quiz(List<Fact> facts, String title, Set<String> used, int target) {
        List<Fact> usable = facts.stream().filter(f -> isQuizSentence(f.sentence())).toList();
        List<Fact> statements = usable.stream().filter(f -> !f.quoted()).toList();
        List<GeneratedQuestion> quiz = new ArrayList<>();
        boolean falseFirst = Math.floorMod(title.hashCode(), 2) == 1;
        while (quiz.size() < target) {
            int before = quiz.size();
            Fact figure = first(usable, used, f -> quantity(f.sentence()) != null);
            if (figure != null) {
                quiz.add(figureQuestion(figure));
                used.add(figure.sentence());
            }
            // Reversible sentences are the scarcer kind, so the false statement is chosen first.
            GeneratedQuestion statedFalse = falseStatement(statements, used);
            GeneratedQuestion statedTrue = trueStatement(statements, used);
            // Vary which comes first so the answers do not always run True, False.
            for (GeneratedQuestion q : falseFirst ? Arrays.asList(statedFalse, statedTrue) : Arrays.asList(statedTrue, statedFalse)) {
                if (q != null && quiz.size() < target) {
                    quiz.add(q);
                }
            }
            if (quiz.size() == before) {
                break; // the module has no more usable sentences
            }
            falseFirst = !falseFirst;
        }
        return quiz;
    }

    /** "Who is responsible for ...?" from a roles table, with the other roles as the wrong options. */
    private static List<GeneratedQuestion> roleQuestions(List<List<String>> rows, int count) {
        List<GeneratedQuestion> out = new ArrayList<>();
        List<String> roles = rows.stream().map(r -> r.get(0)).distinct().toList();
        if (roles.size() < 2) {
            return out;
        }
        for (List<String> row : rows) {
            if (out.size() >= count) {
                break;
            }
            String role = row.get(0);
            String duty = row.get(1).strip().replaceAll("[.;]+$", "");
            String task = duty.replaceFirst("(?i)^responsible for\\s+", "");
            String text = task.equals(duty)
                    ? "According to the SOP, whose responsibility is this: \"" + duty + "\"?"
                    : "According to the SOP, who is responsible for " + task + "?";
            List<String> options = new ArrayList<>(roles.stream().filter(r -> !r.equals(role)).limit(3).toList());
            options.add(Math.floorMod(duty.hashCode(), options.size() + 1), role);
            out.add(question("MULTIPLE_CHOICE", text, options, role,
                    "The SOP's responsibilities list gives this to " + role + ".", "MEDIUM"));
        }
        return out;
    }

    private static GeneratedQuestion falseStatement(List<Fact> statements, Set<String> used) {
        for (Fact f : statements) {
            String reversed = used.contains(f.sentence()) ? null : reverse(f.sentence());
            if (reversed != null) {
                used.add(f.sentence());
                return trueFalse(reversed, false, "False. The SOP states: \"" + f.sentence() + "\"");
            }
        }
        return null;
    }

    private static GeneratedQuestion trueStatement(List<Fact> statements, Set<String> used) {
        Fact rule = first(statements, used, f -> isRuleLike(f.sentence()));
        if (rule == null) {
            rule = first(statements, used, f -> true);
        }
        if (rule == null) {
            return null;
        }
        used.add(rule.sentence());
        return trueFalse(rule.sentence(), true, "True. The SOP states this under \"" + rule.heading() + "\".");
    }

    /**
     * Tops up modules still short of {@link #QUIZ_SIZE} questions with one "Which of these is
     * part of <module>?", using sentences from the other modules as the wrong options.
     */
    private static void addPlacementQuestions(List<Built> built) {
        for (int i = 0; i < built.size(); i++) {
            Built b = built.get(i);
            if (b.module().getQuiz().size() >= QUIZ_SIZE) {
                continue;
            }
            Set<String> own = new HashSet<>();
            b.facts().forEach(f -> own.add(f.sentence()));
            Fact answer = first(b.facts(), b.used(), LocalSopGenerator::isOption);
            if (answer == null) {
                continue;
            }
            List<String> options = new ArrayList<>();
            for (int d = 1; d < built.size() && options.size() < 3; d++) {
                Fact wrong = first(built.get((i + d) % built.size()).facts(), own,
                        f -> isOption(f) && !options.contains(f.sentence()));
                if (wrong != null) {
                    options.add(wrong.sentence());
                }
            }
            if (options.size() < 2) {
                continue;
            }
            options.add(Math.floorMod(answer.sentence().hashCode(), options.size() + 1), answer.sentence());
            b.module().getQuiz().add(question("MULTIPLE_CHOICE",
                    "Which of these is part of \"" + b.module().getTitle() + "\"?", options, answer.sentence(),
                    "It comes from the SOP under \"" + answer.heading() + "\".", "HARD"));
            b.used().add(answer.sentence());
        }
    }

    private static boolean isOption(Fact fact) {
        return !fact.quoted() && isQuizSentence(fact.sentence()) && wordCount(fact.sentence()) <= 25;
    }

    private static GeneratedQuestion figureQuestion(Fact fact) {
        String s = fact.sentence();
        Quantity q = quantity(s);
        String answer = s.substring(q.start(), q.end());
        List<String> options = new ArrayList<>();
        for (double d : distractors(q)) {
            options.add(q.with(d));
        }
        options.add(Math.floorMod(s.hashCode(), options.size() + 1), answer);
        return question("MULTIPLE_CHOICE",
                "Complete this statement from the SOP: \"" + s.substring(0, q.start()) + "____" + s.substring(q.end()) + "\"",
                options, answer, "The SOP states: \"" + s + "\"", "MEDIUM");
    }

    /** A rule as written is a recall question; spotting a reversed one takes more care. */
    private static GeneratedQuestion trueFalse(String statement, boolean truth, String explanation) {
        return question("TRUE_FALSE", "True or false: " + statement, List.of("True", "False"),
                truth ? "True" : "False", explanation, truth ? "EASY" : "MEDIUM");
    }

    private static GeneratedQuestion question(String type, String text, List<String> options, String answer,
                                              String explanation, String difficulty) {
        GeneratedQuestion q = new GeneratedQuestion();
        q.setType(type);
        q.setQuestion(text);
        q.setOptions(new ArrayList<>(options));
        q.setAnswer(answer);
        q.setExplanation(explanation);
        q.setDifficulty(difficulty);
        return q;
    }

    /** The sentence with a figure or an obligation reversed, or null when neither can be changed. */
    private static String reverse(String s) {
        Quantity q = quantity(s);
        if (q != null) {
            List<Double> wrong = distractors(q);
            if (!wrong.isEmpty()) {
                return s.substring(0, q.start()) + q.with(wrong.get(0)) + s.substring(q.end());
            }
        }
        for (Reversal r : REVERSALS) {
            Matcher m = r.pattern().matcher(s);
            if (m.find()) {
                String replacement = !r.to().isEmpty() && Character.isUpperCase(m.group().charAt(0))
                        ? capitalize(r.to()) : r.to();
                String out = (s.substring(0, m.start()) + replacement + s.substring(m.end())).replaceAll("\\s{2,}", " ").strip();
                return m.start() == 0 ? capitalize(out) : out;
            }
        }
        return null;
    }

    /** The first figure in a sentence ("12 hours", "RM 500"), or null. */
    private static Quantity quantity(String s) {
        Quantity found = null;
        Matcher m = CURRENCY.matcher(s);
        if (m.find()) {
            int g = m.group(1) != null ? 1 : 2;
            found = new Quantity(m.start(), m.end(), s.substring(m.start(), m.start(g)), number(m.group(g)), "");
        }
        m = MEASURE.matcher(s);
        if (m.find() && (found == null || m.start() < found.start())) {
            found = new Quantity(m.start(), m.end(), "", number(m.group(1)), m.group(2));
        }
        return found;
    }

    /** Plausible wrong values for a figure: halves, doubles and near misses of the same kind. */
    private static List<Double> distractors(Quantity q) {
        double v = q.value();
        boolean whole = v == Math.rint(v);
        boolean percent = q.suffix().contains("%") || q.suffix().toLowerCase(Locale.ROOT).contains("percent");
        List<Double> out = new ArrayList<>();
        for (double c : new double[] {v / 2, v * 2, v * 3, v + 1, v + 5, v + 10, v - 1}) {
            if (c > 0 && c != v && (!whole || c == Math.rint(c)) && (!percent || c <= 100) && !out.contains(c)) {
                out.add(c);
            }
            if (out.size() == 3) {
                break;
            }
        }
        return out;
    }

    private static boolean isQuizSentence(String s) {
        int words = wordCount(s);
        return words >= 6 && words <= 40 && !s.endsWith(":") && !s.contains("____") && !s.contains("[")
                && "“\"(".indexOf(s.charAt(0)) < 0;
    }

    private static Fact first(List<Fact> facts, Set<String> used, Predicate<Fact> test) {
        return facts.stream().filter(f -> !used.contains(f.sentence()) && test.test(f)).findFirst().orElse(null);
    }

    // --- small helpers -------------------------------------------------------

    private static void addFacts(List<Fact> facts, String text, String heading, boolean prose, boolean quoted) {
        sentences(text).forEach(s -> facts.add(new Fact(s, heading, prose, quoted)));
    }

    /** Glossary entries for abbreviations spelled out in the text: "point-of-sale (POS)". */
    private static void addAcronyms(String text, List<KeyTerm> terms, Set<String> seen) {
        Matcher m = ACRONYM.matcher(text);
        while (m.find()) {
            String acronym = m.group(2);
            if (seen.contains(acronym.toLowerCase(Locale.ROOT))) {
                continue;
            }
            String[] words = m.group(1).split("\\s+");
            for (int start = words.length - 1; start >= 0; start--) {
                String phrase = String.join(" ", Arrays.copyOfRange(words, start, words.length));
                String initials = initials(phrase);
                if (initials.equalsIgnoreCase(acronym)) {
                    terms.add(term(acronym, capitalize(phrase)));
                    seen.add(acronym.toLowerCase(Locale.ROOT));
                    break;
                }
                if (initials.length() > acronym.length()) {
                    break;
                }
            }
        }
    }

    private static String initials(String phrase) {
        StringBuilder sb = new StringBuilder();
        for (String w : phrase.split("[\\s-]+")) {
            if (!w.isEmpty() && Character.isLetter(w.charAt(0))) {
                sb.append(w.charAt(0));
            }
        }
        return sb.toString();
    }

    /** "intro: a, b, c and d" with at least three short items, as a list of steps; else null. */
    private static List<String> colonList(String sentence) {
        int colon = sentence.indexOf(": ");
        if (colon < 8) {
            return null;
        }
        String[] parts = sentence.substring(colon + 2).replaceAll("[.;]$", "").split(",\\s+|;\\s+");
        if (parts.length < 3) {
            return null;
        }
        List<String> items = new ArrayList<>();
        for (String part : parts) {
            String item = part.strip().replaceFirst("^(?:and|or)\\s+", "");
            int words = wordCount(item);
            if (words < 2 || words > 15) {
                return null;
            }
            items.add(capitalize(item));
        }
        return items;
    }

    private static List<String> sentences(String text) {
        List<String> out = new ArrayList<>();
        for (String part : text.split("\n")) {
            int start = 0;
            for (int i = 0; i < part.length() - 1; i++) {
                char c = part.charAt(i);
                if ((c == '.' || c == '!' || c == '?') && Character.isWhitespace(part.charAt(i + 1))
                        && !isAbbreviation(part, i)) {
                    int next = i + 1;
                    while (next < part.length() && Character.isWhitespace(part.charAt(next))) {
                        next++;
                    }
                    if (next < part.length()
                            && (Character.isUpperCase(part.charAt(next)) || "“\"(".indexOf(part.charAt(next)) >= 0)) {
                        String sentence = part.substring(start, i + 1).strip();
                        if (!sentence.isEmpty()) {
                            out.add(sentence);
                        }
                        start = next;
                    }
                }
            }
            String tail = part.substring(start).strip();
            if (!tail.isEmpty()) {
                out.add(tail);
            }
        }
        return out;
    }

    /** "No." in "Refer to No. G", "e.g.", or an initial: a full stop that does not end a sentence. */
    private static boolean isAbbreviation(String text, int dot) {
        int start = dot;
        while (start > 0 && (Character.isLetter(text.charAt(start - 1)) || text.charAt(start - 1) == '.')) {
            start--;
        }
        String word = text.substring(start, dot).replace(".", "");
        return ABBREVIATIONS.contains(word.toLowerCase(Locale.ROOT))
                || (word.length() == 1 && Character.isUpperCase(word.charAt(0)));
    }

    /** Mostly rules: firm wording, or obligations under a heading such as "Safe Operating Rules". */
    private static boolean isRules(List<String> items, String heading) {
        boolean rulesHeading = heading != null && RULE_HEADING.matcher(heading).find();
        long rules = items.stream()
                .filter(i -> STRICT_RULE.matcher(i).find() || (rulesHeading && DUTY.matcher(i).find()))
                .count();
        return rules * 2 > items.size();
    }

    private static boolean isRuleLike(String sentence) {
        return STRICT_RULE.matcher(sentence).find() || DUTY.matcher(sentence).find();
    }

    /** True when most lines start in lower case, i.e. PDF text wrapped mid-sentence. */
    private static boolean isWrapped(List<String> lines) {
        int content = 0;
        int lower = 0;
        for (String line : lines) {
            if (!line.isEmpty()) {
                content++;
                if (Character.isLowerCase(line.charAt(0))) {
                    lower++;
                }
            }
        }
        return lower >= 3 && lower * 20 >= content;
    }

    private static boolean continues(CharSequence previous, String line, boolean wrapped) {
        return previous.length() > 0 && (startsLowercase(line) || (wrapped && !isTerminated(previous)));
    }

    private static boolean startsLowercase(String line) {
        char c = line.charAt(0);
        return Character.isLowerCase(c) || "(,;&)".indexOf(c) >= 0;
    }

    private static void join(StringBuilder tail, String line, boolean quoted) {
        int len = tail.length();
        if (len > 1 && tail.charAt(len - 1) == '-' && Character.isLetter(tail.charAt(len - 2))
                && Character.isLowerCase(line.charAt(0))) {
            tail.append(line); // "point-of-" + "sale"
        } else {
            tail.append(quoted && isTerminated(tail) ? "\n" : " ").append(line);
        }
    }

    private static boolean quoteOpenAfter(boolean open, String line) {
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '“') {
                open = true;
            } else if (c == '”') {
                open = false;
            } else if (c == '"') {
                open = !open;
            }
        }
        return open;
    }

    private static void flushLabel(Section section, String label) {
        if (label != null) {
            add(section, new Block(PARAGRAPH, null)).text.append(label);
        }
    }

    private static Block add(Section section, Block block) {
        section.blocks.add(block);
        return block;
    }

    private static boolean isTableRow(String line) {
        return line.indexOf('\t') >= 0 && cells(line).stream().filter(c -> !c.isEmpty()).count() >= 2;
    }

    private static List<String> cells(String line) {
        List<String> cells = new ArrayList<>(Arrays.stream(line.split("\t")).map(String::strip).toList());
        while (!cells.isEmpty() && cells.get(cells.size() - 1).isEmpty()) {
            cells.remove(cells.size() - 1);
        }
        return cells;
    }

    private static List<String> pad(List<String> row, int width) {
        List<String> padded = new ArrayList<>(row);
        while (padded.size() < width) {
            padded.add("");
        }
        return padded;
    }

    private static String nextContent(List<String> lines, int i) {
        for (int j = i + 1; j < lines.size(); j++) {
            String line = lines.get(j);
            if (!line.isEmpty() && !PAGE_NOISE.matcher(line).matches()) {
                return line;
            }
        }
        return null;
    }

    private static String previousContent(List<String> lines, int i) {
        for (int j = i - 1; j >= 0; j--) {
            String line = lines.get(j);
            if (!line.isEmpty() && !PAGE_NOISE.matcher(line).matches()) {
                return line;
            }
        }
        return null;
    }

    private static boolean isListItem(String line) {
        return line != null && LIST_ITEM.matcher(line).matches();
    }

    /** Short, title-like text without closing punctuation: "4.1 Handling Cash", "Objective". */
    private static boolean isHeadingText(String text) {
        if (text.length() > 90 || wordCount(text) > 12 || isTerminated(text) || text.endsWith(",")) {
            return false;
        }
        int first = firstLetter(text);
        return first >= 0 && Character.isUpperCase(text.charAt(first));
    }

    private static boolean isShortTitle(String text) {
        return text.length() <= 70 && wordCount(text) <= 8 && !isTerminated(text) && !text.endsWith(",")
                && text.indexOf('\t') < 0 && isTitleCase(text);
    }

    private static boolean isLabelText(String text) {
        return !text.isEmpty() && wordCount(text) <= 8 && isTitleCase(text) && !text.matches(".*[.;!?].*");
    }

    private static boolean isInlineLabel(String label) {
        return wordCount(label) <= 6 && isTitleCase(label) && !label.matches(".*[.;!?“\"].*");
    }

    private static boolean isAllCaps(String line) {
        String text = stripColon(line);
        long letters = text.chars().filter(Character::isLetter).count();
        return letters >= 4 && text.chars().noneMatch(Character::isLowerCase) && wordCount(text) <= 10
                && !text.endsWith(".");
    }

    /** At least 70% of the significant words start with a capital or a digit. */
    private static boolean isTitleCase(String text) {
        int significant = 0;
        int capital = 0;
        for (String w : text.split("\\s+")) {
            String bare = w.replaceAll("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$", "");
            if (bare.isEmpty() || SMALL_WORDS.contains(bare.toLowerCase(Locale.ROOT))) {
                continue;
            }
            significant++;
            if (Character.isUpperCase(bare.charAt(0)) || Character.isDigit(bare.charAt(0))) {
                capital++;
            }
        }
        return significant > 0 && capital * 10 >= significant * 7;
    }

    private static boolean isTerminated(CharSequence text) {
        for (int i = text.length() - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (!Character.isWhitespace(c)) {
                return ".!?:;”\"".indexOf(c) >= 0;
            }
        }
        return false;
    }

    private static int firstLetter(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetter(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static int wordCount(CharSequence text) {
        String s = text.toString().strip();
        return s.isEmpty() ? 0 : s.split("\\s+").length;
    }

    private static String stripColon(String text) {
        return text.endsWith(":") ? text.substring(0, text.length() - 1).strip() : text;
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static String joinNatural(List<String> names) {
        List<String> shown = names.subList(0, Math.min(names.size(), 5));
        String last = names.size() > 5 ? "more" : shown.get(shown.size() - 1);
        List<String> rest = names.size() > 5 ? shown : shown.subList(0, shown.size() - 1);
        return rest.isEmpty() ? last : String.join(", ", rest) + " and " + last;
    }

    private static String joinTitles(List<String> names) {
        List<String> distinct = names.stream().filter(n -> n != null && !n.isBlank()).distinct().toList();
        if (distinct.isEmpty()) {
            return "Overview";
        }
        if (distinct.size() == 1) {
            return distinct.get(0);
        }
        String joined = String.join(", ", distinct.subList(0, distinct.size() - 1)) + " & " + distinct.get(distinct.size() - 1);
        if (joined.length() <= 120) {
            return joined;
        }
        int cut = joined.lastIndexOf(',', 110);
        return cut > 0 ? joined.substring(0, cut) + " & more" : joined.substring(0, 117) + "...";
    }

    private static double number(String text) {
        return Double.parseDouble(text.replace(",", ""));
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value)
                : BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /** Keeps "1 hour" / "2 hours" grammatical when a figure changes. */
    private static String plural(String unit, double value) {
        if (!TIME_UNIT.matcher(unit).matches()) {
            return unit;
        }
        String singular = unit.endsWith("s") || unit.endsWith("S") ? unit.substring(0, unit.length() - 1) : unit;
        return value == 1 ? singular : singular + "s";
    }

    private static ContentSection section(String type, String heading) {
        ContentSection s = new ContentSection();
        s.setType(type);
        s.setHeading(heading);
        return s;
    }

    private static ContentSection paragraph(String heading, String body) {
        ContentSection s = section(PARAGRAPH, heading);
        s.setBody(body);
        return s;
    }

    private static ContentSection list(String type, String heading, List<String> items) {
        ContentSection s = section(type, heading);
        s.setItems(new ArrayList<>(items));
        return s;
    }

    private static KeyTerm term(String term, String definition) {
        KeyTerm t = new KeyTerm();
        t.setTerm(term);
        t.setDefinition(definition);
        return t;
    }

    // --- model ---------------------------------------------------------------

    private static final class Parsed {
        /** Text before the first heading (the whole document when it has no headings). */
        final Section body = new Section(null, null, 0);
        final List<Section> sections = new ArrayList<>();
        /** Raw lines before the first heading, for the document-details table. */
        final List<String> preamble = new ArrayList<>();
    }

    /** One heading and the content under it. */
    private static final class Section {
        final String number;
        final String heading;
        final int level;
        final List<Block> blocks = new ArrayList<>();

        Section(String number, String heading, int level) {
            this.number = number;
            this.heading = heading;
            this.level = level;
        }

        int chars() {
            return blocks.stream().mapToInt(Block::chars).sum();
        }
    }

    /** A paragraph, list or table, optionally under a label such as "Cash Drawer Setup". */
    private static final class Block {
        final String type;
        final String label;
        final StringBuilder text = new StringBuilder();
        final List<StringBuilder> items = new ArrayList<>();
        final List<List<String>> rows = new ArrayList<>();
        /** A quoted message template, kept exactly as written and never quizzed on. */
        boolean template;

        Block(String type, String label) {
            this.type = type;
            this.label = label;
        }

        /** The text a wrapped line continues. */
        StringBuilder tail() {
            return LIST.equals(type) ? items.get(items.size() - 1) : text;
        }

        int chars() {
            int chars = text.length() + (label == null ? 0 : label.length());
            for (StringBuilder item : items) {
                chars += item.length();
            }
            for (List<String> row : rows) {
                for (String cell : row) {
                    chars += cell.length();
                }
            }
            return chars;
        }
    }

    /** A run of blocks from one section: what modules are assembled from. */
    private record Leaf(Section section, List<Block> blocks) {
        int chars() {
            return blocks.stream().mapToInt(Block::chars).sum();
        }
    }

    /** A module being assembled. */
    private static final class Unit {
        final List<String> names = new ArrayList<>();
        final List<Leaf> leaves = new ArrayList<>();

        Unit(String name) {
            if (name != null) {
                names.add(name);
            }
        }

        String title() {
            return joinTitles(names);
        }

        int chars() {
            return leaves.stream().mapToInt(Leaf::chars).sum();
        }

        void append(Unit other) {
            names.addAll(other.names);
            leaves.addAll(other.leaves);
        }

        void prepend(Unit other) {
            names.addAll(0, other.names);
            leaves.addAll(0, other.leaves);
        }
    }

    private record Heading(String number, String text, int level, String scheme) {
    }

    /**
     * A sentence from the SOP and the heading it sits under. {@code prose} marks running text (not a
     * list item); {@code quoted} marks a message template, which is only used for figure questions.
     */
    private record Fact(String sentence, String heading, boolean prose, boolean quoted) {
    }

    /** A built module plus what its quiz was written from, for questions that span modules. */
    private record Built(GeneratedModule module, List<Fact> facts, Set<String> used) {
    }

    /** A figure in a sentence, e.g. "12 hours" or "RM 500". */
    private record Quantity(int start, int end, String prefix, double value, String suffix) {
        String with(double newValue) {
            return prefix + format(newValue) + plural(suffix, newValue);
        }
    }

    private record Reversal(Pattern pattern, String to) {
        Reversal(String from, String to) {
            this(Pattern.compile("(?i)\\b" + from + "\\b"), to);
        }
    }
}
