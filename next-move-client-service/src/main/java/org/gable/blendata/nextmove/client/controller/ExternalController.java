package org.gable.blendata.nextmove.client.controller;


import lombok.RequiredArgsConstructor;
import org.gable.blendata.nextmove.client.dto.TaskDTO;
import org.gable.blendata.nextmove.client.dto.TransferHistoryView;
import org.gable.blendata.nextmove.client.service.MainTaskService;
import org.gable.blendata.nextmove.client.service.TransferHistoryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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

    @GetMapping("/taskHistory/detail/{taskId}/{page}/{pageSize}")
    public List<TransferHistoryView> findTaskHistoryByTaskId(
            @PathVariable  String taskId, @PathVariable Integer page, @PathVariable Integer pageSize) {
        return transferHistoryService.findTransferHistoryViewByTaskId(taskId,page,pageSize);
    }

    @GetMapping("/taskHistory/page/{taskId}/{pageSize}")
    public Integer findTaskHistoryPageSize(@PathVariable String taskId, @PathVariable Integer pageSize) {
        return transferHistoryService.findTransferHistoryViewPageSizeByTaskId(taskId,pageSize);
    }

    @GetMapping("/taskHistory/status/{taskId}")
    public Boolean checkStatusOfTask(@PathVariable String taskId) {
        return transferHistoryService.findTransferHistoryStatus(taskId);
    }

    @PostMapping("/test")
    public Boolean test(@RequestBody Map<String,String> body) {
        Pattern changePattern = globToRegexPattern(body.get("pattern"));
        return changePattern.matcher(body.get("fileName")).matches();
    }

    private Pattern globToRegexPattern(String glob) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*':
                    regex.append(".*");
                    break;
                case '?':
                    regex.append(".");
                    break;
                case '[':
                    regex.append("[");
                    break;
                case ']':
                    regex.append("]");
                    break;
                case '{':
                    regex.append("(");
                    break;
                case '}':
                    regex.append(")");
                    break;
                case ',':
                    if (isInsideBraces(glob, i)) {
                        regex.append("|");
                    } else {
                        regex.append(",");
                    }
                    break;
                default:
                    if (isSpecialRegexChar(c)) {
                        regex.append("\\").append(c);
                    } else {
                        regex.append(c);
                    }
                    break;
            }
        }
        return Pattern.compile(regex.toString());
    }

    private boolean isInsideBraces(String glob, int index) {
        int braceCount = 0;
        for (int i = 0; i < index; i++) {
            if (glob.charAt(i) == '{') {
                braceCount++;
            } else if (glob.charAt(i) == '}') {
                braceCount--;
            }
        }
        return braceCount > 0;
    }

    private boolean isSpecialRegexChar(char c) {
        return "\\^$.|+()".indexOf(c) != -1;
    }

}
