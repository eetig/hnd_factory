package org.example.service;

import org.example.dto.PickOrderSubmitDTO;
import org.example.dto.PickOrderVO;

import java.util.List;

/**
 * 手机端领料单的暂存队列：手机提交，电脑取用。
 */
public interface PickOrderService {

    /** 手机提交若干张单，返回落库条数 */
    int submit(PickOrderSubmitDTO dto);

    /** 待取用列表（不带明细），按提交时间倒序 */
    List<PickOrderVO> pending();

    /** 取一张单的完整内容（含明细）；不改状态 —— 取用成功后才标记 */
    PickOrderVO detail(Long id);

    /** 标记已取用 */
    void markUsed(Long id);

    /** 丢弃（取用后发现是重复上传） */
    void discard(Long id);
}
