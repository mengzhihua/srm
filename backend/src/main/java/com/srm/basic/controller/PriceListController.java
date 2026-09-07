package com.srm.basic.controller;

import com.srm.basic.entity.PriceList;
import com.srm.basic.mapper.PriceListMapper;
import com.srm.common.BaseCrudController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/basic/pricelist")
public class PriceListController extends BaseCrudController<PriceList, PriceListMapper> {
    public PriceListController() {
        super(PriceList.class);
    }

    @Override
    protected String[] keywordColumns() {
        return new String[]{"supplier_code", "material_code", "contract_no"};
    }
}
