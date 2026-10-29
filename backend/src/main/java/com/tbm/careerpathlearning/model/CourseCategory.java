package com.tbm.careerpathlearning.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A course category (FR-19): a name, a colour and an icon. The defaults come with the system and have a code; General
 * is the one every course falls back to, and can be neither deleted nor retired. Others are custom, made by staff.
 */
@Getter
@Setter
@Entity
@Table(name = "course_category")
public class CourseCategory {

    public static final String GENERAL = "GENERAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long categoryId;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    /** A colour from the palette, as #RRGGBB. */
    @Column(name = "color", nullable = false, length = 7)
    private String color;

    /** An icon name from the list offered. */
    @Column(name = "icon", nullable = false, length = 30)
    private String icon;

    /** Set for the defaults only (GENERAL, SALES...): patch P012 adds them. */
    @Column(name = "code", length = 20)
    private String code;

    /** A retired default: hidden from selection, kept on the courses that have it (FR-19-03). */
    @Column(name = "retired", nullable = false, columnDefinition = "boolean default false not null")
    private boolean retired;

    /** Who made a custom category; none for the defaults or those made from department names. */
    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public boolean isDefault() {
        return code != null;
    }

    public boolean isGeneral() {
        return GENERAL.equals(code);
    }
}
