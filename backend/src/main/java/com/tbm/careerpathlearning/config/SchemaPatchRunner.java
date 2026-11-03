package com.tbm.careerpathlearning.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/**
 * Applies the training module's SQL patches (db/training-patches/*.sql) once each, in file name order,
 * after Hibernate has updated the schema. ddl-auto=update only adds tables and columns; the patches do
 * what it cannot, such as indexes, changed constraints, data backfills and dropping old columns.
 *
 * <p>Each patch runs in its own transaction and is recorded in schema_patch with a checksum. A failing
 * patch is rolled back and stops the application from starting, and so does editing a patch that was
 * already applied: add a new patch instead. Patches must also work on a new, empty database, so they
 * use IF EXISTS / IF NOT EXISTS.
 */
@Slf4j
@Component
@DependsOn("entityManagerFactory")
public class SchemaPatchRunner {

    @Autowired private DataSource dataSource;

    @Value("${training.schema-patches.enabled:true}")
    private boolean enabled;

    @Value("${training.schema-patches.location:classpath*:db/training-patches/*.sql}")
    private String location;

    @PostConstruct
    public void applyPatches() throws IOException, SQLException {
        if (!enabled) {
            log.info("Training schema patches are disabled");
            return;
        }
        Resource[] patches = new PathMatchingResourcePatternResolver().getResources(location);
        Arrays.sort(patches, Comparator.comparing(Resource::getFilename));

        try (Connection c = dataSource.getConnection()) {
            boolean autoCommit = c.getAutoCommit();
            try {
                Map<String, String> applied = appliedPatches(c);
                int count = 0;
                for (Resource patch : patches) {
                    String name = patch.getFilename();
                    String sql = read(patch);
                    String checksum = checksum(sql);
                    String appliedChecksum = applied.get(name);
                    if (appliedChecksum == null) {
                        apply(c, name, sql, checksum);
                        count++;
                    } else if (!appliedChecksum.equals(checksum)) {
                        throw new IllegalStateException("Schema patch " + name
                                + " was changed after it was applied. Add a new patch instead of editing an applied one.");
                    }
                }
                log.info("Training schema patches: {} applied now, {} in total", count, patches.length);
            } finally {
                c.setAutoCommit(autoCommit);
            }
        }
    }

    private Map<String, String> appliedPatches(Connection c) throws SQLException {
        c.setAutoCommit(true);
        try (Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS schema_patch ("
                    + "name VARCHAR(200) PRIMARY KEY, "
                    + "checksum VARCHAR(64) NOT NULL, "
                    + "applied_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL)");
            Map<String, String> applied = new HashMap<>();
            try (ResultSet rs = st.executeQuery("SELECT name, checksum FROM schema_patch")) {
                while (rs.next()) {
                    applied.put(rs.getString(1), rs.getString(2));
                }
            }
            return applied;
        }
    }

    private void apply(Connection c, String name, String sql, String checksum) throws SQLException {
        c.setAutoCommit(false);
        try (Statement st = c.createStatement();
             PreparedStatement record = c.prepareStatement("INSERT INTO schema_patch (name, checksum) VALUES (?, ?)")) {
            st.execute(sql);
            record.setString(1, name);
            record.setString(2, checksum);
            record.executeUpdate();
            c.commit();
            log.info("Applied schema patch {}", name);
        } catch (SQLException e) {
            c.rollback();
            throw new IllegalStateException("Schema patch " + name + " failed and was rolled back: " + e.getMessage(), e);
        }
    }

    /** Line endings and a byte order mark do not count as changes, so a Windows checkout gives the same checksum. */
    private static String read(Resource patch) throws IOException {
        String sql = new String(patch.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return sql.replace("﻿", "").replace("\r\n", "\n");
    }

    private static String checksum(String sql) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(sql.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
