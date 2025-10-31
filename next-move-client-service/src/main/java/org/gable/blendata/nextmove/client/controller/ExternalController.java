package org.gable.blendata.nextmove.client.controller;


import lombok.RequiredArgsConstructor;
import org.gable.blendata.nextmove.client.dto.TaskDTO;
import org.gable.blendata.nextmove.client.dto.TransferHistoryView;
import org.gable.blendata.nextmove.client.service.MainTaskService;
import org.gable.blendata.nextmove.client.service.TransferHistoryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/external")
@RequiredArgsConstructor
public class ExternalController {

    private final TransferHistoryService transferHistoryService;

    private final MainTaskService mainTaskService;

    @PostMapping("/execute/job")
    public void executeJob(@RequestBody TaskDTO taskDTO) throws Exception {
        mainTaskService.process(taskDTO);
    }

    @PutMapping("/remove/taskId/{taskId}")
    public void removeTaskId(@PathVariable String taskId) {
        mainTaskService.removeTaskIdFromMap(taskId);
    }

    @GetMapping("/taskHistory/{taskId}")
    public List<TransferHistoryView> findTaskHistoryByTaskId(@PathVariable  String taskId) {
        return transferHistoryService.findTransferHistoryViewByTaskId(taskId);
    }

}
