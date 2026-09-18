package org.example.controller;

import org.example.dto.MaterialPickVO;
import org.example.service.MaterialPickService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/pick")
public class MaterialPickController {

    @Autowired
    private MaterialPickService materialPickService;

    @GetMapping("/test")
    public String test(){
        return "ok";
    }

    @GetMapping("/list")
    public List<MaterialPickVO> list(){
        return materialPickService.getPickList();
    }
}
