package vn.edu.fpt.laboratory.controller.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.fpt.laboratory.constant.ResponseStatusEnum;
import vn.edu.fpt.laboratory.controller.MemberController;
import vn.edu.fpt.laboratory.dto.common.GeneralResponse;
import vn.edu.fpt.laboratory.dto.request.member.AddMemberToLaboratoryRequest;
import vn.edu.fpt.laboratory.dto.request.member.AddMemberToProjectRequest;
import vn.edu.fpt.laboratory.dto.request.member.UpdateMemberInfoRequest;
import vn.edu.fpt.laboratory.factory.ResponseFactory;
import vn.edu.fpt.laboratory.service.MemberInfoService;

@RestController
@RequiredArgsConstructor
@Slf4j
/**
 * @author : Hoang Lam
 * @product : Charity Management System
 * @project : Charity System
 * @created : 29/11/2022 - 22:18
 * @contact : 0834481768 - hoang.harley.work@gmail.com
 **/

public class MemberControllerImpl implements MemberController {
    private final ResponseFactory responseFactory;
    private final MemberInfoService memberInfoService;

    @Override
    public ResponseEntity<GeneralResponse<Object>> updateMember(String memberId, UpdateMemberInfoRequest request) {
        memberInfoService.updateMember(memberId, request);
        return responseFactory.response(ResponseStatusEnum.SUCCESS);
    }


}
