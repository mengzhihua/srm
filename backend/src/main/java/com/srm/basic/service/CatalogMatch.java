package com.srm.basic.service;

import com.srm.basic.entity.Material;

import java.util.List;
import java.util.Locale;

/** 按物料编码、制造商料号，或品牌加制造商料号，把一段需求文字对上物料。对不上就返回空。 */
public final class CatalogMatch {
    private CatalogMatch() {}

    public static Material match(List<Material> materials, String text) {
        if (materials == null || text == null || text.trim().isEmpty()) {
            return null;
        }
        String query = text.trim();
        String upper = query.toUpperCase(Locale.ROOT);
        for (Material material : materials) {
            if (same(material.getCode(), query)) {
                return material;
            }
        }
        for (Material material : materials) {
            if (same(material.getMfrPartNo(), query)) {
                return material;
            }
        }
        for (Material material : materials) {
            String brand = material.getBrand();
            String part = material.getMfrPartNo();
            if (brand == null || brand.trim().isEmpty() || part == null || part.trim().isEmpty()) {
                continue;
            }
            if (upper.contains(brand.trim().toUpperCase(Locale.ROOT))
                    && upper.contains(part.trim().toUpperCase(Locale.ROOT))) {
                return material;
            }
        }
        return null;
    }

    private static boolean same(String value, String query) {
        return value != null && value.trim().equalsIgnoreCase(query.trim());
    }
}
