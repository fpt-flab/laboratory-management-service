package vn.edu.fpt.laboratory.dto.request.material;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import vn.edu.fpt.laboratory.dto.common.CreateFileRequest;

import java.io.Serializable;

/**
 * @author : Hoang Lam
 * @product : Charity Management System
 * @project : Charity System
 * @created : 30/11/2022 - 18:28
 * @contact : 0834481768 - hoang.harley.work@gmail.com
 **/
@AllArgsConstructor
@Data
@Builder
public class UpdateMaterialRequest implements Serializable {

    private static final long serialVersionUID = 5721544750162383318L;
    private String materialName;
    private String description;
    private String status;
    private Integer amount;
    private CreateFileRequest image;
    private String note;
}
