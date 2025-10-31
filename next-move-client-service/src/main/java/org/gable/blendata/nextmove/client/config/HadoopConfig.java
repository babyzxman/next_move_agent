package org.gable.blendata.nextmove.client.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.gable.blendata.nextmove.shared.constant.AppConst;
import org.gable.blendata.nextmove.shared.constant.FileSystemType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class HadoopConfig {

    private final AppConfig appConfig;

    @Bean
    public Map<String, FileSystem> fileSystems() throws IOException {
        Map<String, FileSystem> fileSystemMap = new HashMap<>();
        org.apache.hadoop.conf.Configuration conf = createHadoopConfiguration();

        FileSystem defaultFs = FileSystem.get(conf);
        fileSystemMap.put(FileSystemType.DEFAULT, defaultFs);

        FileSystem localFs = FileSystem.get(URI.create("file:///"), conf);
        fileSystemMap.put(FileSystemType.LOCAL_FILE, localFs);

        return fileSystemMap;
    }

    public void registerFileSystem(Map<String, FileSystem> fileSystems, String path){
        try {
            String sanitizedPath = path.replaceAll("[\\s<>\"{}|\\\\^`#%?]", "");
            URI uri = URI.create(sanitizedPath );
            String scheme = uri.getScheme();
            if (StringUtils.isNotEmpty(scheme)) {
                String authority = uri.getAuthority();
                if (FileSystemType.S3A.equalsIgnoreCase(scheme) || FileSystemType.VIEW_FS.equalsIgnoreCase(scheme)) {
                    String key = String.format("%s://%s", scheme, authority);
                    if (!fileSystems.containsKey(key)) {
                        log.info("{} Registering file system for path: {} with key: {}", AppConst.PREFIX_LOG, path, key);
                        fileSystems.put(key
                                , FileSystem.get(URI.create(key), createHadoopConfiguration()));
                    }
                }
            }
        }catch (Exception e) {
            log.error("{} !!!Error registering file system for path: {}", AppConst.PREFIX_LOG, path);
        }

    }

    private org.apache.hadoop.conf.Configuration createHadoopConfiguration() {
        org.apache.hadoop.conf.Configuration conf = new org.apache.hadoop.conf.Configuration();
        conf.addResource(new Path(appConfig.getHadoopCoreSitePath()));
        return conf;
    }

    public static void main(String[] args) {
        org.apache.hadoop.conf.Configuration conf = new org.apache.hadoop.conf.Configuration();
        conf.addResource(new Path("/Users/anucha.r/MyWork/projects/beg-move-file-agent/next-move-service/src/main/resources/core-site.xml")); // โหลด config
        try {
            FileSystem fileSystem = FileSystem.get(URI.create("s3a://dummy-bucket"), conf);
            Arrays.stream(fileSystem.listStatus(new Path("s3a://sftp"))).forEach(status -> {
                System.out.println(status.getPath().toString());
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
