package org.gable.blendata.nextmove.client.mapper;

import org.gable.blendata.nextmove.client.dto.TaskResponse;
import org.gable.blendata.nextmove.shared.constant.FileStatus;
import org.gable.blendata.nextmove.shared.entity.TransferHistory;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface TransferHistoryMapper {

    default TaskResponse mapToTaskResponseWithSourceFiles(List<TransferHistory> transferHistories) {
        if (transferHistories == null || transferHistories.isEmpty()) {
            return new TaskResponse();
        }

        String taskId = transferHistories.get(0).getTaskId();

        TaskResponse response = new TaskResponse();
        response.setTaskId(taskId);

        Set<TaskResponse.SourceFile> sourceFiles = transferHistories.stream()
                .collect(Collectors.toMap(
                        h -> extractFileName(h.getFilePath()),
                        h -> {
                            TaskResponse.SourceFile sf = new TaskResponse.SourceFile();
                            sf.setFileName(extractFileName(h.getFilePath()));
                            sf.setStatus(mapStatus(h.getStatus()));
                            return sf;
                        },
                        (existing, replacement) -> existing // ถ้าชื่อไฟล์ซ้ำ ใช้ตัวแรก
                ))
                .values()
                .stream()
                .collect(Collectors.toSet());

        response.setSourceFiles(sourceFiles.stream()
                .map(TaskResponse.SourceFile::getFileName)
                .collect(Collectors.toSet()));
        response.setNumberOfFiles(sourceFiles.size());

        return response;
    }

    default String extractFileName(String filePath) {
        if (filePath == null) return "";
        return filePath.substring(filePath.lastIndexOf("/") + 1);
    }

    default FileStatus mapStatus(String status) {
        if (status == null) return FileStatus.UNKNOWN;

        switch (status.toUpperCase()) {
            case "SUCCESS":
                return FileStatus.SUCCESS;
            case "FAILED":
                return FileStatus.FAILED;
            case "PROCESSING":
                return FileStatus.PROCESSING;
            default:
                return FileStatus.UNKNOWN;
        }
    }
}
