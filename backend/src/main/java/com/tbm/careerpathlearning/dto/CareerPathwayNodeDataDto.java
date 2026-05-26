package com.tbm.careerpathlearning.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CareerPathwayNodeDataDto {

    private int hasVisited;
    private boolean isDeleted;

    // -1 > not visited yet
    //  0 > current role
    //  1 > visited


    public CareerPathwayNodeDataDto(boolean isDeleted) {
        this.isDeleted = isDeleted;
    }

    public int getHasVisited() {
        return hasVisited;
    }

    public void setHasVisited(int hasVisited) {
        this.hasVisited = hasVisited;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public void setDeleted(boolean deleted) {
        isDeleted = deleted;
    }
}
