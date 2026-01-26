package org.gable.blendata.nextmove.client.adapter.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.ArrayUtils;
import org.apache.hadoop.fs.*;
import org.gable.blendata.nextmove.client.adapter.FileInfo;
import org.gable.blendata.nextmove.client.adapter.FileListingCriteria;
import org.gable.blendata.nextmove.client.adapter.FileSystemAdapter;
import org.gable.blendata.nextmove.client.custom.pathfilter.AndPathFilter;
import org.gable.blendata.nextmove.client.custom.pathfilter.OrPathFilter;
import org.gable.blendata.nextmove.shared.util.DateUtil;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class HadoopFileSystemAdapter implements FileSystemAdapter {
    private final FileSystem fs;

    public HadoopFileSystemAdapter(FileSystem fs) {
        this.fs = fs;
    }

    @Override
    public List<FileInfo> listFiles(String rootPath, FileListingCriteria criteria,
                                    Boolean usedCheckpoint, Timestamp checkpointTime,
                                    Integer filePartitionDate) throws IOException {

        PathFilter pathFilter = createPathFilter(criteria);
        FileStatus[] fileStatusList = fs.globStatus(new Path(rootPath + "/*.*"), pathFilter);

        if (ArrayUtils.isEmpty(fileStatusList)) {
            log.warn("[Blendata] !!! Not found match files on path {}", rootPath);
            return new ArrayList<>();
        }

        return Arrays.stream(fileStatusList)
                .filter(status -> !status.isDirectory())
                .filter(status -> matchesDateFilter(status, criteria.getAfterDate()))
                .map(this::createFileInfo)
                .collect(Collectors.toList());
    }

    private PathFilter createPathFilter(FileListingCriteria criteria) {
        List<PathFilter> filters = new ArrayList<>();

        if (criteria.getExtensions() != null && !criteria.getExtensions().isEmpty()) {
            filters.add(generateSrcExtensionPathFilter(criteria.getExtensions().toArray(new String[0])));
        }

        if (criteria.getWildcardPatterns() != null && !criteria.getWildcardPatterns().isEmpty()) {
            filters.add(generateWildCardPatternPathFilter(criteria.getWildcardPatterns().toArray(new String[0])));
        }

        if (filters.isEmpty()) {
            return path -> true;
        }

        return new AndPathFilter(filters.toArray(new PathFilter[0]));
    }

    private PathFilter generateSrcExtensionPathFilter(String[] srcExtensions){
        if(org.apache.commons.lang3.ArrayUtils.isEmpty(srcExtensions)){
            srcExtensions = new String[]{"*"};
        }
        return new OrPathFilter(Arrays.stream(srcExtensions)
                .map(srcExtension -> {
                    try {
                        return new GlobFilter("*."+srcExtension);
                    } catch (IOException e) {
                        throw new RuntimeException(String.format("!!!Invalid source extension %s", srcExtension));
                    }
                })
                .toArray(PathFilter[]::new));
    }
    private PathFilter generateWildCardPatternPathFilter(String[] wildcardPatterns){
        if(org.apache.commons.lang3.ArrayUtils.isEmpty(wildcardPatterns)){
            wildcardPatterns = new String[]{"*"};
        }
        return new OrPathFilter(Arrays.stream(wildcardPatterns)
                .map(wildcardPattern -> {
                    try {
                        return new GlobFilter(wildcardPattern + ".*");
                    } catch (IOException e) {
                        throw new RuntimeException(String.format("!!!Invalid wildcard pattern file name %s", wildcardPattern));
                    }
                })
                .toArray(PathFilter[]::new));
    }

    private FileInfo createFileInfo(FileStatus status) {
        LocalDateTime modTime = DateUtil.convertToLocalDateTime(status.getModificationTime());
        return new FileInfo(
                status.getPath().toString(),
                status.getPath().getName(),
                status.getLen(),
                modTime,
                status.isDirectory()
        );
    }

    @Override
    public boolean exists(String filePath) throws IOException {
        return fs.exists(new Path(filePath));
    }

    @Override
    public FileInfo getFileInfo(String filePath) throws IOException {
        FileStatus status = fs.getFileStatus(new Path(filePath));
        return createFileInfo(status);
    }

    @Override
    public void close() throws IOException {
        if (fs != null) {
            fs.close();
        }
    }

    private boolean matchesDateFilter(FileStatus status, LocalDateTime afterDate) {
        if (afterDate == null) {
            return true;
        }
        return DateUtil.convertToLocalDateTime(status.getModificationTime()).isAfter(afterDate);
    }
}
