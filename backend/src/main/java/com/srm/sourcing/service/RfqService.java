package com.srm.sourcing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.srm.basic.entity.PriceList;
import com.srm.basic.mapper.PriceListMapper;
import com.srm.common.BizException;
import com.srm.common.CodeGenerator;
import com.srm.purchase.entity.PoLine;
import com.srm.purchase.entity.PurchaseOrder;
import com.srm.purchase.service.PurchaseOrderService;
import com.srm.sourcing.entity.Rfq;
import com.srm.sourcing.entity.RfqLine;
import com.srm.sourcing.entity.RfqQuote;
import com.srm.sourcing.entity.RfqQuoteLine;
import com.srm.sourcing.mapper.RfqLineMapper;
import com.srm.sourcing.mapper.RfqMapper;
import com.srm.sourcing.mapper.RfqQuoteLineMapper;
import com.srm.sourcing.mapper.RfqQuoteMapper;
import com.srm.system.auth.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 询价 RFQ：DRAFT -> PUBLISHED ->（供应商报价）-> AWARDED(定标自动生成PO) */
@Service
@RequiredArgsConstructor
public class RfqService {
    private final RfqMapper rfqMapper;
    private final RfqLineMapper lineMapper;
    private final RfqQuoteMapper quoteMapper;
    private final RfqQuoteLineMapper quoteLineMapper;
    private final PurchaseOrderService poService;
    private final PriceListMapper priceListMapper;
    private final CodeGenerator codeGenerator;

    public Rfq load(Long id) {
        Rfq rfq = rfqMapper.selectById(id);
        if (rfq == null) {
            throw new BizException("询价单不存在: " + id);
        }
        rfq.setLines(lineMapper.selectList(new LambdaQueryWrapper<RfqLine>()
                .eq(RfqLine::getRfqId, id).orderByAsc(RfqLine::getLineNo)));
        List<RfqQuote> quotes = quoteMapper.selectList(new LambdaQueryWrapper<RfqQuote>()
                .eq(RfqQuote::getRfqId, id).orderByAsc(RfqQuote::getId));
        String mine = CurrentUser.supplierCode();
        if (mine != null) {
            quotes = quotes.stream().filter(q -> mine.equals(q.getSupplierCode())).collect(Collectors.toList());
        }
        for (RfqQuote q : quotes) {
            q.setLines(quoteLineMapper.selectList(new LambdaQueryWrapper<RfqQuoteLine>()
                    .eq(RfqQuoteLine::getQuoteId, q.getId())));
        }
        rfq.setQuotes(quotes);
        return rfq;
    }

    public Page<Rfq> page(long current, long size, String status, String keyword) {
        QueryWrapper<Rfq> qw = new QueryWrapper<>();
        String mine = CurrentUser.supplierCode();
        if (mine != null) {
            qw.like("supplier_codes", mine); // 供应商只能看到邀请了自己的询价
        }
        qw.eq(StringUtils.isNotBlank(status), "status", status)
                .and(StringUtils.isNotBlank(keyword),
                        w -> w.like("code", keyword).or().like("title", keyword))
                .orderByDesc("id");
        return rfqMapper.selectPage(new Page<>(current, size), qw);
    }

    @Transactional
    public Rfq create(Rfq rfq) {
        CurrentUser.requireBuyerSide();
        if (rfq.getLines() == null || rfq.getLines().isEmpty()) {
            throw new BizException("询价行不能为空");
        }
        rfq.setId(null);
        rfq.setCode(codeGenerator.next("RFQ"));
        rfq.setStatus("DRAFT");
        rfqMapper.insert(rfq);
        int i = 1;
        for (RfqLine l : rfq.getLines()) {
            l.setId(null);
            l.setRfqId(rfq.getId());
            l.setLineNo(i++);
            lineMapper.insert(l);
        }
        return load(rfq.getId());
    }

    @Transactional
    public Rfq publish(Long id) {
        CurrentUser.requireBuyerSide();
        Rfq rfq = load(id);
        if (!"DRAFT".equals(rfq.getStatus())) {
            throw new BizException("仅草稿可发布");
        }
        if (rfq.getSupplierCodes() == null || rfq.getSupplierCodes().trim().isEmpty()) {
            throw new BizException("请先指定受邀供应商");
        }
        rfq.setStatus("PUBLISHED");
        rfqMapper.updateById(rfq);
        return load(id);
    }

    @Transactional
    public Rfq cancel(Long id) {
        CurrentUser.requireBuyerSide();
        Rfq rfq = load(id);
        if ("AWARDED".equals(rfq.getStatus())) {
            throw new BizException("已定标的询价不可取消");
        }
        rfq.setStatus("CANCELLED");
        rfqMapper.updateById(rfq);
        return load(id);
    }

    /** 供应商报价（只能给自己提交） */
    @Transactional
    public RfqQuote quote(Long rfqId, RfqQuote quote) {
        Rfq rfq = load(rfqId);
        String mine = CurrentUser.supplierCode();
        if (mine != null) {
            quote.setSupplierCode(mine); // 强制使用登录账号绑定的供应商
        }
        if (quote.getSupplierCode() == null) {
            throw new BizException("缺少供应商编码");
        }
        if (rfq.getSupplierCodes() == null
                || !Arrays.asList(rfq.getSupplierCodes().split(",")).contains(quote.getSupplierCode())) {
            throw new BizException("该供应商未被邀请参与此询价");
        }
        if (!"PUBLISHED".equals(rfq.getStatus()) && !"QUOTING".equals(rfq.getStatus())) {
            throw new BizException("询价当前状态不可报价: " + rfq.getStatus());
        }
        if (quote.getLines() == null || quote.getLines().isEmpty()) {
            throw new BizException("报价行不能为空");
        }
        // 同一供应商只保留最新报价
        quoteMapper.delete(new LambdaQueryWrapper<RfqQuote>()
                .eq(RfqQuote::getRfqId, rfqId).eq(RfqQuote::getSupplierCode, quote.getSupplierCode()));
        quote.setId(null);
        quote.setRfqId(rfqId);
        quote.setStatus("SUBMITTED");
        BigDecimal total = BigDecimal.ZERO;
        for (RfqQuoteLine l : quote.getLines()) {
            l.setId(null);
            if (l.getPrice() != null) {
                RfqLine rl = l.getRfqLineId() == null ? null : lineMapper.selectById(l.getRfqLineId());
                if (rl != null && rl.getQty() != null) {
                    total = total.add(l.getPrice().multiply(rl.getQty()));
                }
            }
        }
        quote.setTotalAmount(total);
        quoteMapper.insert(quote);
        for (RfqQuoteLine l : quote.getLines()) {
            l.setQuoteId(quote.getId());
            quoteLineMapper.insert(l);
        }
        if ("PUBLISHED".equals(rfq.getStatus())) {
            rfq.setStatus("QUOTING");
            rfqMapper.updateById(rfq);
        }
        return quote;
    }

    /** 定标：选定报价 -> 生成 PO（取报价价格），回写 PriceList */
    @Transactional
    public PurchaseOrder award(Long rfqId, Long quoteId, LocalDate expectedDate) {
        CurrentUser.requireBuyerSide();
        Rfq rfq = load(rfqId);
        if (!Arrays.asList("PUBLISHED", "QUOTING").contains(rfq.getStatus())) {
            throw new BizException("当前状态不可定标: " + rfq.getStatus());
        }
        RfqQuote win = quoteMapper.selectById(quoteId);
        if (win == null || !win.getRfqId().equals(rfqId) || !"SUBMITTED".equals(win.getStatus())) {
            throw new BizException("报价不存在或已处理: " + quoteId);
        }
        win.setLines(quoteLineMapper.selectList(new LambdaQueryWrapper<RfqQuoteLine>()
                .eq(RfqQuoteLine::getQuoteId, quoteId)));
        Map<Long, RfqLine> rfqLines = rfq.getLines().stream()
                .collect(Collectors.toMap(RfqLine::getId, l -> l));

        PurchaseOrder po = new PurchaseOrder();
        po.setSupplierCode(win.getSupplierCode());
        po.setPlantCode(rfq.getPlantCode());
        po.setExpectedDate(expectedDate);
        po.setSourceType("RFQ");
        po.setSourceCode(rfq.getCode());
        po.setLines(rfq.getLines().stream().map(rl -> {
            PoLine pl = new PoLine();
            pl.setMaterialCode(rl.getMaterialCode());
            pl.setQty(rl.getQty());
            pl.setDeliveryDate(rl.getRequiredDate());
            win.getLines().stream()
                    .filter(q -> rl.getId().equals(q.getRfqLineId()) || rl.getMaterialCode().equals(q.getMaterialCode()))
                    .findFirst().ifPresent(q -> pl.setPrice(q.getPrice()));
            return pl;
        }).collect(Collectors.toList()));
        PurchaseOrder saved = poService.create(po);

        win.setStatus("AWARDED");
        quoteMapper.updateById(win);
        quoteMapper.selectList(new LambdaQueryWrapper<RfqQuote>()
                        .eq(RfqQuote::getRfqId, rfqId).ne(RfqQuote::getId, quoteId))
                .forEach(q -> {
                    q.setStatus("LOST");
                    quoteMapper.updateById(q);
                });
        rfq.setStatus("AWARDED");
        rfqMapper.updateById(rfq);

        // 回写价格表
        for (RfqQuoteLine q : win.getLines()) {
            RfqLine rl = q.getRfqLineId() == null ? null : rfqLines.get(q.getRfqLineId());
            String mat = rl != null ? rl.getMaterialCode() : q.getMaterialCode();
            if (mat == null || q.getPrice() == null) {
                continue;
            }
            PriceList pl = new PriceList();
            pl.setSupplierCode(win.getSupplierCode());
            pl.setMaterialCode(mat);
            pl.setPrice(q.getPrice());
            pl.setCurrency("CNY");
            pl.setMinQty(rl != null ? rl.getQty() : null);
            pl.setValidFrom(LocalDate.now());
            pl.setValidTo(LocalDate.now().plusYears(1));
            pl.setContractNo(rfq.getCode());
            pl.setStatus(1);
            priceListMapper.insert(pl);
        }
        return saved;
    }
}
