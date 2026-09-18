package org.example.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.example.dto.FileAttachmentDTO;
import org.example.dto.FileQueryDTO;
import org.example.dto.MaterialPickVO;
import org.example.entity.MaterialPick;
import org.example.feign.FileFeignClient;
import org.example.mapper.MaterialPickMapper;
import org.example.service.MaterialPickService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MaterialPickServiceImpl extends ServiceImpl<MaterialPickMapper, MaterialPick> implements MaterialPickService {

    @Autowired
    private FileFeignClient fileFeignClient;

    @Override
    public List<MaterialPickVO> getPickList() {
        // 1. 查询全部领料单
        List<MaterialPick> pickList = baseMapper.selectList(null);
        // 提取所有领料单号
        List<String> pickNoList = pickList.stream().map(MaterialPick::getPickNo).collect(Collectors.toList());
        if(pickNoList.isEmpty()){
            return List.of();
        }
        // 2. Feign调用文件微服务查询附件
        FileQueryDTO query = new FileQueryDTO();
        query.setBusinessType("pick");
        query.setBusinessNoList(pickNoList);
        List<FileAttachmentDTO> fileList = fileFeignClient.getAttachmentList(query);
        // 按单号分组
        Map<String, List<FileAttachmentDTO>> fileGroupMap = fileList.stream()
                .collect(Collectors.groupingBy(FileAttachmentDTO::getBusinessNo));

        // 组装VO返回前端
        return pickList.stream().map(pick -> {
            MaterialPickVO vo = new MaterialPickVO();
            vo.setId(pick.getId());
            vo.setPickNo(pick.getPickNo());
            vo.setPickDate(pick.getPickDate());
            vo.setMaterialName(pick.getMaterialName());
            vo.setPickWeight(pick.getPickWeight());
            vo.setRemark(pick.getRemark());
            vo.setFileList(fileGroupMap.getOrDefault(pick.getPickNo(), List.of()));
            return vo;
        }).collect(Collectors.toList());
    }
}
