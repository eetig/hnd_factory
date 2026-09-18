package org.example.controller;

import org.example.dto.ExcelResult;
import org.example.service.MaterialMovementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/movement")
public class MaterialMovementController {

    @Autowired
    private MaterialMovementService materialMovementService;

    @PostMapping("/import")
    public ExcelResult<Void> importGoodsMove(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "templateCode", defaultValue = "goods_move") String templateCode) {
        return materialMovementService.importGoodsMove(file, templateCode);
    }
}
