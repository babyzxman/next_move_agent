package org.gable.blendata.nextmove.client.controller;

import lombok.RequiredArgsConstructor;
import org.gable.blendata.nextmove.client.dto.TaskDTO;
import org.gable.blendata.nextmove.client.dto.TransferHistoryView;
import org.gable.blendata.nextmove.client.service.MainTaskService;
import org.gable.blendata.nextmove.client.service.ScheduleJobService;
import org.gable.blendata.nextmove.client.service.TransferHistoryService;
import org.gable.blendata.nextmove.client.service.TransferService;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/job")
@RequiredArgsConstructor
public class ScheduleJobController {

    private final ScheduleJobService scheduleJobService;

    private final TransferService transferService;

    @GetMapping("/get/all")
    public ResponseEntity<List<String>> getAllJobs() throws SchedulerException {
        return ResponseEntity.ok(scheduleJobService.getAllJobs());
    }

    @DeleteMapping("/delete/{jobGroup}/{jobName}")
    public ResponseEntity<String> deleteByJob(@PathVariable("jobGroup")String jobGroup, @PathVariable("jobName")String jobName) throws SchedulerException {
        scheduleJobService.deleteJob(jobGroup, jobName);
        transferService.requestAllTaskStop(jobGroup, jobName);
        return ResponseEntity.ok(String.format("Delete job %s completely", jobName));
    }

    @DeleteMapping("/delete/all")
    public ResponseEntity<String> deleteAllJob() throws SchedulerException {
        scheduleJobService.deleteAllJob();
        transferService.requestAllTaskStop();
        return ResponseEntity.ok(String.format("Delete all jobs is completely"));
    }

    @PostMapping("/pause/{jobGroup}/{jobName}")
    public ResponseEntity<String> pauseJob(@PathVariable("jobGroup")String jobGroup, @PathVariable("jobName")String jobName) throws SchedulerException {
        scheduleJobService.pauseJob(jobGroup, jobName);
        return ResponseEntity.ok(String.format("Pause job %s completely", jobName));
    }

    @PostMapping("/resume/{jobGroup}/{jobName}")
    public ResponseEntity<String> resumeJob(@PathVariable("jobGroup")String jobGroup, @PathVariable("jobName")String jobName) throws SchedulerException {
        scheduleJobService.resumeJob(jobGroup, jobName);
        return ResponseEntity.ok(String.format("Resume job %s completely", jobName));
    }

    @PostMapping("/reload/task/all")
    public ResponseEntity<String> reloadAllTask() throws SchedulerException {
        scheduleJobService.reloadAllTask();
        return ResponseEntity.ok(String.format("Reload all tasks completely"));
    }

    @PostMapping("/reload/task/{taskId}")
    public ResponseEntity<String> reloadAllTask(@PathVariable("taskId")String taskId) throws SchedulerException {
        scheduleJobService.reloadTask(taskId);
        return ResponseEntity.ok(String.format("Reload task id %s completely", taskId));
    }

}
