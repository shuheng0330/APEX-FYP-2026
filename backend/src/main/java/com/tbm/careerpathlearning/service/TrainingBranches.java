package com.tbm.careerpathlearning.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TBM's own branches, listed on the HRDC registration form (FR-13-03), as TBM gave them on 27 Oct 2026.
 */
public final class TrainingBranches {

    /** Branch names by region, in the order the form lists them. */
    public static final Map<String, List<String>> BY_REGION;

    static {
        Map<String, List<String>> regions = new LinkedHashMap<>();
        regions.put("Kuala Lumpur", List.of("Jalan Klang Lama (HQ)", "Bangsar Village", "Taman Connaught Cheras",
                "Great Eastern Mall", "Setapak Central"));
        regions.put("Selangor", List.of("3 Damansara Factory Outlet", "IOI Mall Damansara", "Bandar Puteri Puchong",
                "DPulze Cyberjaya", "Eco Majestic Semenyih"));
        regions.put("Putrajaya", List.of("Putrajaya Presint 15"));
        regions.put("Service and repair", List.of("TBM Service Centre"));
        BY_REGION = Collections.unmodifiableMap(regions);
    }

    private TrainingBranches() {
    }

    public static boolean isBranch(String name) {
        return name != null && BY_REGION.values().stream().anyMatch(list -> list.contains(name));
    }
}
