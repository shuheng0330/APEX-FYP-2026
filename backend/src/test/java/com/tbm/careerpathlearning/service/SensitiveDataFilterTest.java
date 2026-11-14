package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.service.SensitiveDataFilter.Kind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** What the filter masks before text goes to the AI, and what it must leave alone (FR-06-17). All the data is made up. */
class SensitiveDataFilterTest {

    private static String masked(String text) {
        return SensitiveDataFilter.mask(text).text();
    }

    /** Pairs of input and expected output, all checked even when one fails. */
    private static void check(String... inputThenExpected) {
        List<Executable> checks = new ArrayList<>();
        for (int i = 0; i < inputThenExpected.length; i += 2) {
            String input = inputThenExpected[i], expected = inputThenExpected[i + 1];
            checks.add(() -> assertEquals(expected, masked(input), "input: " + input));
        }
        assertAll(checks);
    }

    private static void unchanged(String... texts) {
        List<Executable> checks = new ArrayList<>();
        for (String text : texts) {
            checks.add(() -> {
                SensitiveDataFilter.Result result = SensitiveDataFilter.mask(text);
                assertEquals(text, result.text());
                assertEquals(0, result.total(), text);
            });
        }
        assertAll(checks);
    }

    // --- IC numbers ------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"900101-14-5678", "900101145678", "900101 14 5678", "900101 - 14 - 5678", "900101‐14–5678",
            "900101 14 5678", "900101-14-\n5678", "900101-\n14-5678", "900101\n14\n5678", "900101\r\n14\r\n5678"})
    @DisplayName("An IC number is masked with dashes, spaces or a line break")
    void icNumberInEveryForm(String ic) {
        SensitiveDataFilter.Result result = SensitiveDataFilter.mask("Check " + ic + " today.");
        assertEquals("Check [IC_NUMBER] today.", result.text());
        assertEquals(Map.of(Kind.IC_NUMBER, 1), result.counts());
    }

    @ParameterizedTest(name = "code {0}")
    @ValueSource(strings = {"01", "09", "10", "14", "16", "21", "59", "60", "68", "71", "72", "74", "79", "82", "90", "93", "98", "99"})
    @DisplayName("Every place-of-birth code in use counts")
    void stateCodesInUse(String code) {
        assertEquals("[IC_NUMBER]", masked("900101-" + code + "-5678"));
    }

    @ParameterizedTest(name = "code {0}")
    @ValueSource(strings = {"00", "17", "18", "19", "20", "69", "70", "73", "80", "81", "94", "95", "96", "97"})
    @DisplayName("A place-of-birth code no IC number uses is another kind of number")
    void stateCodesNotInUse(String code) {
        unchanged("Reference 900101-" + code + "-5678 stays.");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"901301", "900001", "900100", "900132", "900230", "900431", "900631", "900931", "901131"})
    @DisplayName("A date that doesn't exist is another kind of number")
    void impossibleDates(String date) {
        unchanged("Reference " + date + "-14-5678 stays.");
    }

    @Test
    @DisplayName("29 February always counts, because the year has only two digits")
    void leapDay() {
        check("900229-14-5678", "[IC_NUMBER]");
    }

    @Test
    @DisplayName("Too many digits, or letters attached, is another kind of number")
    void notQuiteAnIcNumber() {
        unchanged("90010114567", "9001011456789", "1900101145678", "INV900101145678", "900101145678A", "900101-14-56789");
    }

    @Test
    @DisplayName("An IC number is found next to other numbers, and in the escaped text of stored content")
    void icNumberAmongOthers() {
        check("Staff 123456 900101-14-5678", "Staff 123456 [IC_NUMBER]",
                "900101 900101-14-5678", "900101 [IC_NUMBER]",
                "991399 900101 14 5678", "991399 [IC_NUMBER]", // the first twelve digits look like one but aren't, and overlap the real one
                "900101-14-5678, 910202-10-1234", "[IC_NUMBER], [IC_NUMBER]",
                "900101-14-5678 - 910202-10-1234", "[IC_NUMBER] - [IC_NUMBER]",
                "a\\n900101-14-5678\\nb", "a\\n[IC_NUMBER]\\nb",
                "{\"body\":\"Check 900101145678.\"}", "{\"body\":\"Check [IC_NUMBER].\"}",
                "<td>900101-14-5678</td>", "<td>[IC_NUMBER]</td>");
    }

    @Test
    @DisplayName("After an IC label any value counts, even one with no valid date or code")
    void icLabels() {
        check("IC No: 901301-00-1234", "IC No: [IC_NUMBER]",
                "IC: 901301001234", "IC: [IC_NUMBER]",
                "I/C No. 901301001234", "I/C No. [IC_NUMBER]",
                "NRIC: 901301-00-1234", "NRIC: [IC_NUMBER]",
                "MyKad No 901301001234", "MyKad No [IC_NUMBER]",
                "No. K/P: 901301-00-1234", "No. K/P: [IC_NUMBER]",
                "Kad Pengenalan: 901301-00-1234", "Kad Pengenalan: [IC_NUMBER]",
                "IC Number - 901301001234", "IC Number - [IC_NUMBER]",
                "IC No\t901301001234", "IC No\t[IC_NUMBER]",
                "IC No:\n901301001234", "IC No:\n[IC_NUMBER]",
                "ic no: 901301-00-\n1234", "ic no: [IC_NUMBER]",
                "IC No: S1234567A", "IC No: [IC_NUMBER]",
                "ICNo: 901301001234", "ICNo: [IC_NUMBER]");
    }

    @Test
    @DisplayName("The words about an IC stay: only a value after the label is masked")
    void icWordsStay() {
        unchanged("Record the customer's IC number.", "IC number must be checked.", "Check the IC No: before continuing.",
                "Use IC 7805 for the regulator.", "IC chip 74HC595", "Topic: 123456", "Basic: 123456", "Clinic No: 123456",
                "ICT123456", "Music 123456", "IC note 123456");
    }

    // --- passports -------------------------------------------------------------------

    @Test
    @DisplayName("A, H or K and eight digits is a passport number, with or without a label")
    void passportNumbers() {
        check("Use A12345678 here.", "Use [PASSPORT_NUMBER] here.",
                "H12345678", "[PASSPORT_NUMBER]",
                "K12345678", "[PASSPORT_NUMBER]",
                "a\\nA12345678", "a\\n[PASSPORT_NUMBER]");
        unchanged("B12345678", "a12345678", "A1234567", "A123456789", "XA12345678", "A12345678B");
    }

    @Test
    @DisplayName("After a passport label any passport-style value counts")
    void passportLabels() {
        check("Passport No: B1234567", "Passport No: [PASSPORT_NUMBER]",
                "Passport: 12345678", "Passport: [PASSPORT_NUMBER]",
                "Passport Number 987654321", "Passport Number [PASSPORT_NUMBER]",
                "No. Pasport: AB123456", "No. Pasport: [PASSPORT_NUMBER]",
                "passport no.: e1234567", "passport no.: [PASSPORT_NUMBER]",
                "IC or passport no.: B1234567", "IC or passport no.: [PASSPORT_NUMBER]",
                "IC or passport no.: 900101-14-5678", "IC or passport no.: [IC_NUMBER]",
                "Passport/IC No: 12345678", "Passport/IC No: [IC_NUMBER]",
                "Passport No: 901301-00-1234", "Passport No: [PASSPORT_NUMBER]");
        unchanged("Passport is required for foreigners.", "Check the passport number.", "Passports 123456", "Passport 2024");
    }

    // --- mobile numbers --------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"012-345 6789", "0123456789", "012 345 6789", "012-3456789", "011-2345 6789", "01123456789", "+6012-345 6789",
            "+60123456789", "6012-345 6789", "60123456789", "+60 12 345 6789", "(012) 345 6789", "(+60)12-345 6789", "012-345\n6789", "019 - 876 5432"})
    @DisplayName("A Malaysian mobile number is masked, with or without +60")
    void mobileNumbers(String mobile) {
        SensitiveDataFilter.Result result = SensitiveDataFilter.mask("Call " + mobile + " now.");
        assertEquals("Call [MOBILE_NUMBER] now.", result.text());
        assertEquals(Map.of(Kind.MOBILE_NUMBER, 1), result.counts());
    }

    @Test
    @DisplayName("Office and toll-free numbers stay, and so do other numbers that only start like a mobile number")
    void notMobileNumbers() {
        unchanged("Tel: 03-7890 1234", "Fax +603-7890 1235", "1300-88-1234", "1800 22 8888", "082-123456", "0.0123456789", "RM0123456789",
                "10123456789", "012345678901", "012-34567", "012-345-6789-1", "012345-00-1234");
        check("010203-14-5678", "[IC_NUMBER]", // an IC number from 2001 starts like a mobile number
                "Call 012-345 6789 - 013-456 7890", "Call [MOBILE_NUMBER] - [MOBILE_NUMBER]");
    }

    // --- email addresses ---------------------------------------------------------------

    @Test
    @DisplayName("Any address outside @tbm.net is masked, and the full stop after it stays")
    void emailAddresses() {
        check("Write to someone@example.com.", "Write to [EMAIL].",
                "ali.baba+test@example.com.my", "[EMAIL]",
                "<SOMEONE@EXAMPLE.COM>", "<[EMAIL]>",
                "mailto:someone@example.com", "mailto:[EMAIL]",
                "someone@tbm.net.example.com", "[EMAIL]",
                "someone@hr.tbm.net", "[EMAIL]",
                "someone@xtbm.net", "[EMAIL]",
                "0123456789@example.com", "[EMAIL]");
    }

    @Test
    @DisplayName("Company addresses stay, and an @ on its own is no address")
    void emailsThatStay() {
        unchanged("Report it to hr@tbm.net.", "HR@TBM.NET", "first.last@tbm.net", "Meet @ 5pm", "someone@localhost", "@example.com");
    }

    // --- bank accounts -----------------------------------------------------------------

    @Test
    @DisplayName("A number after an account label is masked; a bank's name before it stays")
    void bankAccounts() {
        check("Account No: 1234567890", "Account No: [BANK_ACCOUNT]",
                "Account number 1234-5678-9012", "Account number [BANK_ACCOUNT]",
                "Bank Account: 1234567890", "Bank Account: [BANK_ACCOUNT]",
                "Bank account 1234 5678 90", "Bank account [BANK_ACCOUNT]",
                "Bank Account: Maybank 1234567890", "Bank Account: Maybank [BANK_ACCOUNT]",
                "A/C No: 12345678", "A/C No: [BANK_ACCOUNT]",
                "A/C 12345678", "A/C [BANK_ACCOUNT]",
                "Acct No. 12345678", "Acct No. [BANK_ACCOUNT]",
                "Account: 12345678", "Account: [BANK_ACCOUNT]",
                "No. Akaun: 1234567890", "No. Akaun: [BANK_ACCOUNT]",
                "Nombor akaun 1234567890", "Nombor akaun [BANK_ACCOUNT]",
                "Akaun: 1234567890", "Akaun: [BANK_ACCOUNT]",
                "Account No:\n1234567890", "Account No:\n[BANK_ACCOUNT]",
                "Account No: 900101145678", "Account No: [BANK_ACCOUNT]");
    }

    @Test
    @DisplayName("A number with no account label stays, and so do short numbers and the words themselves")
    void notBankAccounts() {
        unchanged("Reference 1234567890", "Account No: 1234", "Account number must be checked.", "Charge account 40000000 in the ledger.",
                "Bank Negara 12345678", "Account manager 12345678", "account notes 12345678");
    }

    // --- names ---------------------------------------------------------------------------

    @Test
    @DisplayName("A name is masked after a name label at the start of a line")
    void names() {
        check("Name: Ahmad bin Ali", "Name: [NAME]",
                "Full Name: Ahmad bin Ali", "Full Name: [NAME]",
                "Staff name: Nur Aisyah", "Staff name: [NAME]",
                "NAME: AHMAD BIN ALI", "NAME: [NAME]",
                "Nama: Siti binti Hassan", "Nama: [NAME]",
                "Nama Penuh: Siti binti Hassan", "Nama Penuh: [NAME]",
                "Employee Name : Tan Wei Ming", "Employee Name : [NAME]",
                "Customer name: Ravi a/l Muthu", "Customer name: [NAME]",
                "Customer name: Ravi A/L Muthu", "Customer name: [NAME]",
                "Name of staff: Lee Chong Wei", "Name of staff: [NAME]",
                "Surname: O'Brien", "Surname: [NAME]",
                "Name: Muhammad Al-Amin", "Name: [NAME]",
                "Name: M. Kumar", "Name: [NAME]",
                "Name: Ali & Abu", "Name: [NAME]",
                "Name: Ali / Abu", "Name: [NAME]",
                "Name: Muhammad Amir Hafiz bin Abdul Rahman bin Ismail", "Name: [NAME]",
                "Name: 陈伟明", "Name: [NAME]",
                "- Name: Ahmad Ali", "- Name: [NAME]",
                "1. Name: Ahmad Ali", "1. Name: [NAME]",
                "   Name: Ahmad Ali", "   Name: [NAME]",
                "Name:\tAhmad Ali", "Name:\t[NAME]",
                "First line\nName: Ahmad Ali\nLast line", "First line\nName: [NAME]\nLast line",
                "First line\r\nName: Ahmad Ali\r\nLast line", "First line\r\nName: [NAME]\r\nLast line");
    }

    @Test
    @DisplayName("Whatever follows the name stays")
    void whatFollowsANameStays() {
        check("Name: Ahmad bin Ali, Manager", "Name: [NAME], Manager",
                "Name: Ahmad bin Ali (Manager)", "Name: [NAME] (Manager)",
                "Name: Ahmad bin Ali  Position: Manager", "Name: [NAME]  Position: Manager",
                "Name: Ahmad bin Ali Position: Manager", "Name: [NAME] Position: Manager",
                "Name: Ahmad bin Ali - Manager", "Name: [NAME] - Manager",
                "Name: Ahmad bin Ali\tDepartment", "Name: [NAME]\tDepartment",
                "Name: Ahmad Ali. Please sign below.", "Name: [NAME] Please sign below.",
                "Name: Ahmad bin Ali\tIC No: 900101-14-5678", "Name: [NAME]\tIC No: [IC_NUMBER]");
    }

    @Test
    @DisplayName("A name in stored rich content is masked too")
    void namesInStoredContent() {
        check("intro\\nName: Ahmad Ali\\nmore", "intro\\nName: [NAME]\\nmore",
                "<p>Name: Ahmad Ali</p>", "<p>Name: [NAME]</p>",
                "{\"body\":\"Name: Ahmad Ali\"}", "{\"body\":\"Name: [NAME]\"}");
    }

    @Test
    @DisplayName("Other labels, instructions and blanks stay")
    void notNames() {
        unchanged("Course name: Cash Handling", "File name: SOP Final", "Company name: TBM Berhad", "Module Name: Opening", "Username: Ahmad",
                "The Name: Ahmad Ali", "Name: ________", "Name:", "Name: ", "Name Ahmad Ali", "Name: Enter the customer's full name",
                "Name: full name as per IC", "Name: the customer's name", "Their name is Ahmad Ali");
    }

    // --- the filter as a whole -------------------------------------------------------------

    private static final String SOP = """
            Standard Operating Procedure: Cash Handling
            Course name: Handling Cash at the Front Desk

            Name: Ahmad bin Ali
            IC No: 900101-14-5678
            Passport No: A12345678
            Mobile: 012-345 6789
            Email: ahmad.ali@example.com
            Bank Account: Maybank 1234567890

            1. Count the float at the start of every shift.
            2. Report shortages to finance@tbm.net or call 03-7890 1234.
            3. Record the customer's IC number on the form.
            """;

    private static final String MASKED_SOP = """
            Standard Operating Procedure: Cash Handling
            Course name: Handling Cash at the Front Desk

            Name: [NAME]
            IC No: [IC_NUMBER]
            Passport No: [PASSPORT_NUMBER]
            Mobile: [MOBILE_NUMBER]
            Email: [EMAIL]
            Bank Account: Maybank [BANK_ACCOUNT]

            1. Count the float at the start of every shift.
            2. Report shortages to finance@tbm.net or call 03-7890 1234.
            3. Record the customer's IC number on the form.
            """;

    @Test
    @DisplayName("A whole document: each item is replaced and counted, and every other word stays")
    void wholeDocument() {
        SensitiveDataFilter.Result result = SensitiveDataFilter.mask(SOP);

        assertEquals(MASKED_SOP, result.text());
        assertEquals(Map.of(Kind.NAME, 1, Kind.IC_NUMBER, 1, Kind.PASSPORT_NUMBER, 1, Kind.MOBILE_NUMBER, 1, Kind.EMAIL, 1, Kind.BANK_ACCOUNT, 1),
                result.counts());
        assertEquals(6, result.total());
    }

    @Test
    @DisplayName("Masking text twice changes nothing, so the two call points can't interfere")
    void maskingTwiceChangesNothing() {
        SensitiveDataFilter.Result again = SensitiveDataFilter.mask(masked(SOP));

        assertEquals(MASKED_SOP, again.text());
        assertEquals(0, again.total());
        unchanged("[IC_NUMBER]", "[PASSPORT_NUMBER]", "[MOBILE_NUMBER]", "[EMAIL]", "[BANK_ACCOUNT]", "[NAME]", "IC No: [IC_NUMBER]", "Name: [NAME]",
                "[BANK_ACCOUNT]: x", "[PASSPORT_NUMBER]: x", "Account No: [BANK_ACCOUNT]");
    }

    @Test
    @DisplayName("Text with nothing to mask comes back as the same text, and ordinary numbers stay")
    void cleanTextIsUntouched() {
        String clean = "1. Count the float.\n2. Lock the drawer before every break.";
        assertSame(clean, SensitiveDataFilter.mask(clean).text());
        assertTrue(SensitiveDataFilter.mask(clean).counts().isEmpty());
        unchanged("SOP-2024-001", "Version 3.2.1", "Invoice 1234567890", "Room 1204", "RM 1,250.00", "12/01/2026", "2026-11-09", "Postcode 50450",
                "Serial 123456789012", "Serial A1234567", "10:30 to 12:45", "ISO 9001:2015", "50% of 1000", "Section 4.1.2");
    }

    @Test
    @DisplayName("No text is no problem")
    void noText() {
        assertEquals("", masked(null));
        assertEquals("", masked(""));
        assertEquals(0, SensitiveDataFilter.mask(null).total());
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    @DisplayName("Each label is recognised")
    void labelsAreRecognised(Kind kind) {
        assertEquals("[" + kind.name() + "]", kind.label());
        assertTrue(SensitiveDataFilter.hasLabels("before " + kind.label() + " after"));
    }

    @Test
    @DisplayName("Other text isn't taken for a label")
    void otherTextIsNoLabel() {
        assertFalse(SensitiveDataFilter.hasLabels(null));
        assertFalse(SensitiveDataFilter.hasLabels(""));
        assertFalse(SensitiveDataFilter.hasLabels("IC_NUMBER and NAME"));
        assertFalse(SensitiveDataFilter.hasLabels("[OTHER]"));
    }

    static Stream<String> hugeInputs() {
        return Stream.of("a".repeat(2_000_000), "1".repeat(2_000_000), "a.".repeat(1_000_000), "a@".repeat(1_000_000), "a@b.".repeat(500_000),
                "900101 ".repeat(300_000), "123456 12 3456 ".repeat(150_000), "012 ".repeat(500_000), " ".repeat(2_000_000), "\n".repeat(2_000_000),
                "Name: ".repeat(300_000), "Name: A ".repeat(250_000), "Name: A\n".repeat(250_000), "IC No: ".repeat(300_000), "Account No: ".repeat(200_000),
                "Bank Account: word word ".repeat(100_000), "Passport ".repeat(200_000), ">".repeat(2_000_000), "\"".repeat(2_000_000), "\\n".repeat(1_000_000),
                "A B ".repeat(500_000), "-".repeat(2_000_000), "900101-".repeat(300_000), "Staff 123456 900101-14-5678\n".repeat(50_000));
    }

    @ParameterizedTest(name = "input {index}")
    @MethodSource("hugeInputs")
    @DisplayName("A huge or awkward document is checked quickly, without running out of stack")
    void hugeDocumentsAreQuick(String input) {
        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            SensitiveDataFilter.Result result = SensitiveDataFilter.mask(input);
            assertNotNull(result.text());
        });
    }
}
