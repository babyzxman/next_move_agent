package org.gable.blendata.nextmove.client.service.connection;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.fs.FileSystem;
import org.gable.blendata.nextmove.client.adapter.FileSystemAdapter;
import org.gable.blendata.nextmove.client.config.HadoopConfig;
import org.gable.blendata.nextmove.client.config.TaskConfig;
import org.gable.blendata.nextmove.client.dto.TaskDTO;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskAwareFileSystemService {

    private final TaskConfig taskConfig;
    private final ConnectionManagerFactory connectionManagerFactory;
    private final Map<String, ConnectionManager> connectionManagers = new ConcurrentHashMap<>();
    private final Map<String, TaskDTO> taskConfigs = new ConcurrentHashMap<>();
    private final HadoopConfig hadoopConf;
    private final Map<String, FileSystem> fileSystems;

    @PostConstruct
    public void initialize() {
        for (TaskDTO taskDTO : taskConfig.getTasks()) {
            addTask(taskDTO);
        }
    }

    public void addTask(TaskDTO taskDTO) {
        hadoopConf.registerFileSystem(fileSystems, taskDTO.getRootPath().getSource());
        hadoopConf.registerFileSystem(fileSystems, taskDTO.getRootPath().getDestination());

        ConnectionManager connectionManager = connectionManagerFactory.createConnectionManager(taskDTO);
        connectionManagers.put(taskDTO.getId(), connectionManager);
        taskConfigs.put(taskDTO.getId(), taskDTO);
        log.info("Initialized connection manager for task {} with source type {}",
                taskDTO.getId(), taskDTO.getSourceType());
    }

    public FileSystemAdapter getFileSystemAdapter(String taskId) throws IOException {
        ConnectionManager connectionManager = connectionManagers.get(taskId);
        if (connectionManager == null) {
            throw new IllegalArgumentException("No connection manager found for task: " + taskId);
        }
        return connectionManager.createConnection();
    }

    public void createFileSystemAdapterInCaseKeyNull(TaskDTO taskDTO) {
        ConnectionManager connectionManager = connectionManagers.get(taskDTO.getId());
        if(connectionManager == null) {
            connectionManager = connectionManagerFactory.createConnectionManager(taskDTO);
            connectionManagers.put(taskDTO.getId(), connectionManager);
        }
    }

    public void closeConnection(String taskId, FileSystemAdapter adapter) throws IOException {
        ConnectionManager connectionManager = connectionManagers.get(taskId);
        if (connectionManager != null) {
            connectionManager.closeConnection(adapter);
        }
    }

    public void closeConnectionWithRemoveMap(String taskId) {
        connectionManagers.remove(taskId);
    }

    public TaskDTO getTaskConfig(String taskId) {
        return taskConfigs.get(taskId);
    }
}
