package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.OrgChartType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrgChartGraphDto {

    private Long id;
    private String name;
    private OrgChartGraphNodeDto data;
    private List<OrgChartGraphDto> children;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public OrgChartGraphNodeDto getData() {
        return data;
    }

    public void setData(OrgChartGraphNodeDto data) {
        this.data = data;
    }

    public List<OrgChartGraphDto> getChildren() {
        return children;
    }

    public void setChildren(List<OrgChartGraphDto> children) {
        this.children = children;
    }
}
