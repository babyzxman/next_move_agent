package org.gable.blendata.nextmove.service.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class AppConfig {

    @Value("${app.id}")
    private String appId;
    @Value("${const.path.config.hadoop-core-site}")
    private String hadoopCoreSitePath;
    @Value("${const.path.config.license}")
    private String licenseFilePath;
    @Value("${spring.application.name}")
    private String serviceId;
    @Value("${app.temp-dir.decompress}")
    private String decompressDir;
    @Value("${app.temp-dir.reconcile-log}")
    private String reconcileLogDir;
}
