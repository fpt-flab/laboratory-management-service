package vn.edu.fpt.laboratory.config.kafka.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import vn.edu.fpt.laboratory.dto.event.GenerateProjectAppEvent;
import vn.edu.fpt.laboratory.exception.BusinessException;

import java.util.UUID;

/**
 * @author : Hoang Lam
 * @product : Charity Management System
 * @project : Charity System
 * @created : 05/12/2022 - 11:11
 * @contact : 0834481768 - hoang.harley.work@gmail.com
 **/
@Service
public class GenerateProjectAppProducer extends Producer{

    private final ObjectMapper objectMapper;

    @Autowired
    public GenerateProjectAppProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        super(kafkaTemplate);
        this.objectMapper = objectMapper;
    }

    public void sendMessage(GenerateProjectAppEvent event){
        try {
            String value = objectMapper.writeValueAsString(event);
            super.sendMessage("flab.project.generate-app", UUID.randomUUID().toString(), value);
        } catch (JsonProcessingException e) {
            throw new BusinessException("Can't convert Create workspace event to String: "+ e.getMessage());
        }
    }
}
