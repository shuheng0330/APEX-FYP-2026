package com.tbm.careerpathlearning.dto;


import lombok.Data;

import java.util.List;

@Data
public class CascaderOptionDTO {
    private String label;
    private Object value;
    private boolean isLeaf;
    private List<CascaderChildrenDTO> children;
}

