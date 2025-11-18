package org.gable.blendata.nextmove.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.gable.blendata.nextmove.client.config.AppConfig;
import org.gable.blendata.nextmove.client.config.TaskConfig;
import org.gable.blendata.nextmove.client.dto.TaskDTO;
import org.gable.blendata.nextmove.client.service.MainTaskService;
import org.gable.blendata.nextmove.license.LicenseValidator;
import org.gable.blendata.nextmove.shared.util.StringUtil;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.annotation.PreDestroy;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.Executor;

@Slf4j
@SpringBootApplication
@EntityScan(basePackages = {"org.gable.blendata.nextmove.shared.entity"})
@Import({StringUtil.class})
@EnableEurekaClient
@EnableWebMvc
@EnableAsync
@RequiredArgsConstructor
@EnableScheduling
public class NextMoveClientApplication implements CommandLineRunner {

    private final AppConfig appConfig;
    private final TaskConfig taskConfig;
    private final DiscoveryClient discoveryClient;
    private final MainTaskService mainTaskService;
    private final StringUtil stringUtil;
    private final Scheduler scheduler;

    public static void main(String[] args) {
        if(ArrayUtils.isNotEmpty(args) && "-Hw".equalsIgnoreCase(args[0])){
            try {
                System.out.println("Hardware ID : = " + LicenseValidator.getHardwareId());
            } catch (UnknownHostException e) {
                System.err.println("!!! Cannot get HardwareId : " + e.getCause() + " : " + e.getMessage());
            } catch (SocketException e) {
                System.err.println("!!! Cannot get HardwareId : " + e.getCause() + " : " + e.getMessage());
            }
            System.exit(0);
        }
        SpringApplication.run(NextMoveClientApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        try {
            boolean isValidLicense = LicenseValidator.validateLicense(appConfig.getLicenseFilePath());
        }catch(Exception e){
            log.error("", e);
            log.info("Your Hardware ID is : {}", LicenseValidator.getHardwareId());
            System.exit(1);
        }
        log.info("...License validation successful");
        stringUtil.loadDynamicVariable();
        //...Load tasks
        List<TaskDTO> taskDtos = taskConfig.getTasks();

        //...Create main tasks
        if (!scheduler.isStarted()) {
            scheduler.start();
        }
        scheduler.clear();
        log.info("All jobs cleared on startup");
        mainTaskService.create(taskDtos);

    }

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("Async-");
        executor.initialize();
        return executor;
    }

    @PreDestroy
    public void shutdownScheduler() throws SchedulerException {
        log.info("Shutting down Quartz...");
        scheduler.shutdown(true);  // true = รอ job ทำเสร็จก่อน
    }




}