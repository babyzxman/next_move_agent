package org.gable.blendata.nextmove.client.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.gable.blendata.nextmove.client.dto.TaskDTO;
import org.gable.blendata.nextmove.client.service.MainTaskService;
import org.gable.blendata.nextmove.shared.constant.AppConst;
import org.gable.blendata.nextmove.shared.util.DateUtil;
import org.quartz.*;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
@DisallowConcurrentExecution
public class TransferJob implements Job {

    private final MainTaskService mainTaskService;
    private final Scheduler scheduler;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
        TaskDTO taskDto = (TaskDTO)jobDataMap.get("data");
        log.info("{}[TransferJob] Task {} start {}", AppConst.PREFIX_LOG, taskDto.getId(), DateUtil.convertToString(LocalDateTime.now(), DateUtil.DD_sl_MM_sl_YYYY_HH_mm_ss));
        try {
            mainTaskService.process(taskDto);
        } catch (Exception e) {
            log.error(e.getMessage(),e);
        }
    }

    private boolean isJobAlreadyRunning(JobKey jobKey) throws SchedulerException {
        try {
            return scheduler.getCurrentlyExecutingJobs().stream()
                    .filter(jobExecutionContext -> jobExecutionContext.getJobDetail().getKey().equals(jobKey))
                    .count() > 1;
        } catch (SchedulerException e) {
            log.error("{} !!!Error get current executing jobs", AppConst.PREFIX_LOG);
            throw e;
        }
    }
}
