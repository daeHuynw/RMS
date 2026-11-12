package com.rms.modules.table.controller;

import com.rms.modules.table.dto.TableDtos.QrTokenResponse;
import com.rms.modules.table.dto.TableDtos.SessionResponse;
import com.rms.modules.table.dto.TableDtos.StatusRequest;
import com.rms.modules.table.dto.TableDtos.TableResponse;
import com.rms.modules.table.service.TableService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/staff")
public class StaffTableController {

    private final TableService tables;

    public StaffTableController(TableService tables) {
        this.tables = tables;
    }

    @GetMapping("/tables")
    public List<TableResponse> list() {
        return tables.list();
    }

    @GetMapping("/tables/{id}")
    public TableResponse get(@PathVariable Long id) {
        return tables.get(id);
    }

    /** Staff lấy link gọi món QR (không xoay token). */
    @GetMapping("/tables/{id}/qr")
    public QrTokenResponse qr(@PathVariable Long id) {
        return tables.getQr(id);
    }

    @PatchMapping("/tables/{id}/status")
    public TableResponse status(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return tables.changeStatus(id, request.status());
    }

    @GetMapping("/sessions/open")
    public List<SessionResponse> openSessions() {
        return tables.openSessions();
    }

    @PostMapping("/tables/{id}/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse open(@PathVariable Long id, Authentication authentication) {
        return tables.openSession(id, (Long) authentication.getPrincipal());
    }

    @PostMapping("/sessions/{id}/close")
    public SessionResponse close(@PathVariable Long id) {
        return tables.closeSession(id);
    }
}
