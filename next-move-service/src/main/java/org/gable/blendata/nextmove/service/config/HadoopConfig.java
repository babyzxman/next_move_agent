package org.gable.blendata.nextmove.service.config;

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
            URI uri = URI.create(path);
            String scheme = uri.getScheme();
            if (StringUtils.isNotEmpty(scheme)) {
                String authority = uri.getAuthority();
                if (FileSystemType.S3A.equalsIgnoreCase(scheme) || FileSystemType.VIEW_FS.equalsIgnoreCase(scheme)) {
                    String key = String.format("%s://%s", scheme, authority);
                    if (!fileSystems.containsKey(key)) {
                        fileSystems.put(key
                                , FileSystem.get(URI.create(key), createHadoopConfiguration()));
                    }
                }
            }
        }catch (IOException e) {
            log.error("{} !!!Error registering file system for path: {}", AppConst.PREFIX_LOG, path, e);
        }

    }

//
//    @Primary
//    @Bean(FileSystemType.DEFAULT)
//    public FileSystem fileSystem() throws IOException {
//        org.apache.hadoop.conf.Configuration conf = createHadoopConfiguration();
//        return FileSystem.get(conf);
//    }
//
//    @Bean(FileSystemType.VIEW_FS)
//    public FileSystem viewFileSystem() throws IOException {
//        org.apache.hadoop.conf.Configuration conf = createHadoopConfiguration();
//        return FileSystem.get(URI.create("viewfs://blendata"), conf);
//    }
//
//    @Bean(FileSystemType.LOCAL_FILE)
//    public FileSystem localFileSystem() throws IOException {
//        org.apache.hadoop.conf.Configuration conf = createHadoopConfiguration();
//        return FileSystem.get(URI.create("file:///"), conf);
//    }
//
//    @Bean(FileSystemType.S3A)
//    public FileSystem s3aFileSystem() throws IOException {
//        org.apache.hadoop.conf.Configuration conf = createHadoopConfiguration();
//        return FileSystem.get(URI.create("s3a://sftp"), conf);
//    }
//
    private org.apache.hadoop.conf.Configuration createHadoopConfiguration() {
        org.apache.hadoop.conf.Configuration conf = new org.apache.hadoop.conf.Configuration();
        conf.addResource(new Path(appConfig.getHadoopCoreSitePath()));
        return conf;
    }

    public static void main(String[] args) {
        org.apache.hadoop.conf.Configuration conf = new org.apache.hadoop.conf.Configuration();
        conf.addResource(new Path("/Users/anucha.r/MyWork/projects/beg-move-file-agent/next-move-service/src/main/resources/core-site.xml"));
        try {
            FileSystem fileSystem = FileSystem.get(URI.create("s3a://sftp"), conf);
            fileSystem.copyFromLocalFile(false, true, new Path("file:///Users/anucha.r/MyWork/tmp/test-next-move-src-dest/extract/N001_20250919113144259_3a7dacfd-b27e-4703-8c58-f82272e103ab/uncompress/zfile-2.txt")
                    , new Path("s3a://sftp/direct/compress/zip/Users/anucha.r/MyWork/tmp/test-next-move-src-dest/src/main/submain/zip/zfiles/zfile-2.txt.processing"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
