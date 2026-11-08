package com.rms.modules.menu.controller;

import com.rms.modules.menu.service.MenuService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/menu-images")
public class MenuImageController {

    private final MenuService menu;

    public MenuImageController(MenuService menu) {
        this.menu = menu;
    }

    @GetMapping("/{name}")
    public ResponseEntity<FileSystemResource> image(@PathVariable String name) {
        var path = menu.imageFile(name);
        String type = name.endsWith(".png")
                ? MediaType.IMAGE_PNG_VALUE
                : name.endsWith(".webp") ? "image/webp" : MediaType.IMAGE_JPEG_VALUE;
        return ResponseEntity.ok()
                .header("Content-Type", type)
                .body(new FileSystemResource(path));
    }
}
