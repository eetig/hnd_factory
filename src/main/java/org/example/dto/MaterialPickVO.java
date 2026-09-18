package org.example.dto;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
@Data
public class MaterialPickVO {
    private Long id;
    private String pickNo;
    private LocalDateTime pickDate;
    private String materialName;
    private Double pickWeight;
    private String remark;
    private List<FileAttachmentDTO> fileList; // 当前单据对应的图片集合
}
