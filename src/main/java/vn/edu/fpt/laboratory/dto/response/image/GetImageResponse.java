package vn.edu.fpt.laboratory.dto.response.image;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author : Hoang Lam
 * @product : Charity Management System
 * @project : Charity System
 * @created : 30/11/2022 - 22:11
 * @contact : 0834481768 - hoang.harley.work@gmail.com
 **/
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class GetImageResponse implements Serializable {

    private static final long serialVersionUID = -7594311781155770618L;
    private String imageId;
    private String imageName;
    private String url;
}
