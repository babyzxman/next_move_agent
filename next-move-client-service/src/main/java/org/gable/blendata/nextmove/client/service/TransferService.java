package org.gable.blendata.nextmove.client.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.gable.blendata.nextmove.client.config.AppConfig;
import org.gable.blendata.nextmove.client.dto.AllTaskDTO;
import org.gable.blendata.nextmove.client.dto.TaskDTO;
import org.gable.blendata.nextmove.client.dto.TransferHistoryView;
import org.gable.blendata.nextmove.shared.constant.AppConst;
import org.gable.blendata.nextmove.shared.constant.ServiceConst;
import org.gable.blendata.nextmove.shared.constant.TaskConst;
import org.gable.blendata.nextmove.shared.dto.StopTaskRequestWrapper;
import org.gable.blendata.nextmove.shared.dto.TransferRequestWrapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {
    @Qualifier("directRestTemplate")
    private final RestTemplate directRestTemplate;
    private final RestTemplate restTemplate;
    private final DiscoveryClient discoveryClient;
    private final WebClient webClient;
    private final AppConfig appConfig;

    public void requestAllTaskStop() {
        requestAllTaskStop(null, null);
    }

    public void requestAllTaskStop(String jobGroup, String jobName) {
        boolean stopAllTasks = (null == jobGroup && null == jobName);
        Yaml yaml = new Yaml(new Constructor(AllTaskDTO.class));
        List<StopTaskRequestWrapper> noneControlRequest = new ArrayList<>();
        List<StopTaskRequestWrapper> zeroSizeControlRequest = new ArrayList<>();
        try (InputStream inputStream = new FileInputStream(appConfig.getTaskConfigurePath())) {
            AllTaskDTO allTasks = yaml.load(inputStream);
            if(stopAllTasks){
                noneControlRequest = getStopTaskRequestWrappersByType(allTasks.getTaskConfig().getTasks(), TaskConst.TaskType.NONE_CONTROL);
                zeroSizeControlRequest = getStopTaskRequestWrappersByType(allTasks.getTaskConfig().getTasks(), TaskConst.TaskType.ZERO_SIZE_CONTROL);
            }else {
                TaskConst.TaskType taskType = TaskConst.TaskType.valueOf(jobGroup.toUpperCase().replace("_group", ""));
                String taskId = jobName.replace("_job", "");
                if(TaskConst.TaskType.NONE_CONTROL.name().equalsIgnoreCase(jobGroup.replace("_group", ""))) {
                    noneControlRequest = getStopTaskRequestWrappersByTypeAndId(allTasks.getTaskConfig().getTasks(), TaskConst.TaskType.NONE_CONTROL, taskId);
                }else if(TaskConst.TaskType.ZERO_SIZE_CONTROL.name().equalsIgnoreCase(jobGroup.replace("_group", ""))) {
                    zeroSizeControlRequest = getStopTaskRequestWrappersByTypeAndId(allTasks.getTaskConfig().getTasks(), TaskConst.TaskType.ZERO_SIZE_CONTROL, taskId);
                }
            }
        } catch (FileNotFoundException e) {
            log.error("{} !!!Error no task config found for path {}: ", AppConst.PREFIX_LOG, appConfig.getTaskConfigurePath());
            return;
        } catch (IOException e) {
            log.error("{} !!!Error failed to get task ids for path {}: ", AppConst.PREFIX_LOG, appConfig.getTaskConfigurePath(), e);
            return;
        }

        if (!zeroSizeControlRequest.isEmpty()) {
            List<ServiceInstance> zeroControlInstances = discoveryClient.getInstances(ServiceConst.ServiceId.ZERO_CONTROL.val());
            sendStopTaskRequests(zeroControlInstances, zeroSizeControlRequest);
        }

        if (!noneControlRequest.isEmpty()) {
            List<ServiceInstance> noneControlInstances = discoveryClient.getInstances(ServiceConst.ServiceId.NONE_CONTROL.val());
            sendStopTaskRequests(noneControlInstances, noneControlRequest);
        }
    }

    private void sendStopTaskRequests(List<ServiceInstance> instances, List<StopTaskRequestWrapper> requestWrappers) {
        instances.stream()
            .map(instance -> String.format("%s://%s:%d%s",
                    appConfig.getClientUrlScheme(),
                    instance.getHost(),
                    instance.getPort(),
                    ServiceConst.ServiceUrl.STOP_ALL_TASK.val()))
            .forEach(stopTaskUrl -> {
                HttpHeaders headers = new HttpHeaders();
                headers.set("Accept", "application/json");
                HttpEntity<List<StopTaskRequestWrapper>> entity = new HttpEntity<>(requestWrappers, headers);
                log.info("stopTaskUl = {}", stopTaskUrl);
                log.info("request = {}", requestWrappers.stream().map(r-> r.getClientId() + " : " + r.getTaskId()).collect(Collectors.joining()));
                try {
                    directRestTemplate.exchange(stopTaskUrl, HttpMethod.POST, entity, String.class);
                } catch (Exception e) {
                    log.error("{} !!!Error failed to request all task stop for URL {}: ", AppConst.PREFIX_LOG, stopTaskUrl, e);
                }
            });
    }

    private List<StopTaskRequestWrapper> getStopTaskRequestWrappersByType(List<TaskDTO> tasks, TaskConst.TaskType taskType) {
        return tasks.stream()
                .filter(taskDTO -> taskDTO.getType().name().equalsIgnoreCase(taskType.name()))
                .map(taskDTO -> StopTaskRequestWrapper.builder()
                        .taskId(taskDTO.getId())
                        .clientId(appConfig.getAppId())
                        .build())
                .collect(Collectors.toList());
    }

    private List<StopTaskRequestWrapper> getStopTaskRequestWrappersByTypeAndId(List<TaskDTO> tasks, TaskConst.TaskType taskType, String taskId) {
        return tasks.stream()
                .filter(taskDTO -> taskDTO.getType().name().equalsIgnoreCase(taskType.name())
                    && taskDTO.getId().equalsIgnoreCase(taskId)
                )
                .map(taskDTO -> StopTaskRequestWrapper.builder()
                        .taskId(taskDTO.getId())
                        .clientId(appConfig.getAppId())
                        .build())
                .collect(Collectors.toList());
    }

    public CompletableFuture<ResponseEntity<String>> transfer(TaskDTO taskDto, List<String> groupFilePathStr, String serviceId){
        String url = appConfig.getClientUrlScheme() + "://" + serviceId + ServiceConst.ServiceUrl.TRANSFER.val();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/json");
        TransferRequestWrapper body = TransferRequestWrapper.builder()
                .sourceType(taskDto.getSourceType())
                .host(null != taskDto.getSourceProperties()?
                        taskDto.getSourceProperties().get(TaskConst.SourceProperties.HOST.getPropertyName()).toString()
                        : null)
                .sourceProperties(taskDto.getSourceProperties())
                .taskId(taskDto.getId())
                .clientId(appConfig.getAppId())
                .sourceRootPathStr(taskDto.getRootPath().getSource())
                .destinationRootPathStr(taskDto.getRootPath().getDestination())
                .filePathStrs(groupFilePathStr)
                .moveType(taskDto.getMoveType().name())
                .checkFileDelaySeconds(taskDto.getCheckFileDelaySeconds())
                .retry(taskDto.getRetry())
                .checkFileSize(taskDto.isCheckFileSize())
                .overwrite(taskDto.isOverwrite())
                .ctrlExtensions(taskDto.getCtrlExtensions())
                .destinationCtrlRootPathStr(taskDto.getRootPath().getCtrlDestPath())
                .notExtract(Objects.isNull(taskDto.getCompression())? false : taskDto.getCompression().isNotExtract())
                .createTargetZipBaseDir(Objects.isNull(taskDto.getCompression())? false : taskDto.getCompression().isCreateTargetDirectory())
                .filePartitionDate(taskDto.getFilePartitionDate())
                .usedCheckpoint(taskDto.getUsedCheckpoint())
                .downloadToTmpBeforeUpload(taskDto.isDownloadToTmpBeforeUpload())
                .build();
        HttpEntity<TransferRequestWrapper> entity = new HttpEntity<>(body, headers);

        return CompletableFuture.completedFuture(restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                String.class
        ));
    }

}
