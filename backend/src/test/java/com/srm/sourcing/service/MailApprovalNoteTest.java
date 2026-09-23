package com.srm.sourcing.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MailApprovalNoteTest {
    @Test
    void recordsAnAddressWithoutSending() {
        assertEquals("buyer@example.com", MailApprovalNote.address(" buyer@example.com "));
        assertEquals("已登记，未连接邮件网关", MailApprovalNote.DETAIL);
        assertThrows(IllegalArgumentException.class, () -> MailApprovalNote.address("buyer"));
        assertThrows(IllegalArgumentException.class, () -> MailApprovalNote.address("@example.com"));
    }
}
