package com.tbm.careerpathlearning.config;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SchemaPatchRunnerTest {

    @TempDir
    Path patches;

    private JdbcDataSource dataSource;
    private SchemaPatchRunner runner;

    @BeforeEach
    void setUp() {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:patches-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        runner = new SchemaPatchRunner();
        ReflectionTestUtils.setField(runner, "dataSource", dataSource);
        ReflectionTestUtils.setField(runner, "enabled", true);
        ReflectionTestUtils.setField(runner, "location", patches.toUri() + "*.sql");
    }

    @Test
    @DisplayName("Pending patches are applied once each, in file name order, with every statement in the file")
    void appliesPendingPatchesOnceInOrder() throws Exception {
        write("P002__add_courses.sql", "INSERT INTO course (id, title) VALUES (1, 'Cash Handling');\n"
                + "INSERT INTO course (id, title) VALUES (2, 'Forklift Safety');");
        write("P001__create_course.sql", "CREATE TABLE course (id INT PRIMARY KEY, title VARCHAR(100));");

        runner.applyPatches();
        runner.applyPatches(); // a restart applies nothing again

        assertEquals(2, count("SELECT COUNT(*) FROM course"));
        assertEquals(List.of("P001__create_course.sql", "P002__add_courses.sql"), applied());
    }

    @Test
    @DisplayName("A failing patch is rolled back, is not recorded, and stops the start-up")
    void failingPatchIsRolledBack() throws Exception {
        execute("CREATE TABLE course (id INT PRIMARY KEY)");
        write("P001__broken.sql", "INSERT INTO course (id) VALUES (1);\nINSERT INTO missing_table (id) VALUES (1);");

        IllegalStateException e = assertThrows(IllegalStateException.class, runner::applyPatches);

        assertTrue(e.getMessage().startsWith("Schema patch P001__broken.sql failed and was rolled back"), e.getMessage());
        assertEquals(0, count("SELECT COUNT(*) FROM course"), "the first insert was rolled back");
        assertTrue(applied().isEmpty());
    }

    @Test
    @DisplayName("Editing a patch that was already applied stops the start-up")
    void editedPatchStopsTheStartUp() throws Exception {
        write("P001__create_course.sql", "CREATE TABLE course (id INT PRIMARY KEY);");
        runner.applyPatches();
        write("P001__create_course.sql", "CREATE TABLE course (id INT PRIMARY KEY, title VARCHAR(100));");

        IllegalStateException e = assertThrows(IllegalStateException.class, runner::applyPatches);

        assertTrue(e.getMessage().contains("was changed after it was applied"), e.getMessage());
    }

    @Test
    @DisplayName("Different line endings do not count as an edit")
    void lineEndingsAreNotAnEdit() throws Exception {
        write("P001__create_course.sql", "CREATE TABLE course (\n  id INT PRIMARY KEY\n);\n");
        runner.applyPatches();
        write("P001__create_course.sql", "CREATE TABLE course (\r\n  id INT PRIMARY KEY\r\n);\r\n");

        assertDoesNotThrow(runner::applyPatches);
    }

    @Test
    @DisplayName("When disabled, nothing is applied and no patch table is created")
    void disabled() throws Exception {
        ReflectionTestUtils.setField(runner, "enabled", false);
        write("P001__create_course.sql", "CREATE TABLE course (id INT PRIMARY KEY);");

        runner.applyPatches();

        assertEquals(0, count("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE UPPER(table_name) IN ('COURSE', 'SCHEMA_PATCH')"));
    }

    private void write(String name, String sql) throws Exception {
        Files.writeString(patches.resolve(name), sql, StandardCharsets.UTF_8);
    }

    private void execute(String sql) throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    private int count(String sql) throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private List<String> applied() throws SQLException {
        List<String> names = new ArrayList<>();
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM schema_patch ORDER BY name")) {
            while (rs.next()) {
                names.add(rs.getString(1));
            }
        }
        return names;
    }
}
