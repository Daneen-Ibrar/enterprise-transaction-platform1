package com.enterprise.aop;

import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.springframework.context.annotation.Configuration;

@Configuration
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = Long.class))
public class TenantFilterDefinition {
    // This class exists only to define the filter once
    // All entities use @Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
}