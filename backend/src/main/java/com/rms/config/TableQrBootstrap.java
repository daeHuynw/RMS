package com.rms.config;

import com.rms.modules.table.entity.DiningTable;
import com.rms.modules.table.repository.DiningTableRepository;
import com.rms.modules.table.service.QrTokenService;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TableQrBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TableQrBootstrap.class);

    private final DiningTableRepository tables;
    private final QrTokenService qrTokens;

    public TableQrBootstrap(DiningTableRepository tables, QrTokenService qrTokens) {
        this.tables = tables;
        this.qrTokens = qrTokens;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<DiningTable> dirty = new ArrayList<>();
        for (DiningTable table : tables.findAll()) {
            if (!table.hasQr()) {
                qrTokens.rotate(table);
                dirty.add(table);
            }
        }
        if (!dirty.isEmpty()) {
            tables.saveAll(dirty);
            log.info("Generated QR tokens for {} dining table(s)", dirty.size());
        }
    }
}
