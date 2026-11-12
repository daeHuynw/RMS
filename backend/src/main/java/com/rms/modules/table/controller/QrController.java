package com.rms.modules.table.controller;

import com.rms.modules.table.dto.TableDtos.QrMenuResponse;
import com.rms.modules.table.dto.TableDtos.SessionResponse;
import com.rms.modules.table.service.TableService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/qr")
public class QrController {

    private final TableService tables;

    public QrController(TableService tables) {
        this.tables = tables;
    }

    /** Token qua query — tránh PathVariable cắt / encode token Base64. */
    @GetMapping("/menu")
    public QrMenuResponse menu(@RequestParam("token") String token) {
        return tables.qrMenu(token);
    }

    @GetMapping("/session")
    public SessionResponse session(@RequestParam("token") String token) {
        return tables.qrSession(token);
    }
}
