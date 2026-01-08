package org.gable.blendata.nextmove.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.gable.blendata.nextmove.license.LicenseValidator;
import org.gable.blendata.nextmove.service.config.AppConfig;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

@Slf4j
@SpringBootApplication
@EntityScan(basePackages = {"org.gable.blendata.nextmove.shared.entity"})
@EnableEurekaClient
@RequiredArgsConstructor
public class NextMoveServiceApplication implements CommandLineRunner {

    private final AppConfig appConfig;

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
        SpringApplication.run(NextMoveServiceApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        try {
            boolean isValidLicense = LicenseValidator.validateLicense(appConfig.getLicenseFilePath());
        } catch (Exception e) {
            log.error("Your Hardware ID is: {}", LicenseValidator.getHardwareId(), e);
            System.exit(1);
        }

        log.info("...License validation successful");
    }

    @Bean(name = "taskExecutor")
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(50);
        executor.setMaxPoolSize(150);
        executor.setQueueCapacity(2000);
        executor.setThreadNamePrefix("Async-");
        executor.initialize();
        return executor;
    }
}