package com.srm.basic.controller;

import com.srm.basic.entity.Material;
import com.srm.basic.mapper.MaterialMapper;
import com.srm.common.BaseCrudController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/basic/material")
public class MaterialController extends BaseCrudController<Material, MaterialMapper> {
    public MaterialController() {
        super(Material.class);
    }

    @Override
    protected String[] keywordColumns() {
        return new String[]{"code", "name"};
    }
}
