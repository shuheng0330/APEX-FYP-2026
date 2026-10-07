package com.tbm.careerpathlearning.service;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PerformanceDepartmentResolverTest {
    OrgChartRepository nodes=mock(OrgChartRepository.class);
    ParentChildNodeRepository links=mock(ParentChildNodeRepository.class);
    PerformanceDepartmentResolver resolver=new PerformanceDepartmentResolver(nodes,links);
    @Test void directDepartmentIsReused() {var d=node(1L,OrgChartType.D);var r=new Role();r.setOrgChart(d);assertSame(d,resolver.resolve(r));verifyNoInteractions(links);}
    @Test void resolvesUniqueNearestDepartment() {var p=node(1L,OrgChartType.P);var d=node(2L,OrgChartType.D);var r=new Role();r.setOrgChart(p);
        when(links.findAllByRelationType(RelationType.ORG_CHART)).thenReturn(List.of(link(2L,1L)));when(nodes.findById(2L)).thenReturn(Optional.of(d));assertSame(d,resolver.resolve(r));}
    @Test void ambiguousDepartmentFailsRatherThanGuessing() {var r=new Role();r.setOrgChart(node(1L,OrgChartType.P));
        when(links.findAllByRelationType(RelationType.ORG_CHART)).thenReturn(List.of(link(2L,1L),link(3L,1L)));
        when(nodes.findById(2L)).thenReturn(Optional.of(node(2L,OrgChartType.D)));when(nodes.findById(3L)).thenReturn(Optional.of(node(3L,OrgChartType.D)));
        assertThrows(BadRequestException.class,()->resolver.resolve(r));}
    @Test void rejectsCycles() {var r=new Role();r.setOrgChart(node(1L,OrgChartType.P));when(links.findAllByRelationType(RelationType.ORG_CHART)).thenReturn(List.of(link(2L,1L),link(1L,2L)));assertThrows(BadRequestException.class,()->resolver.resolve(r));}
    private OrgChart node(Long id,OrgChartType type) {var n=new OrgChart();n.setId(id);n.setType(type);return n;}
    private ParentChildNode link(Long parent,Long child) {var l=new ParentChildNode();l.setParentId(parent);l.setChildId(child);return l;}
}
