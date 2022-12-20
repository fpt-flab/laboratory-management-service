package vn.edu.fpt.laboratory.config.batch.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListenerSupport;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class JobCompletionNotificationListener extends JobExecutionListenerSupport {

    @Override
    public void afterJob(JobExecution jobExecution) {
        if(jobExecution.getStatus() == BatchStatus.COMPLETED){
            log.info("Job: {} finish at: {}", jobExecution.getJobInstance().getJobName(), jobExecution.getEndTime());
        }else{
            log.error("Job: {} failed at: {}", jobExecution.getJobInstance().getJobName(), jobExecution.getEndTime());
        }
    }
}