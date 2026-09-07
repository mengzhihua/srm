package com.srm.basic.controller;

import com.srm.basic.entity.Supplier;
import com.srm.basic.mapper.SupplierMapper;
import com.srm.common.BaseCrudController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/basic/supplier")
public class SupplierController extends BaseCrudController<Supplier, SupplierMapper> {
    public SupplierController() {
        super(Supplier.class);
    }

    @Override
    protected String[] keywordColumns() {
        return new String[]{"code", "name", "short_name"};
    }
}
