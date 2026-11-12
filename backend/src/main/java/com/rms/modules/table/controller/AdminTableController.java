package com.rms.modules.table.controller;

import com.rms.modules.table.dto.TableDtos.QrTokenResponse;
import com.rms.modules.table.dto.TableDtos.StatusRequest;
import com.rms.modules.table.dto.TableDtos.TableRequest;
import com.rms.modules.table.dto.TableDtos.TableResponse;
import com.rms.modules.table.service.TableService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/tables")
public class AdminTableController {

    private final TableService tables;

    public AdminTableController(TableService tables) {
        this.tables = tables;
    }

    @GetMapping
    public List<TableResponse> list() {
        return tables.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TableResponse create(@Valid @RequestBody TableRequest request) {
        return tables.create(request);
    }

    @PutMapping("/{id}")
    public TableResponse update(@PathVariable Long id, @Valid @RequestBody TableRequest request) {
        return tables.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public TableResponse status(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return tables.changeStatus(id, request.status());
    }

    @GetMapping("/{id}/qr")
    public QrTokenResponse qr(@PathVariable Long id) {
        return tables.getQr(id);
    }

    @PostMapping("/{id}/qr/rotate")
    public QrTokenResponse rotate(@PathVariable Long id) {
        return tables.rotateQr(id);
    }
}
