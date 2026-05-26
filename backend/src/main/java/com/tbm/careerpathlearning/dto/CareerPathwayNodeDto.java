package com.tbm.careerpathlearning.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CareerPathwayNodeDto {

    private Long id;
    private String name;
    private CareerPathwayNodeDataDto data;
    private List<CareerPathwayNodeDto> children;

    public CareerPathwayNodeDto(Long id, String name, List<CareerPathwayNodeDto> children) {
        this.id = id;
        this.name = name;
        this.children = children;
    }

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

    public CareerPathwayNodeDataDto getData() {
        return data;
    }

    public void setData(CareerPathwayNodeDataDto data) {
        this.data = data;
    }

    public List<CareerPathwayNodeDto> getChildren() {
        return children;
    }

    public void setChildren(List<CareerPathwayNodeDto> children) {
        this.children = children;
    }
}
