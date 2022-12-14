package vn.edu.fpt.laboratory.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.laboratory.config.kafka.producer.GenerateProjectAppProducer;
import vn.edu.fpt.laboratory.constant.LaboratoryRoleEnum;
import vn.edu.fpt.laboratory.constant.ProjectRoleEnum;
import vn.edu.fpt.laboratory.constant.ResponseStatusEnum;
import vn.edu.fpt.laboratory.constant.RoleInLaboratoryEnum;
import vn.edu.fpt.laboratory.dto.cache.UserInfo;
import vn.edu.fpt.laboratory.dto.common.GeneralResponse;
import vn.edu.fpt.laboratory.dto.common.MemberInfoResponse;
import vn.edu.fpt.laboratory.dto.common.PageableResponse;
import vn.edu.fpt.laboratory.dto.common.UserInfoResponse;
import vn.edu.fpt.laboratory.dto.event.GenerateProjectAppEvent;
import vn.edu.fpt.laboratory.dto.request.member.GetMemberNotInProjectRequest;
import vn.edu.fpt.laboratory.dto.request.project._CreateProjectRequest;
import vn.edu.fpt.laboratory.dto.request.project._GetProjectRequest;
import vn.edu.fpt.laboratory.dto.request.project._UpdateProjectRequest;
import vn.edu.fpt.laboratory.dto.response.laboratory.GetMemberResponse;
import vn.edu.fpt.laboratory.dto.response.member.GetMemberNotInProjectResponse;
import vn.edu.fpt.laboratory.dto.response.project.CreateProjectResponse;
import vn.edu.fpt.laboratory.dto.response.project.GetProjectDetailResponse;
import vn.edu.fpt.laboratory.dto.response.project.GetProjectResponse;
import vn.edu.fpt.laboratory.entity.Laboratory;
import vn.edu.fpt.laboratory.entity.MemberInfo;
import vn.edu.fpt.laboratory.entity.Project;
import vn.edu.fpt.laboratory.exception.BusinessException;
import vn.edu.fpt.laboratory.repository.BaseMongoRepository;
import vn.edu.fpt.laboratory.repository.LaboratoryRepository;
import vn.edu.fpt.laboratory.repository.MemberInfoRepository;
import vn.edu.fpt.laboratory.repository.ProjectRepository;
import vn.edu.fpt.laboratory.service.ProjectService;
import vn.edu.fpt.laboratory.service.UserInfoService;
import vn.edu.fpt.laboratory.utils.AuditorUtils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author : Hoang Lam
 * @product : Charity Management System
 * @project : Charity System
 * @created : 30/11/2022 - 00:06
 * @contact : 0834481768 - hoang.harley.work@gmail.com
 **/
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectServiceImpl implements ProjectService {

    private final LaboratoryRepository laboratoryRepository;
    private final ProjectRepository projectRepository;
    private final MemberInfoRepository memberInfoRepository;
    private final UserInfoService userInfoService;
    private final MongoTemplate mongoTemplate;
    private final GenerateProjectAppProducer generateProjectAppProducer;

    @Override
    @Transactional
    public CreateProjectResponse createProject(String labId, _CreateProjectRequest request) {
        AuditorUtils auditorUtils = new AuditorUtils();

        Laboratory laboratory = laboratoryRepository.findById(labId)
                .orElseThrow(() -> new BusinessException(ResponseStatusEnum.BAD_REQUEST, "Laboratory ID not exist when create project: "+ labId));

        Optional<Project> projectInDb = laboratory.getProjects().stream().filter(v->v.getProjectName().equals(request.getProjectName())).findFirst();
        if (projectInDb.isPresent()) {
            throw new BusinessException(ResponseStatusEnum.BAD_REQUEST, "Project name already exist");
        }

        String accountId = auditorUtils.getAccountId();

        MemberInfo memberInfoInLab = laboratory.getMembers().stream().filter(m -> m.getAccountId().equals(accountId)).findAny()
                .orElseThrow(() -> new BusinessException("Account ID not contain in repository member"));

        if (!memberInfoInLab.getRole().equals(LaboratoryRoleEnum.OWNER.getRole()) && !memberInfoInLab.getRole().equals(LaboratoryRoleEnum.MANAGER.getRole())) {
            throw new BusinessException(ResponseStatusEnum.BAD_REQUEST, "Invalid role for create project");
        }

        MemberInfo memberInfo = MemberInfo.builder()
                .accountId(accountId)
                .role(ProjectRoleEnum.OWNER.getRole())
                .build();

        try {
            memberInfo = memberInfoRepository.save(memberInfo);
            log.info("Create member info success: {}", memberInfo);
        } catch (Exception ex) {
            throw new BusinessException("Can't create member info: " + ex.getMessage());
        }

        Project project = Project.builder()
                .projectName(request.getProjectName())
                .description(request.getDescription())
                .startDate(request.getStartDate())
                .toDate(request.getToDate())
                .ownerBy(memberInfo)
                .members(List.of(memberInfo))
                .build();

        try {
            project = projectRepository.save(project);
            log.info("Add project to database success");
        } catch (Exception ex) {
            throw new BusinessException("Can't create project in database: " + ex.getMessage());
        }

        List<Project> currentProject = laboratory.getProjects();
        currentProject.add(project);
        laboratory.setProjects(currentProject);

        try {
            laboratoryRepository.save(laboratory);
            log.info("Update laboratory success");
        } catch (Exception ex) {
            throw new BusinessException("Can't update laboratory in database: " + ex.getMessage());
        }

        generateProjectAppProducer.sendMessage(GenerateProjectAppEvent.builder()
                        .projectId(project.getProjectId())
                        .accountId(project.getCreatedBy())
                        .projectName(project.getProjectName())
                .build());

        return CreateProjectResponse.builder()
                .projectId(project.getProjectId())
                .build();
    }

    @Override
    public void updateProject(String labId, String projectId, _UpdateProjectRequest request) {
        Laboratory laboratory = laboratoryRepository.findById(labId)
                .orElseThrow(()-> new BusinessException(ResponseStatusEnum.BAD_REQUEST, "Laboratory ID not exist when update project: "+ labId));
        Project project = projectRepository.findById(projectId)
                .orElseThrow(()-> new BusinessException(ResponseStatusEnum.BAD_REQUEST, "Project ID not exist when update project: "+ projectId));
        List<Project> projects = laboratory.getProjects();

        if (projects.stream().noneMatch(m -> m.getProjectId().equals(projectId))) {
            throw new BusinessException(ResponseStatusEnum.BAD_REQUEST, "Laboratory not contain this project");
        }

        String accountId = userInfoService.getAccountId();

        MemberInfo memberInfo = project.getMembers().stream()
                .filter(v -> v.getAccountId().equals(accountId))
                .findAny().orElseThrow(()-> new BusinessException(ResponseStatusEnum.BAD_REQUEST, "Account Id not in project"));

        if(!memberInfo.getRole().equals("OWNER") && !memberInfo.getRole().equals("MANAGER")){
            throw new BusinessException("You don't have permission to update role");
        }
        if (!project.getProjectName().equals(request.getProjectName())) {
            if (Objects.nonNull(request.getProjectName())) {
                if (projects.stream().anyMatch(m -> m.getProjectName().equals(request.getProjectName()))) {
                    throw new BusinessException(ResponseStatusEnum.BAD_REQUEST, "Project name is already exist");
                } else {
                    project.setProjectName(request.getProjectName());
                }
            }
        }
        if (Objects.nonNull(request.getDescription())) {
            project.setDescription(request.getDescription());
        }
        if (Objects.nonNull(request.getStartDate())) {
            project.setStartDate(request.getStartDate());
        }
        if (Objects.nonNull(request.getToDate()) && request.getToDate().isAfter(request.getStartDate())) {
            project.setToDate(request.getToDate());
        }
        try {
            projectRepository.save(project);
        } catch (Exception ex) {
            throw new BusinessException("Can't save project to database: " + ex.getMessage());
        }
    }

    @Override
    public void deleteProject(String labId, String projectId) {
        Laboratory laboratory = laboratoryRepository.findById(labId)
                .orElseThrow(()-> new BusinessException(ResponseStatusEnum.BAD_REQUEST, "Laboratory ID not exist when delete project: "+ labId));

        List<Project> projects = laboratory.getProjects();
        String accountId = userInfoService.getAccountId();
        Project project = projects.stream()
                .filter(v -> v.getProjectId().equals(projectId))
                .findAny()
                .orElseThrow(() -> new BusinessException(ResponseStatusEnum.BAD_REQUEST, "Project Id not exist"));
        MemberInfo memberInfo = project.getMembers().stream()
                .filter(v -> v.getAccountId().equals(accountId))
                .findAny()
                .orElseThrow(() -> new BusinessException(ResponseStatusEnum.BAD_REQUEST, "Account Id not in project member"));
        if(!memberInfo.getRole().equals("OWNER")){
            throw new BusinessException(ResponseStatusEnum.BAD_REQUEST, "You don't have permission to delete project");
        }
        if(projects.removeIf(v -> v.getProjectId().equals(projectId))){
            log.info("Delete success");
        }else{
            log.error("Can't delete project");
        }
        laboratory.setProjects(projects);
        try {
            laboratoryRepository.save(laboratory);
        } catch (Exception ex) {
            throw new BusinessException("Can't update laboratory in database");
        }
        try {
            projectRepository.deleteById(projectId);
            log.info("Delete project success: {}", project);
        } catch (Exception ex) {
            throw new BusinessException("Can't delete project in database");
        }
    }

    @Override
    public PageableResponse<GetProjectResponse> getProjectByCondition(_GetProjectRequest request) {
        Query query = new Query();

        if (Objects.nonNull(request.getProjectId())) {
            query.addCriteria(Criteria.where("_id").is(request.getProjectId()));
        }
        if (Objects.nonNull(request.getProjectName())) {
            query.addCriteria(Criteria.where("project_name").regex(request.getProjectName()));
        }

        if (Objects.nonNull(request.getDescription())) {
            query.addCriteria(Criteria.where("description").regex(request.getDescription()));
        }

        query.addCriteria(Criteria.where("start_date").gte(request.getStartDateFrom()).lte(request.getStartDateTo()));

        query.addCriteria(Criteria.where("to_date").gte(request.getToDateFrom()).lte(request.getToDateTo()));

        BaseMongoRepository.addCriteriaWithAuditable(query, request);

        Long totalElements = mongoTemplate.count(query, Project.class);

        BaseMongoRepository.addCriteriaWithPageable(query, request);
        BaseMongoRepository.addCriteriaWithSorted(query, request);

        List<Project> projects = mongoTemplate.find(query, Project.class);

        List<GetProjectResponse> getProjectResponses = projects.stream().map(this::convertProjectToGetProjectResponse).collect(Collectors.toList());

        return new PageableResponse<>(request, totalElements, getProjectResponses);
    }

    @Override
    public PageableResponse<GetProjectResponse> getProjectByLaboratoryId(String labId, String memberId) {
        Laboratory laboratory = laboratoryRepository.findById(labId)
                .orElseThrow(()-> new BusinessException(ResponseStatusEnum.BAD_REQUEST, "Lab ID not exist"));
        MemberInfo memberInfo = memberInfoRepository.findById(memberId)
                .orElseThrow(()-> new BusinessException(ResponseStatusEnum.BAD_REQUEST, "Member ID not exist"));
        List<Project> projects = laboratory.getProjects();
        String accountId = memberInfo.getAccountId();
        List<Project> listProjectContainMember = projects.stream().filter(v -> v.getMembers().stream().anyMatch(s -> s.getAccountId().equals(accountId))).collect(Collectors.toList());
        List<GetProjectResponse> getProjectResponses = listProjectContainMember.stream().map(this::convertProjectToGetProjectResponse).collect(Collectors.toList());

        return new PageableResponse<>(getProjectResponses);
    }

    @Override
    public PageableResponse<GetMemberResponse> getMemberInProject(String projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ResponseStatusEnum.BAD_REQUEST, "Project ID not exist when get member in project: "+ projectId));
        List<MemberInfo> memberInfos = project.getMembers();
        List<GetMemberResponse> getMemberResponses = memberInfos.stream().map(this::convertMemberToGetMemberInfoResponse).collect(Collectors.toList());
        return new PageableResponse<>(getMemberResponses);
    }

    private GetMemberResponse convertMemberToGetMemberInfoResponse(MemberInfo memberInfo) {
        return GetMemberResponse.builder()
                .memberId(memberInfo.getMemberId())
                .role(memberInfo.getRole())
                .userInfo(UserInfoResponse.builder()
                        .accountId(memberInfo.getAccountId())
                        .userInfo(userInfoService.getUserInfo(memberInfo.getAccountId()))
                        .build())
                .build();
    }

    private GetProjectResponse convertProjectToGetProjectResponse(Project project) {
        return GetProjectResponse.builder()
                .projectId(project.getProjectId())
                .projectName(project.getProjectName())
                .description(project.getDescription())
                .members(project.getMembers().size())
                .build();
    }

    @Override
    public GetProjectDetailResponse getProjectDetailByProjectId(String projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ResponseStatusEnum.BAD_REQUEST, "Project ID not exist when get project detail: "+ projectId));
        String accountId = userInfoService.getAccountId();
        MemberInfo memberInfo = project.getMembers().stream()
                .filter(v -> v.getAccountId().equals(accountId))
                .findFirst()
                .orElse(null);
        return GetProjectDetailResponse.builder()
                .projectId(project.getProjectId())
                .projectName(project.getProjectName())
                .description(project.getDescription())
                .memberInfo(new MemberInfoResponse(memberInfo))
                .ownerBy(new MemberInfoResponse(project.getOwnerBy()))
                .members(project.getMembers().stream()
                        .map(this::convertMemberToMemberInfoResponse)
                        .collect(Collectors.toList()))
                .createdBy(UserInfoResponse.builder()
                        .accountId(project.getCreatedBy())
                        .userInfo(userInfoService.getUserInfo(project.getCreatedBy()))
                        .build())
                .createdDate(project.getCreatedDate())
                .lastModifiedBy(UserInfoResponse.builder()
                        .accountId(project.getLastModifiedBy())
                        .userInfo(userInfoService.getUserInfo(project.getLastModifiedBy()))
                        .build())
                .lastModifiedDate(project.getLastModifiedDate())
                .build();
    }

    @Override
    public void removeMemberFromProject(String projectId, String memberId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ResponseStatusEnum.BAD_REQUEST, "Project id not found"));

        List<MemberInfo> memberInfos = project.getMembers();
        Optional<Laboratory> lab = laboratoryRepository.findAll().stream().filter(v->v.getProjects().stream().anyMatch(t->t.getProjectId().equals(projectId))).findFirst();
        if (lab.isEmpty()) {
            throw new BusinessException(ResponseStatusEnum.BAD_REQUEST, "Project not in any lab");
        }
        if (memberInfos.size() == 1) {
            deleteProject(lab.get().getLaboratoryId(), projectId);
        }

        Optional<MemberInfo> member = memberInfos.stream().filter(v -> v.getMemberId().equals(memberId)).findFirst();

        if (member.isEmpty()) {
            throw new BusinessException(ResponseStatusEnum.BAD_REQUEST, "Member id not found removeMemberFromProject");
        }

        memberInfos.removeIf(v->v.getMemberId().equals(memberId));
        if (member.get().getRole().equals(RoleInLaboratoryEnum.OWNER.getRole())) {
            Optional<MemberInfo> newOwner = memberInfos.stream().findFirst();
            newOwner.get().setRole(RoleInLaboratoryEnum.OWNER.getRole());
            try {
                memberInfoRepository.save(newOwner.get());
                log.info("Save new owner in database success");
            } catch (Exception ex) {
                throw new BusinessException(ResponseStatusEnum.INTERNAL_SERVER_ERROR, "Can't save new owner in database");
            }
            project.setOwnerBy(newOwner.get());
        }

        project.setMembers(memberInfos);
        try {
            projectRepository.save(project);
            log.info("Save project from lab success");
        } catch (Exception ex) {
            throw new BusinessException(ResponseStatusEnum.INTERNAL_SERVER_ERROR, "Can't update project in database after remove member");
        }
        try {
            memberInfoRepository.deleteById(memberId);
            log.info("Remove member in database success");
        } catch (Exception ex) {
            throw new BusinessException(ResponseStatusEnum.INTERNAL_SERVER_ERROR, "Can't remove member in database");
        }
    }

    @Override
    public PageableResponse<GetMemberNotInProjectResponse> getMemberNotInProject(GetMemberNotInProjectRequest request) {
        Laboratory laboratory = laboratoryRepository.findById(request.getLabId())
                .orElseThrow(() -> new BusinessException(ResponseStatusEnum.BAD_REQUEST, "Lab Id not exist in get member not in project: "+ request.getLabId()));
        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new BusinessException(ResponseStatusEnum.BAD_REQUEST, "Project Id not exist in get member not in project: "+ request.getProjectId()));
        List<MemberInfo> memberInfoInLab = laboratory.getMembers();
        List<MemberInfo> memberInfoInProject = project.getMembers();
        List<String> accountIdInProject = memberInfoInProject.stream().map(MemberInfo::getAccountId).collect(Collectors.toList());
        List<ObjectId> memberIbInLabNotInProject = memberInfoInLab.stream()
                .filter(v -> !accountIdInProject.contains(v.getAccountId()))
                .map(MemberInfo::getMemberId)
                .map(ObjectId::new)
                .collect(Collectors.toList());
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(memberIbInLabNotInProject));
        BaseMongoRepository.addCriteriaWithSorted(query, request);
        BaseMongoRepository.addCriteriaWithPageable(query, request);
        List<MemberInfo> memberInfoInLabNotInProject = mongoTemplate.find(query, MemberInfo.class);
        List<GetMemberNotInProjectResponse> getMemberNotInProjectResponses = memberInfoInLabNotInProject.stream().map(this::convertMemberNotInProject).filter(Objects::nonNull).collect(Collectors.toList());
        return new PageableResponse<>(request, (long)memberIbInLabNotInProject.size(), getMemberNotInProjectResponses);
    }

    private GetMemberNotInProjectResponse convertMemberNotInProject(MemberInfo memberInfo){
        UserInfo userInfo = userInfoService.getUserInfo(memberInfo.getAccountId());
        if(Objects.nonNull(userInfo)){
            return GetMemberNotInProjectResponse.builder()
                    .memberId(memberInfo.getMemberId())
                    .username(userInfo.getUsername())
                    .email(userInfo.getEmail())
                    .fullName(userInfo.getFullName())
                    .build();
        }else{
            return null;
        }

    }

    private MemberInfoResponse convertMemberToMemberInfoResponse(MemberInfo memberInfo) {
        return MemberInfoResponse.builder()
                .memberId(memberInfo.getMemberId())
                .role(memberInfo.getRole())
                .accountId(memberInfo.getAccountId())
                .userInfo(userInfoService.getUserInfo(memberInfo.getAccountId()))
                .build();
    }
}
