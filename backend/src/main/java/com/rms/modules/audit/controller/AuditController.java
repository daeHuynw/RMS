package com.rms.modules.audit.controller;

import com.rms.modules.audit.service.AuditService;
import com.rms.modules.audit.service.AuditService.View;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit")
public class AuditController {

    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    public List<View> recent() {
        return audit.recent();
    }
}
