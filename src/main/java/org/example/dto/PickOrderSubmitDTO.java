package org.example.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 手机端提交的「待取用」领料单（一次可以提交多张）。
 *
 * <p>字段与 {@link PickExportDTO.Order} 对齐，电脑端取用后可以直接塞进导出请求，
 * 不需要在中间做一次字段翻译。
 */
@Data
public class PickOrderSubmitDTO {

    private List<Order> orders;

    @Data
    public static class Order {

        /** 领料部门；空的话电脑端会要求人工选 */
        private String dept;

        private String requester;

        private String docNo;

        /** yyyy-MM-dd */
        private String docDate;

        private List<Item> items;
    }

    @Data
    public static class Item {

        /** 物料编码：纸质单上常常手写，识别不一定准，允许手机端改 */
        private String materialCode;

        private String materialName;

        private Double quantity;

        private String unit;
    }
}
