package com.tbm.careerpathlearning.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * A course carrying a category (FR-19-05). It belongs to the course, so every version shares it; one of a course's
 * categories is its primary one. One link per course and category: patch P012.
 */
@Getter
@Setter
@Entity
@Table(name = "course_category_link")
public class CourseCategoryLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long linkId;

    /** The course: the id its first version has, which every version shares ({@link SopDocument#courseKey()}). */
    @Column(name = "course_key", nullable = false)
    private Long courseKey;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "is_primary", nullable = false, columnDefinition = "boolean default false not null")
    private boolean primary;
}
