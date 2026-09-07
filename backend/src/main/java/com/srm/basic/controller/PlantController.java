package com.srm.basic.controller;

import com.srm.basic.entity.Plant;
import com.srm.basic.mapper.PlantMapper;
import com.srm.common.BaseCrudController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/basic/plant")
public class PlantController extends BaseCrudController<Plant, PlantMapper> {
    public PlantController() {
        super(Plant.class);
    }

    @Override
    protected String[] keywordColumns() {
        return new String[]{"code", "name"};
    }
}
