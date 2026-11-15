package com.tbm.careerpathlearning.service;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Masks personal data in text before it is sent to the external AI service (FR-06-17): each match becomes a label such
 * as [IC_NUMBER]. A label matches no rule, so masking the same text twice changes nothing. The rules are fixed, so they
 * lower the risk without removing it: a name inside a sentence still gets through and salaries aren't looked for, which
 * is why the upload page still warns trainers (FR-06-10).
 */
public final class SensitiveDataFilter {

    /** What a match was, and the label it becomes. */
    public enum Kind {
        IC_NUMBER, PASSPORT_NUMBER, MOBILE_NUMBER, EMAIL, BANK_ACCOUNT, NAME;

        public String label() {
            return "[" + name() + "]";
        }
    }

    /** The masked text, and how many of each kind were replaced. Counts only: the real values are never kept. */
    public record Result(String text, Map<Kind, Integer> counts) {
        public int total() {
            return counts.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    /** Company addresses stay: SOPs list them as contacts for staff. */
    private static final String COMPANY_EMAIL_DOMAIN = "tbm.net";

    /** The place-of-birth codes no IC number uses. */
    private static final Set<Integer> UNUSED_STATE_CODES = Set.of(0, 17, 18, 19, 20, 69, 70, 73, 80, 81, 94, 95, 96, 97);

    /** An IC number's year has only two digits, so 29 February always counts. */
    private static final int[] DAYS_IN_MONTH = {31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

    // --- the parts of a number ---------------------------------------------------

    /** A value starts at a word's start, or right after a JSON escape such as \n in a module's stored content. */
    private static final String VALUE_START = "(?:(?<=\\\\[nrt])|(?<![0-9A-Za-z]))";
    private static final String VALUE_END = "(?![0-9A-Za-z])";
    private static final String DASH = "[-\\u2010-\\u2015\\u2212]";
    private static final String SPACES = "[ \\u00A0]{0,4}";
    /** What can split a number: spaces, one dash, and a line break where a PDF wrapped it. */
    private static final String SPLIT = SPACES + "(?:\\R" + SPACES + ")?" + DASH + "?" + SPACES + "(?:\\R" + SPACES + ")?";
    /** Digits that run on after a dash aren't a whole number yet: the real one may start later, as in 123456 900101-14-5678. */
    private static final String NOT_CONTINUED = "(?!" + DASH + "\\d)";
    /** One space or dash between digits, on one line. */
    private static final String DIGIT_GAP = "[ \\-\\u2010-\\u2015\\u2212]?";

    // --- the parts of a label ----------------------------------------------------

    private static final String LABEL_START = "(?<![\\p{L}\\p{N}])";
    private static final String NUMBER_WORD = "(?:no\\.?|number|num\\.?|nombor|#)";
    /** Between a label and its value: spaces or a line break, with or without a colon or dash. */
    private static final String LABEL_GAP = "\\s{0,12}[:\\uFF1A=\\-\\u2013\\u2014]?\\s{0,12}";
    /** An identity number after its label: an IC number's shape, or any value on one line with at least six digits. */
    private static final String ID_VALUE = "(\\d{6}" + SPLIT + "\\d{2}" + SPLIT + "\\d{4}(?!\\d)"
            + "|[A-Za-z]{0,2}\\d(?:" + DIGIT_GAP + "\\d){5,13}[A-Za-z]?)" + VALUE_END;
    private static final String ACCOUNT_WORD = "(?:account|acct|a/c|akaun)";

    // --- the rules, in the order they run -------------------------------------------

    /** First, so a labelled account number is never taken for another kind of number. */
    private static final Pattern BANK_LABEL = Pattern.compile(LABEL_START + "(?:"
            + "(?:bank\\s{1,4})?" + ACCOUNT_WORD + "\\s{0,4}" + NUMBER_WORD + "(?!\\p{L})" + LABEL_GAP
            + "|(?:bank\\s{1,4})?" + ACCOUNT_WORD + "\\s{0,12}[:\\uFF1A=]\\s{0,12}"
            + "|bank\\s{1,4}" + ACCOUNT_WORD + "\\s{1,12}"
            + "|a/c\\s{1,12}"
            + "|(?:no\\.?|nombor)\\s{1,4}akaun" + LABEL_GAP
            + ")(?:\\p{L}{1,30}[ \\t]{1,4}){0,2}" // a bank's name before the number stays
            + "(\\d(?:" + DIGIT_GAP + "\\d){7,19})" + VALUE_END, Pattern.CASE_INSENSITIVE);

    /** YYMMDD-PB-####: group 1 is the month, group 2 the day and group 3 the place-of-birth code. */
    private static final Pattern IC_SHAPE = Pattern.compile(VALUE_START + "\\d{2}(\\d{2})(\\d{2})" + SPLIT + "(\\d{2})" + SPLIT
            + "\\d{4}" + VALUE_END + NOT_CONTINUED);

    private static final Pattern IC_LABEL = Pattern.compile(LABEL_START
            + "(?:NRIC|My(?:Kad|Kid)|I/?C|K/?P|Kad\\s{1,4}Pengenalan)(?:\\s{0,2}" + NUMBER_WORD + ")?(?!\\p{L})"
            + LABEL_GAP + ID_VALUE, Pattern.CASE_INSENSITIVE);

    private static final Pattern PASSPORT_LABEL = Pattern.compile(LABEL_START
            + "(?:passport|pasport)(?:\\s{0,2}" + NUMBER_WORD + ")?(?!\\p{L})"
            + LABEL_GAP + ID_VALUE, Pattern.CASE_INSENSITIVE);

    /** A Malaysian passport: A, H or K and eight digits. */
    private static final Pattern PASSPORT_SHAPE = Pattern.compile(VALUE_START + "[AHK]\\d{8}" + VALUE_END);

    /** Group 1 is the domain. Starting only at the start of a run of address characters keeps this linear on long runs. */
    private static final Pattern EMAIL = Pattern.compile("(?<![A-Za-z0-9._%+\\-])[A-Za-z0-9._%+\\-]++@"
            + "([A-Za-z0-9\\-]{1,63}(?:\\.[A-Za-z0-9\\-]{1,63}){1,8})");

    /** 01x with seven or eight more digits, with or without +60. Office, 1300 and 1800 numbers don't match. */
    private static final Pattern MOBILE = Pattern.compile("(?:(?<=\\\\[nrt])|(?<![0-9A-Za-z+.]))\\(?(?:\\+?60\\)?" + SPLIT + "|0)"
            + "1\\d\\)?" + SPLIT + "\\d{3,4}" + SPLIT + "\\d{4}" + VALUE_END + NOT_CONTINUED);

    private static final String NAME_WORD = "[\\p{Lu}\\p{Lo}][\\p{L}\\p{M}'\\u2019./\\-]*";
    private static final String NAME_JOINER = "(?i:bin|binti|binte|bte|bt\\.?|b\\.|a/l|a/p|s/o|d/o|al|el|ibni|ibn|anak|ak"
            + "|van|von|de|da|dos)|[@&/]";
    private static final String NAME_ROLE = "staff|employee|customer|client|trainee|trainer|participant|student|applicant"
            + "|driver|rider|manager|supervisor";
    /** Where a name ends. Anything else after it, such as lower-case words, reads as an instruction and not a name. */
    private static final String NAME_END = "(?=[ \\t]*$|[ \\t]*[,;|(]|\\t| {2}|[ \\t]+[-\\u2013\\u2014][ \\t]"
            + "|[ \\t]+\\p{L}[\\p{L}/ .]{0,24}[:\\uFF1A]|(?<=\\p{L}\\.)[ \\t]|\\\\[nrt]|\"|<)";

    /**
     * Only after one of these labels at the start of a line, or of a paragraph in stored rich content. "Course name:" and
     * "File name:" aren't personal, so an unlisted word before "name" doesn't match.
     */
    private static final Pattern NAME_LABEL = Pattern.compile(
            "(?:^|(?<=\\\\n)|(?<=>)|(?<=\"))[ \\t]{0,12}(?:[-*\\u2022\\u00B7\\u25AA][ \\t]{0,4}|\\d{1,2}[.)][ \\t]{0,4})?"
                    + "(?i:(?:(?:full|first|last|given|family|contact|emergency\\s{1,4}contact|" + NAME_ROLE + ")\\s{1,4})?name"
                    + "|surname"
                    + "|name\\s{1,4}of\\s{1,4}(?:the\\s{1,4})?(?:" + NAME_ROLE + ")"
                    + "|nama(?:\\s{1,4}(?:penuh|pekerja|staf|kakitangan|pelanggan|pemandu|pengurus))?)"
                    + "[ \\t]{0,12}[:\\uFF1A][ \\t]{0,12}"
                    + "(" + NAME_WORD + "(?: (?:" + NAME_WORD + "|" + NAME_JOINER + ")){0,11})" + NAME_END,
            Pattern.MULTILINE);

    private static final Pattern ANY_LABEL = Pattern.compile("\\[(?:"
            + Arrays.stream(Kind.values()).map(Kind::name).collect(Collectors.joining("|")) + ")]");

    private SensitiveDataFilter() {
    }

    /**
     * The text with its personal data replaced by labels. Text with nothing to mask comes back unchanged.
     *
     * @throws IllegalStateException if the text can't be checked, so that callers send nothing
     */
    public static Result mask(String text) {
        if (text == null || text.isEmpty()) {
            return new Result("", Map.of());
        }
        try {
            Map<Kind, Integer> counts = new EnumMap<>(Kind.class);
            String masked = replace(text, BANK_LABEL, 1, Kind.BANK_ACCOUNT, m -> true, counts);
            masked = replace(masked, IC_SHAPE, 0, Kind.IC_NUMBER, SensitiveDataFilter::isIcNumber, counts);
            masked = replace(masked, IC_LABEL, 1, Kind.IC_NUMBER, m -> true, counts);
            masked = replace(masked, PASSPORT_LABEL, 1, Kind.PASSPORT_NUMBER, m -> true, counts);
            masked = replace(masked, PASSPORT_SHAPE, 0, Kind.PASSPORT_NUMBER, m -> true, counts);
            masked = replace(masked, EMAIL, 0, Kind.EMAIL, m -> !COMPANY_EMAIL_DOMAIN.equalsIgnoreCase(m.group(1)), counts);
            masked = replace(masked, MOBILE, 0, Kind.MOBILE_NUMBER, m -> true, counts);
            masked = replace(masked, NAME_LABEL, 1, Kind.NAME, m -> true, counts);
            return new Result(masked, Collections.unmodifiableMap(counts));
        } catch (StackOverflowError e) {
            throw new IllegalStateException("The text could not be checked for personal data, so nothing was sent to the AI.");
        }
    }

    /** Whether the text holds a label, so that whoever reads it should be told what the labels are. */
    public static boolean hasLabels(String text) {
        return text != null && ANY_LABEL.matcher(text).find();
    }

    /** Replaces the pattern's group with the label wherever {@code accept} agrees; the rest of the match stays. */
    private static String replace(String text, Pattern pattern, int group, Kind kind, Predicate<MatchResult> accept,
                                  Map<Kind, Integer> counts) {
        Matcher m = pattern.matcher(text);
        StringBuilder out = null;
        int copied = 0;
        int from = 0;
        while (from <= text.length() && m.find(from)) {
            if (!accept.test(m)) {
                from = m.start() + 1; // the real value may overlap what was refused
                continue;
            }
            if (out == null) {
                out = new StringBuilder(text.length());
            }
            out.append(text, copied, m.start(group)).append(kind.label());
            copied = m.end(group);
            from = m.end();
            counts.merge(kind, 1, Integer::sum);
        }
        return out == null ? text : out.append(text, copied, text.length()).toString();
    }

    private static boolean isIcNumber(MatchResult m) {
        int month = Integer.parseInt(m.group(1));
        int day = Integer.parseInt(m.group(2));
        return month >= 1 && month <= 12 && day >= 1 && day <= DAYS_IN_MONTH[month - 1]
                && !UNUSED_STATE_CODES.contains(Integer.parseInt(m.group(3)));
    }
}
