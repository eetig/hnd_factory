package org.example.dto;
import lombok.Data;
import java.util.List;
@Data
public class FileQueryDTO {
    private String businessType;
    private List<String> businessNoList;
}
