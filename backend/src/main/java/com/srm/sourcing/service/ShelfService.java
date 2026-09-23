package com.srm.sourcing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.srm.basic.entity.DiscountBand;
import com.srm.basic.entity.Material;
import com.srm.basic.entity.Plant;
import com.srm.basic.entity.PriceList;
import com.srm.basic.mapper.DiscountBandMapper;
import com.srm.basic.mapper.MaterialMapper;
import com.srm.basic.mapper.PlantMapper;
import com.srm.basic.mapper.PriceListMapper;
import com.srm.basic.service.AgreementPrice;
import com.srm.basic.service.CouponLimit;
import com.srm.basic.service.ShelfPrice;
import com.srm.common.BizException;
import com.srm.sourcing.entity.Coupon;
import com.srm.sourcing.entity.PlantPool;
import com.srm.sourcing.entity.ShelfMark;
import com.srm.sourcing.mapper.CouponMapper;
import com.srm.sourcing.mapper.PlantPoolMapper;
import com.srm.sourcing.mapper.ShelfMarkMapper;
import com.srm.system.auth.CurrentUser;
import com.srm.system.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ShelfService {
    static final String FAVORITE = "FAVORITE";
    static final String HISTORY = "HISTORY";
    private static final String UNCATEGORIZED = "未分品类";
    private static final int HISTORY_LIMIT = 20;
    private static final BigDecimal QTY_1 = BigDecimal.ONE;

    private final MaterialMapper materialMapper;
    private final PriceListMapper priceListMapper;
    private final DiscountBandMapper discountBandMapper;
    private final PlantMapper plantMapper;
    private final PlantPoolMapper plantPoolMapper;
    private final ShelfMarkMapper markMapper;
    private final CouponMapper couponMapper;

    public Map<String, Object> shelf(String supplierCode, String couponCode, String plantCode) {
        CurrentUser.requireBuyerSide();
        String plant = blank(plantCode) ? null : requirePlant(plantCode).getCode();
        BigDecimal percent = couponPercent(couponCode);
        String supplier = blank(supplierCode) ? null : supplierCode.trim();
        List<PriceList> lists = supplier == null ? Collections.<PriceList>emptyList() : priceListMapper.selectList(null);
        List<DiscountBand> bands = supplier == null ? Collections.<DiscountBand>emptyList() : discountBandMapper.selectList(null);
        List<String> allowed = plant == null ? null : poolCodes(plant);
        LocalDate today = LocalDate.now();
        List<String> favorites = favoriteCodes();
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<String, List<Map<String, Object>>>();
        List<Material> materials = new ArrayList<Material>(materialMapper.selectList(null));
        materials.sort(Comparator.comparing(material -> text(material.getCode())));
        for (Material material : materials) {
            if (material.getStatus() != null && material.getStatus() != 1) {
                continue;
            }
            if (allowed != null && !containsCode(allowed, material.getCode())) {
                continue;
            }
            String category = categoryOf(material);
            List<Map<String, Object>> cards = grouped.get(category);
            if (cards == null) {
                cards = new ArrayList<Map<String, Object>>();
                grouped.put(category, cards);
            }
            cards.add(card(material, lists, bands, supplier, today, percent, favorites));
        }
        List<String> names = new ArrayList<String>(grouped.keySet());
        names.sort(new Comparator<String>() {
            @Override
            public int compare(String left, String right) {
                if (UNCATEGORIZED.equals(left)) {
                    return 1;
                }
                if (UNCATEGORIZED.equals(right)) {
                    return -1;
                }
                return left.compareTo(right);
            }
        });
        List<Map<String, Object>> groups = new ArrayList<Map<String, Object>>();
        for (String name : names) {
            Map<String, Object> group = new LinkedHashMap<String, Object>();
            group.put("category", name);
            group.put("cards", grouped.get(name));
            groups.add(group);
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("groups", groups);
        body.put("couponApplied", percent != null);
        body.put("couponPercent", percent);
        body.put("plantCode", plant);
        return body;
    }

    public List<PlantPool> pool(String plantCode) {
        CurrentUser.requireBuyerSide();
        if (blank(plantCode)) {
            return Collections.emptyList();
        }
        requirePlant(plantCode);
        List<PlantPool> rows = plantPoolMapper.selectList(new LambdaQueryWrapper<PlantPool>()
                .eq(PlantPool::getPlantCode, plantCode.trim())
                .orderByAsc(PlantPool::getMaterialCode));
        List<PlantPool> open = new ArrayList<PlantPool>();
        for (PlantPool row : rows) {
            if (row.getStatus() == null || row.getStatus() == 1) {
                open.add(row);
            }
        }
        return open;
    }

    @Transactional
    public PlantPool addPool(String plantCode, String materialCode) {
        CurrentUser.requireBuyerSide();
        Plant plant = requirePlant(plantCode);
        Material material = requireMaterial(materialCode);
        PlantPool existing = plantPoolMapper.selectOne(new LambdaQueryWrapper<PlantPool>()
                .eq(PlantPool::getPlantCode, plant.getCode())
                .eq(PlantPool::getMaterialCode, material.getCode())
                .last("LIMIT 1"));
        if (existing != null) {
            existing.setStatus(1);
            plantPoolMapper.updateById(existing);
            return existing;
        }
        PlantPool created = new PlantPool();
        created.setPlantCode(plant.getCode());
        created.setMaterialCode(material.getCode());
        created.setStatus(1);
        plantPoolMapper.insert(created);
        return created;
    }

    @Transactional
    public void removePool(String plantCode, String materialCode) {
        CurrentUser.requireBuyerSide();
        if (blank(plantCode) || blank(materialCode)) {
            throw new BizException("请选择工厂和物料");
        }
        List<PlantPool> rows = plantPoolMapper.selectList(new LambdaQueryWrapper<PlantPool>()
                .eq(PlantPool::getPlantCode, plantCode.trim()));
        for (PlantPool row : rows) {
            if (row.getMaterialCode() != null && row.getMaterialCode().equalsIgnoreCase(materialCode.trim())) {
                row.setStatus(0);
                plantPoolMapper.updateById(row);
            }
        }
    }

    public List<Coupon> coupons() {
        CurrentUser.requireBuyerSide();
        return couponMapper.selectList(new LambdaQueryWrapper<Coupon>()
                .eq(Coupon::getOwnerName, owner())
                .orderByDesc(Coupon::getId));
    }

    @Transactional
    public Coupon saveCoupon(String code, BigDecimal percent, String validFrom, String validTo, Integer maxUses) {
        CurrentUser.requireBuyerSide();
        if (blank(code)) {
            throw new BizException("请填写优惠券编码");
        }
        if (percent == null || percent.signum() <= 0 || percent.compareTo(new BigDecimal("100")) > 0) {
            throw new BizException("优惠券折扣要大于 0，并且不超过 100");
        }
        LocalDate from = parseDay(validFrom, "请填写优惠券生效日");
        LocalDate to = parseDay(validTo, "请填写优惠券失效日");
        if (to.isBefore(from)) {
            throw new BizException("优惠券失效日不能早于生效日");
        }
        if (maxUses == null || maxUses < 1) {
            throw new BizException("优惠券可用次数至少为 1");
        }
        Coupon existing = findCoupon(code);
        if (existing == null) {
            Coupon created = new Coupon();
            created.setOwnerName(owner());
            created.setCode(code.trim());
            created.setPercent(percent);
            created.setStatus(1);
            created.setValidFrom(from);
            created.setValidTo(to);
            created.setMaxUses(maxUses);
            created.setUsedCount(0);
            couponMapper.insert(created);
            return created;
        }
        existing.setPercent(percent);
        existing.setStatus(1);
        existing.setValidFrom(from);
        existing.setValidTo(to);
        existing.setMaxUses(maxUses);
        couponMapper.updateById(existing);
        return existing;
    }

    @Transactional
    public Coupon useCoupon(String code) {
        CurrentUser.requireBuyerSide();
        Coupon coupon = findCoupon(code);
        if (coupon == null || coupon.getPercent() == null || coupon.getPercent().signum() <= 0
                || !CouponLimit.usable(coupon.getStatus(), coupon.getValidFrom(), coupon.getValidTo(), LocalDate.now(),
                coupon.getMaxUses(), coupon.getUsedCount())) {
            throw new BizException("优惠券不在有效期内或次数已用完");
        }
        int used = coupon.getUsedCount() == null ? 0 : coupon.getUsedCount();
        coupon.setUsedCount(used + 1);
        couponMapper.updateById(coupon);
        return coupon;
    }

    @Transactional
    public ShelfMark favorite(String materialCode) {
        CurrentUser.requireBuyerSide();
        Material material = requireMaterial(materialCode);
        ShelfMark existing = findMark(material.getCode(), FAVORITE);
        if (existing != null) {
            return existing;
        }
        ShelfMark mark = new ShelfMark();
        mark.setOwnerName(owner());
        mark.setMaterialCode(material.getCode());
        mark.setKind(FAVORITE);
        markMapper.insert(mark);
        return mark;
    }

    @Transactional
    public void unfavorite(String materialCode) {
        CurrentUser.requireBuyerSide();
        if (blank(materialCode)) {
            throw new BizException("请选择物料");
        }
        markMapper.delete(new LambdaQueryWrapper<ShelfMark>()
                .eq(ShelfMark::getOwnerName, owner())
                .eq(ShelfMark::getMaterialCode, materialCode.trim())
                .eq(ShelfMark::getKind, FAVORITE));
    }

    @Transactional
    public ShelfMark history(String materialCode) {
        CurrentUser.requireBuyerSide();
        Material material = requireMaterial(materialCode);
        ShelfMark existing = findMark(material.getCode(), HISTORY);
        if (existing == null) {
            existing = new ShelfMark();
            existing.setOwnerName(owner());
            existing.setMaterialCode(material.getCode());
            existing.setKind(HISTORY);
            existing.setSeenAt(LocalDateTime.now());
            markMapper.insert(existing);
        } else {
            existing.setSeenAt(LocalDateTime.now());
            markMapper.updateById(existing);
        }
        trimHistory();
        return existing;
    }

    public Map<String, Object> marks() {
        CurrentUser.requireBuyerSide();
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("favorites", markMapper.selectList(new LambdaQueryWrapper<ShelfMark>()
                .eq(ShelfMark::getOwnerName, owner())
                .eq(ShelfMark::getKind, FAVORITE)
                .orderByDesc(ShelfMark::getId)));
        body.put("history", recentHistory());
        return body;
    }

    private Map<String, Object> card(Material material, List<PriceList> lists, List<DiscountBand> allBands, String supplier,
                                      LocalDate today, BigDecimal percent, List<String> favorites) {
        String code = material.getCode();
        PriceList contract = AgreementPrice.contract(lists, supplier, code, today);
        List<ShelfPrice.Band> rates = ratesFor(contract, allBands);
        BigDecimal price1 = ShelfPrice.afterBands(AgreementPrice.pick(lists, supplier, code, QTY_1, today), QTY_1, rates);
        List<Map<String, Object>> bandRows = new ArrayList<Map<String, Object>>();
        if (contract != null) {
            List<DiscountBand> own = new ArrayList<DiscountBand>();
            for (DiscountBand band : allBands) {
                if (contract.getId() != null && contract.getId().equals(band.getPriceListId())) {
                    own.add(band);
                }
            }
            own.sort(Comparator.comparing(band -> band.getMinQty() == null ? BigDecimal.ZERO : band.getMinQty()));
            for (DiscountBand band : own) {
                BigDecimal agreed = AgreementPrice.pick(lists, supplier, code, band.getMinQty(), today);
                BigDecimal price = ShelfPrice.afterBands(agreed, band.getMinQty(), rates);
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("minQty", band.getMinQty());
                item.put("rate", band.getRate());
                item.put("price", price);
                item.put("couponPrice", percent == null ? null : ShelfPrice.afterCoupon(price, percent));
                bandRows.add(item);
            }
        }
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("materialCode", code);
        row.put("name", material.getName());
        row.put("brand", material.getBrand());
        row.put("mfrPartNo", material.getMfrPartNo());
        row.put("spec", material.getSpec());
        row.put("unit", material.getUnit());
        row.put("category", categoryOf(material));
        row.put("agreementQty1", price1);
        row.put("couponQty1", percent == null ? null : ShelfPrice.afterCoupon(price1, percent));
        row.put("bands", bandRows);
        row.put("favorite", containsCode(favorites, code));
        return row;
    }

    private static List<ShelfPrice.Band> ratesFor(PriceList contract, List<DiscountBand> allBands) {
        List<ShelfPrice.Band> rates = new ArrayList<ShelfPrice.Band>();
        if (contract == null || contract.getId() == null) {
            return rates;
        }
        for (DiscountBand band : allBands) {
            if (contract.getId().equals(band.getPriceListId()) && band.getMinQty() != null && band.getRate() != null) {
                rates.add(new ShelfPrice.Band(band.getMinQty(), band.getRate()));
            }
        }
        return rates;
    }

    private BigDecimal couponPercent(String code) {
        if (blank(code)) {
            return null;
        }
        Coupon coupon = findCoupon(code);
        if (coupon == null || coupon.getPercent() == null || coupon.getPercent().signum() <= 0
                || coupon.getPercent().compareTo(new BigDecimal("100")) > 0) {
            return null;
        }
        if (!CouponLimit.usable(coupon.getStatus(), coupon.getValidFrom(), coupon.getValidTo(), LocalDate.now(),
                coupon.getMaxUses(), coupon.getUsedCount())) {
            return null;
        }
        return coupon.getPercent();
    }

    private Coupon findCoupon(String code) {
        List<Coupon> rows = couponMapper.selectList(new LambdaQueryWrapper<Coupon>().eq(Coupon::getOwnerName, owner()));
        for (Coupon coupon : rows) {
            if (coupon.getCode() != null && coupon.getCode().equalsIgnoreCase(code.trim())) {
                return coupon;
            }
        }
        return null;
    }

    private List<String> favoriteCodes() {
        List<ShelfMark> rows = markMapper.selectList(new LambdaQueryWrapper<ShelfMark>()
                .eq(ShelfMark::getOwnerName, owner())
                .eq(ShelfMark::getKind, FAVORITE));
        List<String> codes = new ArrayList<String>();
        for (ShelfMark row : rows) {
            codes.add(row.getMaterialCode());
        }
        return codes;
    }

    private ShelfMark findMark(String materialCode, String kind) {
        return markMapper.selectOne(new LambdaQueryWrapper<ShelfMark>()
                .eq(ShelfMark::getOwnerName, owner())
                .eq(ShelfMark::getMaterialCode, materialCode)
                .eq(ShelfMark::getKind, kind)
                .last("LIMIT 1"));
    }

    private void trimHistory() {
        List<ShelfMark> rows = markMapper.selectList(new LambdaQueryWrapper<ShelfMark>()
                .eq(ShelfMark::getOwnerName, owner())
                .eq(ShelfMark::getKind, HISTORY)
                .orderByDesc(ShelfMark::getSeenAt)
                .orderByDesc(ShelfMark::getId));
        for (int i = HISTORY_LIMIT; i < rows.size(); i++) {
            markMapper.deleteById(rows.get(i).getId());
        }
    }

    private List<ShelfMark> recentHistory() {
        return markMapper.selectList(new LambdaQueryWrapper<ShelfMark>()
                .eq(ShelfMark::getOwnerName, owner())
                .eq(ShelfMark::getKind, HISTORY)
                .orderByDesc(ShelfMark::getSeenAt)
                .orderByDesc(ShelfMark::getId)
                .last("LIMIT " + HISTORY_LIMIT));
    }

    private List<String> poolCodes(String plantCode) {
        List<PlantPool> rows = plantPoolMapper.selectList(new LambdaQueryWrapper<PlantPool>()
                .eq(PlantPool::getPlantCode, plantCode));
        List<String> codes = new ArrayList<String>();
        for (PlantPool row : rows) {
            if (row.getStatus() == null || row.getStatus() == 1) {
                codes.add(row.getMaterialCode());
            }
        }
        return codes;
    }

    private Plant requirePlant(String code) {
        if (blank(code)) {
            throw new BizException("请选择工厂");
        }
        for (Plant plant : plantMapper.selectList(null)) {
            if (plant.getCode() != null && plant.getCode().equalsIgnoreCase(code.trim())) {
                if (plant.getStatus() != null && plant.getStatus() != 1) {
                    throw new BizException("工厂已停用");
                }
                return plant;
            }
        }
        throw new BizException("工厂不存在");
    }

    private static LocalDate parseDay(String value, String message) {
        if (blank(value)) {
            throw new BizException(message);
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new BizException(message);
        }
    }

    private Material requireMaterial(String code) {
        if (blank(code)) {
            throw new BizException("请选择物料");
        }
        for (Material material : materialMapper.selectList(null)) {
            if (material.getCode() != null && material.getCode().equalsIgnoreCase(code.trim())) {
                if (material.getStatus() != null && material.getStatus() != 1) {
                    throw new BizException("物料已停用");
                }
                return material;
            }
        }
        throw new BizException("物料不存在");
    }

    private static boolean containsCode(List<String> codes, String code) {
        for (String item : codes) {
            if (item != null && item.equalsIgnoreCase(code)) {
                return true;
            }
        }
        return false;
    }

    private static String categoryOf(Material material) {
        if (material.getCategory() == null || material.getCategory().trim().isEmpty()) {
            return UNCATEGORIZED;
        }
        return material.getCategory().trim();
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static String owner() {
        User user = CurrentUser.get();
        if (user == null || user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            return "buyer";
        }
        return user.getUsername().trim();
    }
}
