package com.tbm.careerpathlearning.aspect;

import java.lang.annotation.*;

/** Prevents the application aspect from recording request, response or exception content. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface MetadataOnlyLogging {}
