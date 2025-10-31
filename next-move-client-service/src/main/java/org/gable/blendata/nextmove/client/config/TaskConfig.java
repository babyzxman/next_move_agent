package org.gable.blendata.nextmove.client.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.gable.blendata.nextmove.client.dto.TaskDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Configuration
@Data
public class TaskConfig {
    private List<TaskDTO> tasks = new ArrayList<>();
    @Value("${const.path.config.task:classpath:/task-configure.yml}")
    private String configFile;  //...Support multiple files with wildcard pattern

    @PostConstruct
    public void loadTaskConfigs() throws IOException {
        if (configFile == null || configFile.trim().isEmpty()) {
            log.warn("Config task file not specified");
            return;
        }

        String pattern = "file:" + configFile;
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources(pattern);
        log.info("Found {} task config files matching pattern: {}", resources.length, pattern);

        for (Resource resource : resources) {
            try {
                List<TaskDTO> fileTasks = loadTasksFromResource(resource);
                this.tasks.addAll(fileTasks);
                log.info("Loaded {} tasks from {}", fileTasks.size(), resource.getFilename());
            } catch (Exception e) {
                log.error("Failed to load tasks from {}", resource.getFilename(), e);
            }
        }

        log.info("Total tasks loaded: {}", this.tasks.size());
        validateTaskIds();
    }

    @SuppressWarnings("unchecked")
    private List<TaskDTO> loadTasksFromResource(Resource resource) throws IOException {
        ObjectMapper yamlMapper = JsonMapper.builder(
                        new YAMLFactory()
                                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                )
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
                .build();

        List<TaskDTO> tasks = new ArrayList<>();

        try (InputStream inputStream = resource.getInputStream()) {
            Map<String, Object> data = yamlMapper.readValue(inputStream, Map.class);

            Object taskConfigObj = data.get("taskConfig");
            if (taskConfigObj instanceof Map) {
                Map<String, Object> taskConfig = (Map<String, Object>) taskConfigObj;
                Object tasksObj = taskConfig.get("tasks");
                if (tasksObj instanceof List) {
                    List<Map<String, Object>> taskMaps = (List<Map<String, Object>>) tasksObj;

                    tasks = taskMaps.stream()
                            .map(taskMap -> {
                                try {
                                    TaskDTO task = yamlMapper.convertValue(taskMap, TaskDTO.class);
                                    // เพิ่ม metadata ของไฟล์ที่มาจาก
                                    task.setSourceFile(resource.getFilename());
                                    return task;
                                } catch (Exception e) {
                                    log.warn("Failed to parse task from {}: {}", resource.getFilename(), taskMap, e);
                                    return null;
                                }
                            })
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
                }
            }
        } catch (Exception e) {
            log.error("Error parsing YAML file {}: {}", resource.getFilename(), e.getMessage());
            throw e;
        }

        return tasks;
    }

    private void validateTaskIds() {
        Set<String> taskIds = new HashSet<>();
        List<String> duplicates = new ArrayList<>();

        for (TaskDTO task : tasks) {
            if (!taskIds.add(task.getId())) {
                duplicates.add(task.getId());
            }
        }

        if (!duplicates.isEmpty()) {
            throw new IllegalStateException("Duplicate task id found: " + duplicates);
        }
    }

}
