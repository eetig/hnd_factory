package org.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.PickOrderSubmitDTO;
import org.example.dto.PickOrderVO;
import org.example.entity.PickOrder;
import org.example.exception.BusinessException;
import org.example.mapper.PickOrderMapper;
import org.example.service.PickOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 队列实现：只做落库、列队、标记，不碰模板与 Excel —— 导出仍由
 * {@code PickTemplateServiceImpl} 在电脑端完成，保证「手机发什么 = 电脑导什么」同一条链路。
 */
@Service
public class PickOrderServiceImpl implements PickOrderService {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private PickOrderMapper pickOrderMapper;

    @Override
    @Transactional
    public int submit(PickOrderSubmitDTO dto) {
        if (dto == null || dto.getOrders() == null || dto.getOrders().isEmpty()) {
            throw new BusinessException("没有可提交的领料单");
        }
        int saved = 0;
        for (PickOrderSubmitDTO.Order order : dto.getOrders()) {
            if (order == null || order.getItems() == null || order.getItems().isEmpty()) {
                // 空单没有意义，也不能让它占住队列给人「还有一单没处理」的错觉
                continue;
            }
            PickOrder row = new PickOrder();
            row.setDept(trim(order.getDept()));
            row.setRequester(trim(order.getRequester()));
            row.setDocNo(trim(order.getDocNo()));
            row.setDocDate(trim(order.getDocDate()));
            row.setItemCount(order.getItems().size());
            row.setItemsJson(writeItems(order.getItems()));
            row.setStatus(0);
            row.setCreatedAt(LocalDateTime.now());
            pickOrderMapper.insert(row);
            saved++;
        }
        if (saved == 0) {
            throw new BusinessException("每张单至少要有一行物料");
        }
        return saved;
    }

    @Override
    public List<PickOrderVO> pending() {
        QueryWrapper<PickOrder> wrapper = new QueryWrapper<>();
        wrapper.eq("status", 0).orderByDesc("id");
        return pickOrderMapper.selectList(wrapper).stream().map(row -> toVO(row, false)).collect(Collectors.toList());
    }

    @Override
    public PickOrderVO detail(Long id) {
        PickOrder row = pickOrderMapper.selectById(id);
        if (row == null) {
            throw new BusinessException("这条领料单已不存在");
        }
        return toVO(row, true);
    }

    @Override
    public void markUsed(Long id) {
        PickOrder row = pickOrderMapper.selectById(id);
        if (row == null) {
            throw new BusinessException("这条领料单已不存在");
        }
        row.setStatus(1);
        row.setUsedAt(LocalDateTime.now());
        pickOrderMapper.updateById(row);
    }

    @Override
    public void discard(Long id) {
        pickOrderMapper.deleteById(id);
    }

    private PickOrderVO toVO(PickOrder row, boolean withItems) {
        PickOrderVO vo = new PickOrderVO();
        vo.setId(row.getId());
        vo.setDept(row.getDept());
        vo.setRequester(row.getRequester());
        vo.setDocNo(row.getDocNo());
        vo.setDocDate(row.getDocDate());
        vo.setItemCount(row.getItemCount());
        vo.setCreatedAt(row.getCreatedAt());
        if (withItems) {
            vo.setItems(readItems(row.getItemsJson()));
        }
        return vo;
    }

    private String writeItems(List<PickOrderSubmitDTO.Item> items) {
        try {
            return JSON.writeValueAsString(items);
        } catch (Exception e) {
            throw new BusinessException("明细保存失败：" + e.getMessage());
        }
    }

    private List<PickOrderSubmitDTO.Item> readItems(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return JSON.readValue(json, new TypeReference<List<PickOrderSubmitDTO.Item>>() {
            });
        } catch (Exception e) {
            // 明细坏了不该让整个列表 500：返回空，让电脑端能删掉这条脏数据
            return Collections.emptyList();
        }
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
