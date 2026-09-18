package org.example.dto;

import lombok.Data;
import java.util.List;

@Data
public class ExcelResult<T> {
    private Boolean success;
    private String msg;
    private List<T> dataList;
    private List<String> errorMsgList;
    private Integer totalRow;
    private Integer successRow;
}
