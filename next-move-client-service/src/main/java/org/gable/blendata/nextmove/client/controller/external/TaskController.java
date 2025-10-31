package org.gable.blendata.nextmove.client.controller.external;

import lombok.RequiredArgsConstructor;
import org.gable.blendata.nextmove.client.dto.ApiResponse;
import org.gable.blendata.nextmove.client.dto.CreateTaskResponse;
import org.gable.blendata.nextmove.client.dto.TaskDTO;
import org.gable.blendata.nextmove.client.dto.TaskResponse;
import org.gable.blendata.nextmove.client.service.MainTaskService;
import org.gable.blendata.nextmove.client.service.TransferHistoryService;
import org.gable.blendata.nextmove.client.service.connection.TaskAwareFileSystemService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/external/task")
@RequiredArgsConstructor
public class TaskController {

    private final MainTaskService mainTaskService;
    private final TransferHistoryService transferHistoryService;
    private final TaskAwareFileSystemService taskAwareFileSystemService;

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<CreateTaskResponse>> createTask(@RequestBody TaskDTO taskDTO) {
        taskAwareFileSystemService.addTask(taskDTO);
        return ResponseEntity.ok( ApiResponse.success(mainTaskService.process(taskDTO)));
    }

    @GetMapping("/get/{task-id}")
    public ResponseEntity<ApiResponse<TaskResponse>> getTaskInfo(@PathVariable("task-id") String taskId) {
        return ResponseEntity.ok( ApiResponse.success(transferHistoryService.getTaskInfoByTaskId(taskId)));
    }
}
