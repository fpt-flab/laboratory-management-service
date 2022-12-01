package vn.edu.fpt.laboratory.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.laboratory.entity.AppConfig;

import java.util.Optional;

/**
 * @author : Hoang Lam
 * @product : Charity Management System
 * @project : Charity System
 * @created : 01/12/2022 - 06:52
 * @contact : 0834481768 - hoang.harley.work@gmail.com
 **/
@Repository
public interface AppConfigRepository extends MongoRepository<AppConfig, String> {

    Optional<AppConfig> findByConfigKey(String configKey);
}
