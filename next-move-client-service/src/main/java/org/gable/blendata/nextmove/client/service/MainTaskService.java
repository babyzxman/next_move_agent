package org.gable.blendata.nextmove.client.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.gable.blendata.nextmove.client.adapter.FileSystemAdapter;
import org.gable.blendata.nextmove.client.config.AppConfig;
import org.gable.blendata.nextmove.client.dto.CreateTaskResponse;
import org.gable.blendata.nextmove.client.dto.TaskDTO;
import org.gable.blendata.nextmove.client.service.connection.TaskAwareFileSystemService;
import org.gable.blendata.nextmove.shared.constant.AppConst;
import org.gable.blendata.nextmove.shared.constant.ServiceConst.ServiceId;
import org.gable.blendata.nextmove.shared.constant.TaskConst;
import org.gable.blendata.nextmove.shared.exception.BusinessException;
import org.gable.blendata.nextmove.shared.exception.GeneralException;
import org.gable.blendata.nextmove.shared.exception.NotFoundException;
import org.gable.blendata.nextmove.shared.util.DateUtil;
import org.gable.blendata.nextmove.shared.util.ErrorUtil;
import org.gable.blendata.nextmove.shared.util.StringUtil;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.gable.blendata.nextmove.shared.constant.TaskConst.TaskType.NONE_CONTROL;
import static org.gable.blendata.nextmove.shared.constant.TaskConst.TaskType.ZERO_SIZE_CONTROL;

@Slf4j
@Service
@RequiredArgsConstructor
public class MainTaskService {

    private final RestTemplate restTemplate;
    private final DiscoveryClient discoveryClient;
    private final AppConfig appConfig;
    private final ListFileService listFileService;
    private final ScheduleJobService scheduleJobService;
    private final TransferService transferService;
    private final TaskAwareFileSystemService taskAwareFileSystemService;

    public void create(List<TaskDTO> taskDtos) {
        //...Create schedule job
        for (TaskDTO taskDto : taskDtos) {
            scheduleJobService.scheduleJob(taskDto);
        }
    }

    public void removeTaskIdFromMap(String taskId) {
        taskAwareFileSystemService.closeConnectionWithRemoveMap(taskId);
    }

    public CreateTaskResponse process(TaskDTO taskDto) {
        CreateTaskResponse.CreateTaskResponseBuilder createTaskResponseBuilder = CreateTaskResponse.builder();
        taskAwareFileSystemService.createFileSystemAdapterInCaseKeyNull(taskDto);
        try {
            taskDto.getRootPath().setDestination(StringUtil.replaceVariableCurrentDate(LocalDateTime.now(), taskDto.getRootPath().getDestination()).toString());

            String startExecuteTime = DateUtil.convertToString(LocalDateTime.now(), DateUtil.DD_sl_MM_sl_YYYY_HH_mm_ss);
            log.info("{}...Start process {}", AppConst.PREFIX_LOG, taskDto.getRootPath());
            String serviceId = NONE_CONTROL == taskDto.getType() ? ServiceId.NONE_CONTROL.val()
                    : ZERO_SIZE_CONTROL == taskDto.getType() ? ServiceId.ZERO_CONTROL.val()
                    : "";
            //...Check the promptness of the service nodes.
            int numNodes = checkServiceNodes(taskDto, serviceId);
            if (numNodes == 0) return null; //...Skip to the next round.

            //...List files of tasks
            List<String> fileSourcePaths = listFile(taskDto);
            //...P'Ban request to print all matching file paths
            log.info("{} task = {}, matching file source paths = {}"
                    , AppConst.PREFIX_LOG
                    , taskDto.getId()
                    , fileSourcePaths.stream()
                            .collect(Collectors.joining("\n")));

            if (CollectionUtils.isEmpty(fileSourcePaths)){
                throw new NotFoundException(String.format("No files are found for task = %s, root path = %s"
                                , taskDto.getId()
                                , taskDto.getRootPath().getSource()));
            }

            //...Divide files according to the number of service nodes
            List<List<String>> groupFilePaths = divideFiles(fileSourcePaths, numNodes);
            log.debug("{} Size of groupFilePath = {}", AppConst.PREFIX_LOG, groupFilePaths.size());
            //...Distribute tasks to service nodes asynchronously
            List<CompletableFuture<ResponseEntity<String>>> futures = new ArrayList<>();
            for (List<String> groupFilePath : groupFilePaths) {
                futures.add(transferService.transfer(taskDto, groupFilePath, serviceId));
            }
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            allFutures.get();  //...Wait each service mark processing status
            log.info("{}[MainTaskService] Task {} is mark processing successfully", AppConst.PREFIX_LOG, taskDto.getId());

            return createTaskResponseBuilder
                    .taskId(taskDto.getId())
                    .numberOfFiles(fileSourcePaths.size())
                    .build();

        }catch(NotFoundException e) {
            String errorNo = ErrorUtil.generateErrorNo(appConfig.getAppId());
            log.error("{} !!!ErrorNo({}) : process task = {}, root path = {}"
                    , AppConst.PREFIX_LOG
                    , errorNo
                    , taskDto.getId()
                    , taskDto.getRootPath().getSource()
                    , e);
            throw new BusinessException(String.format("Error(%s) : process task = %s, root path = %s, error = %s (%s)"
                    , errorNo
                    , taskDto.getId()
                    , taskDto.getRootPath().getSource()
                    , e.getMessage()
                    , ErrorUtil.getCauseClassInfo(e.getStackTrace())), HttpStatus.NOT_FOUND.value());
        }catch(Exception e) {
            String errorNo = ErrorUtil.generateErrorNo(appConfig.getAppId());
            log.error("{} !!!ErrorNo({}) : process task = {}, root path = {}"
                    , AppConst.PREFIX_LOG
                    , errorNo
                    , taskDto.getId()
                    , taskDto.getRootPath().getSource()
                    , e);
            throw new GeneralException(String.format("Error(%s) : process task = %s, root path = %s, error = %s (%s)"
                    , errorNo
                    , taskDto.getId()
                    , taskDto.getRootPath().getSource()
                    , e.getMessage()
                    , ErrorUtil.getCauseClassInfo(e.getStackTrace())), HttpStatus.INTERNAL_SERVER_ERROR.value());

        }
    }

    private Integer checkServiceNodes(TaskDTO taskDto, String serviceId) throws InterruptedException, IOException {
        int numNodes = 0;
        if (StringUtils.isEmpty(serviceId)) {
            throw new IllegalArgumentException("[Blendata] !!! Please specify type of task config");
        }
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
        if (CollectionUtils.isEmpty(instances)) {
            log.warn("!!!Waiting for {} registration", serviceId);
        }
        numNodes = instances.size();
        return numNodes;
    }

    private List<String> listFile(TaskDTO taskDTO) throws IOException {
        List<String> filteredSourcePaths = new ArrayList<>();
        LocalDateTime executeTime = LocalDateTime.now();
        String sourcePathString = StringUtil.replaceVariableCurrentDate(LocalDateTime.now(), taskDTO.getRootPath().getSource()).toString();
        taskDTO.getRootPath().setSource(sourcePathString);
        String destination = StringUtil.replaceVariableCurrentDate(LocalDateTime.now(), taskDTO.getRootPath().getDestination()).toString();
        Path sourcePath = new Path(sourcePathString);
        List<String> wildcardPatterns = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(taskDTO.getWildcardPatterns())) {
            wildcardPatterns = taskDTO.getWildcardPatterns().stream()
                    .map(w -> StringUtil.replaceVariableCurrentDate(LocalDateTime.now(), w).toString())
                    .collect(Collectors.toList());
        }
        LocalDateTime afterDate = taskDTO.getAfterDate() != null ? DateUtil.convertToLocalDateTime(taskDTO.getAfterDate(), DateUtil.DD_sl_MM_sl_YYYY_HH_mm_ss)
                : taskDTO.getBeforeCurrentDateInDays() != null ? DateUtil.calculateDateBySubtractingDays(taskDTO.getBeforeCurrentDateInDays())
                : taskDTO.getBeforeCurrentDateInHours() != null ? DateUtil.calculateDateBySubtractingHours(taskDTO.getBeforeCurrentDateInHours())
                : null;
        FileSystemAdapter fileSystemAdapter = taskAwareFileSystemService.getFileSystemAdapter(taskDTO.getId());
        try {
            switch (taskDTO.getType()) {
                case NONE_CONTROL:
                    filteredSourcePaths = listFileService.listNoneCtrlFile(fileSystemAdapter
                            , sourcePath.toString()
                            , taskDTO.getFilesPerRound()
                            , afterDate
                            , taskDTO.getSrcExtensions()
                            , wildcardPatterns
                            , taskDTO.isOverwrite()
                            , taskDTO.getMoveType()
                            , taskDTO.getId()
                            , taskDTO.getSourceType()
                            , taskDTO.getSourceProperties() == null ?
                                    null
                                    : taskDTO.getSourceProperties().get(TaskConst.SourceProperties.HOST.getPropertyName()).toString(),
                            destination
                    );
                    break;
                case ZERO_SIZE_CONTROL:
                    filteredSourcePaths = listFileService.listZeroSizeCtrlFile(fileSystemAdapter
                            , sourcePath.toString()
                            , taskDTO.getFilesPerRound()
                            , afterDate
                            , taskDTO.getCtrlExtensions()
                            , taskDTO.getSrcExtensions()
                            , wildcardPatterns
                            , taskDTO.isOverwrite()
                            , taskDTO.getMoveType()
                            , taskDTO.getId()
                            , taskDTO.getSourceType()
                            , taskDTO.getSourceProperties() == null ?
                                    null
                                    : taskDTO.getSourceProperties().get(TaskConst.SourceProperties.HOST.getPropertyName()).toString(),
                            taskDTO.getRootPath().getCtrlPath(),taskDTO.getCtrlFilePatterns(),
                            destination
                    );
            }
        } finally {
            taskAwareFileSystemService.closeConnection(taskDTO.getId(), fileSystemAdapter);
        }
        return filteredSourcePaths;
    }

    public List<List<String>> divideFiles(List<String> filePaths, int numberOfGroups) {
        List<List<String>> groupFilePaths = new ArrayList<>();

        int size = filePaths.size();
        int baseGroupSize = size / numberOfGroups;
        int remainder = size % numberOfGroups;

        int start = 0;
        for (int i = 0; i < numberOfGroups; i++) {
            int end = start + baseGroupSize + (remainder > 0 ? 1 : 0);
            groupFilePaths.add(new ArrayList<>(filePaths.subList(start, Math.min(end, size))));
            start = end;
            if (remainder > 0) remainder--;
        }

        return groupFilePaths;
    }
}
