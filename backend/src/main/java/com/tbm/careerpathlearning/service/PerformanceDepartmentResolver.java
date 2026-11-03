package com.tbm.careerpathlearning.service;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class PerformanceDepartmentResolver {
    private final OrgChartRepository nodes;
    private final ParentChildNodeRepository relationships;
    public PerformanceDepartmentResolver(OrgChartRepository nodes,ParentChildNodeRepository relationships) {this.nodes=nodes;this.relationships=relationships;}
    public OrgChart resolve(Role role) {
        if(role==null || role.getOrgChart()==null) throw new BadRequestException("The Job Role has no organisation assignment; confirm its Department");
        OrgChart node=role.getOrgChart();
        if(node.isDeleted()) throw new BadRequestException("The Job Role's organisation node has been deleted");
        if(node.getType()==OrgChartType.D) return node;
        Map<Long,List<Long>> parents=new HashMap<>();
        relationships.findAllByRelationType(RelationType.ORG_CHART).forEach(r->parents.computeIfAbsent(r.getChildId(),k->new ArrayList<>()).add(r.getParentId()));
        checkCycles(node.getId(), parents, new HashSet<>(), new HashSet<>());
        Set<Long> visited=new HashSet<>();Set<Long> frontier=Set.of(node.getId());
        while(!frontier.isEmpty()) {
            Set<Long> next=new HashSet<>();Map<Long,OrgChart> departments=new HashMap<>();
            for(Long id:frontier) {
                if(!visited.add(id)) continue;
                for(Long parent:parents.getOrDefault(id,List.of())) {
                    OrgChart candidate=nodes.findById(parent).orElseThrow(()->new BadRequestException("Organisation hierarchy contains a missing node"));
                    if(candidate.isDeleted()) throw new BadRequestException("Organisation hierarchy contains a deleted parent; confirm Department scope");
                    if(candidate.getType()==OrgChartType.D) departments.put(candidate.getId(),candidate);
                    else if(!visited.contains(parent)) next.add(parent);
                }
            }
            if(departments.size()>1) throw new BadRequestException("The Job Role has ambiguous Department membership; confirm its organisation hierarchy");
            if(departments.size()==1) return departments.values().iterator().next();
            frontier=next;
        }
        // A top-level organisation position can legitimately have no Department.
        return null;
    }
    private void checkCycles(Long id, Map<Long,List<Long>> parents, Set<Long> path, Set<Long> checked) {
        if(path.contains(id)) throw new BadRequestException("Organisation hierarchy contains a cycle; confirm Department scope");
        if(checked.contains(id)) return;
        path.add(id);
        for(Long parent:parents.getOrDefault(id,List.of())) checkCycles(parent,parents,path,checked);
        path.remove(id);checked.add(id);
    }
}
