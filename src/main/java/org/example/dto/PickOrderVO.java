package org.example.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 队列里的领料单（给电脑端取用）。
 *
 * <p>{@code items} 只在「取用」和「查看」时返回：列表页只显示 部门/单号/行数/时间，
 * 没必要把每条明细都传一遍。
 */
@Data
public class PickOrderVO {

    private Long id;

    private String dept;

    private String requester;

    private String docNo;

    private String docDate;

    private Integer itemCount;

    private LocalDateTime createdAt;

    private List<PickOrderSubmitDTO.Item> items;
}
