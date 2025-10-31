package org.gable.blendata.nextmove.client.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.gable.blendata.nextmove.client.config.AppConfig;
import org.gable.blendata.nextmove.client.dto.AllTaskDTO;
import org.gable.blendata.nextmove.client.dto.TaskDTO;
import org.gable.blendata.nextmove.client.job.TransferJob;
import org.gable.blendata.nextmove.shared.constant.AppConst;
import org.gable.blendata.nextmove.shared.exception.InvalidFormatException;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ScheduleJobService {

    private final Scheduler scheduler;
    private final AppConfig appConfig;

    public List<String> getAllJobs() throws SchedulerException {
        Set<JobKey> jobKeys = scheduler.getJobKeys(GroupMatcher.anyJobGroup());
        return jobKeys.stream()
                .map(jobKey -> String.format("group = %s , name = %s ", jobKey.getGroup(), jobKey.getName()))
                .collect(Collectors.toList());
    }

    public void pauseJob(String jobGroup, String jobName) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(jobName, jobGroup);
        scheduler.pauseJob(jobKey);
    }

    public void reloadAllTask() throws SchedulerException {
        Set<JobKey> jobKeys = scheduler.getJobKeys(GroupMatcher.anyJobGroup());
        scheduler.deleteJobs(new ArrayList<>(jobKeys));
        Yaml yaml = new Yaml(new Constructor(AllTaskDTO.class));
        try(InputStream inputStream = new FileInputStream(appConfig.getTaskConfigurePath())) {
            AllTaskDTO allTasks = yaml.load(inputStream);
            for(TaskDTO task : allTasks.getTaskConfig().getTasks()) {
                scheduleJob(task);
            }
        } catch (IOException e) {
            throw new RuntimeException(String.format("Reload tasks incompletely"));
        }
    }

    public void reloadTask(String taskId) throws SchedulerException {
        Yaml yaml = new Yaml(new Constructor(AllTaskDTO.class));
        try(InputStream inputStream = new FileInputStream(appConfig.getTaskConfigurePath())) {
            AllTaskDTO allTasks = yaml.load(inputStream);
            Optional<TaskDTO> taskDto = allTasks.getTaskConfig().getTasks().stream()
                    .filter(taskDTO -> taskDTO.getId().equalsIgnoreCase(taskId))
                    .findFirst();
            if(taskDto.isPresent()){
                String jobGroup = taskDto.get().getType().name().toLowerCase() + "_group";
                String jobName = taskDto.get().getId() + "_job";
                scheduler.deleteJob(JobKey.jobKey(jobName, jobGroup));
                scheduleJob(taskDto.get());
            }
        } catch (IOException e) {
            throw new RuntimeException(String.format("Cannot reload task id %s", taskId));
        }
    }

    public void resumeJob(String jobGroup, String jobName) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(jobName, jobGroup);
        scheduler.resumeJob(jobKey);
    }

    public void scheduleJob(TaskDTO taskDto) {
        String jobGroup = taskDto.getType().name().toLowerCase() + "_group";
        String jobName = taskDto.getId() + "_job";
        JobKey jobKey = JobKey.jobKey(jobName, jobGroup);
        String cronExpression = taskDto.getCronExpression();
        try {

            if (scheduler.checkExists(JobKey.jobKey(jobName, jobGroup))) {
                scheduler.deleteJob(jobKey);
            }
            if (null != cronExpression && !CronExpression.isValidExpression(cronExpression)) {
                throw new InvalidFormatException(String.format("Cron Expression %s is invalid", cronExpression));
            }
            JobDataMap jobDataMap = new JobDataMap();
            jobDataMap.put("data", taskDto);

            JobDetail jobDetail = JobBuilder.newJob(TransferJob.class)
                    .setJobData(jobDataMap)
                    .withIdentity(jobName, jobGroup)
                    .storeDurably()
                    .build();
            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(jobName + "_trigger", jobName + "_trigger_group")
                    .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression).withMisfireHandlingInstructionDoNothing())
                    .build();
            Date nextFireTime = scheduler.scheduleJob(jobDetail, trigger);
            log.info("{} : Create schedule job name '{}'", AppConst.PREFIX_LOG, jobName);
        } catch (Exception e) {
            log.error("{} !!!Error : Cannot schedule job name '{}'", AppConst.PREFIX_LOG, jobName, e);
        }
    }

    public void deleteJob(String jobGroup, String jobName) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(jobName, jobGroup);
        scheduler.deleteJob(jobKey);
    }

    public void deleteAllJob() throws SchedulerException {
        Set<JobKey> jobKeys = scheduler.getJobKeys(GroupMatcher.anyJobGroup());
        scheduler.deleteJobs(new ArrayList<>(jobKeys));
    }


}
