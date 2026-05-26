package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.OrgChart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrgChartRepository extends JpaRepository<OrgChart, Long> {

    @Query("SELECT org FROM OrgChart org WHERE org.type = 'D' AND org.isDeleted = false ORDER BY org.name")
    List<OrgChart> findAllByOrgChartTypeD();

    List<OrgChart> findAllByIsDeletedIsFalse();
}