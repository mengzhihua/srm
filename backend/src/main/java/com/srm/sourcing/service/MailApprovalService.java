package com.srm.sourcing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.srm.common.BizException;
import com.srm.sourcing.entity.MailApproval;
import com.srm.sourcing.entity.PurchaseRequisition;
import com.srm.sourcing.mapper.MailApprovalMapper;
import com.srm.system.auth.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MailApprovalService {
    private final MailApprovalMapper mailMapper;
    private final PrService prService;

    @Transactional
    public MailApproval record(Long prId, String email) {
        CurrentUser.requireBuyerSide();
        String address;
        try {
            address = MailApprovalNote.address(email);
        } catch (IllegalArgumentException ex) {
            throw new BizException(ex.getMessage());
        }
        PurchaseRequisition pr = prService.load(prId);
        List<MailApproval> existing = mailMapper.selectList(new LambdaQueryWrapper<MailApproval>().eq(MailApproval::getPrId, pr.getId()));
        for (MailApproval row : existing) {
            if (row.getEmail() != null && row.getEmail().equalsIgnoreCase(address)) {
                return row;
            }
        }
        MailApproval created = new MailApproval();
        created.setPrId(pr.getId());
        created.setPrCode(pr.getCode());
        created.setEmail(address);
        created.setStatus("RECORDED");
        created.setDetail(MailApprovalNote.DETAIL);
        mailMapper.insert(created);
        return created;
    }
}
