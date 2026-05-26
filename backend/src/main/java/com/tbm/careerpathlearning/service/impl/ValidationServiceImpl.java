package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.service.ValidationService;
import org.springframework.stereotype.Service;

@Service
public class ValidationServiceImpl implements ValidationService {

    @Override
    public boolean isNullOrBlank(String str) {
        return str == null || str.trim().isBlank();
    }
}

