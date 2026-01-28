package org.gable.blendata.nextmove.client.adapter.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClient.DirEntry;
import org.gable.blendata.nextmove.client.adapter.FileInfo;
import org.gable.blendata.nextmove.client.adapter.FileListingCriteria;
import org.gable.blendata.nextmove.client.adapter.FileSystemAdapter;
import org.gable.blendata.nextmove.shared.util.DateUtil;

import java.io.IOException;
import java.nio.file.attribute.FileTime;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalField;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Slf4j
public class SftpFileSystemAdapter implements FileSystemAdapter {

    private final SftpClient sftpClient;
    private final ClientSession session;

    public SftpFileSystemAdapter(SftpClient sftpClient, ClientSession session) {
        this.sftpClient = sftpClient;
        this.session = session;
    }

    @Override
    public List<FileInfo> listFiles(String rootPath, FileListingCriteria criteria,Boolean usedCheckpoint,
                                    Timestamp checkpointTime,Integer filePartitionDate) throws IOException {
        List<FileInfo> allFiles = new ArrayList<>();
        // Check if rootPath contains glob patterns
        if (containsGlobPattern(rootPath)) {
            List<String> matchedPaths = resolveGlobPattern(rootPath);
            log.debug("Glob pattern '{}' resolved to {} paths", rootPath, matchedPaths.size());

            for (String path : matchedPaths) {
                if (criteria.getMaxFetchFiles() != null && allFiles.size() >= criteria.getMaxFetchFiles()) {
                    break;
                }
                collectFiles(path, criteria, allFiles, 0,
                        usedCheckpoint,checkpointTime,filePartitionDate);
            }
        } else {
            collectFiles(rootPath, criteria, allFiles, 0,
                    usedCheckpoint,checkpointTime,filePartitionDate);
        }

        return allFiles;
    }

    private boolean containsGlobPattern(String path) {
        return path.contains("*") || path.contains("?") || path.contains("[") || path.contains("{");
    }

    private List<String> resolveGlobPattern(String globPattern) throws IOException {
        List<String> matchedPaths = new ArrayList<>();
        String[] pathSegments = globPattern.split("/");

        // Start from root and expand each segment
        List<String> currentPaths = new ArrayList<>();
        currentPaths.add("");

        for (int i = 0; i < pathSegments.length; i++) {
            String segment = pathSegments[i];
            if (segment.isEmpty() && i == 0) {
                currentPaths.clear();
                currentPaths.add("/");
                continue;
            }
            if (segment.isEmpty()) {
                continue;
            }

            List<String> nextPaths = new ArrayList<>();

            for (String currentPath : currentPaths) {
                if (containsGlobPattern(segment)) {
                    // Expand this segment using glob matching
                    List<String> expandedPaths = expandGlobSegment(currentPath, segment);
                    nextPaths.addAll(expandedPaths);
                } else {
                    // Regular path segment
                    String nextPath = currentPath.equals("/") ? "/" + segment : currentPath + "/" + segment;
                    if (exists(nextPath)) {
                        nextPaths.add(nextPath);
                    }
                }
            }

            currentPaths = nextPaths;
            if (currentPaths.isEmpty()) {
                break; // No matching paths found
            }
        }

        // Filter to only include directories for the final result
        for (String path : currentPaths) {
            try {
                SftpClient.Attributes attrs = sftpClient.stat(path);
                if (attrs.isDirectory()) {
                    matchedPaths.add(path);
                }
            } catch (IOException e) {
                log.debug("Path '{}' does not exist or is not accessible", path);
            }
        }

        return matchedPaths;
    }

    private List<String> expandGlobSegment(String parentPath, String globSegment) throws IOException {
        List<String> matchedPaths = new ArrayList<>();

        try {
            Iterable<DirEntry> entries = sftpClient.readDir(parentPath.isEmpty() ? "/" : parentPath);
            Pattern pattern = globToRegexPattern(globSegment);

            for (DirEntry entry : entries) {
                String entryName = entry.getFilename();
                if (!".".equals(entryName) && !"..".equals(entryName) &&
                        pattern.matcher(entryName).matches() && entry.getAttributes().isDirectory()) {

                    String matchedPath = parentPath.equals("/") ? "/" + entryName : parentPath + "/" + entryName;
                    matchedPaths.add(matchedPath);
                }
            }
        } catch (IOException e) {
            log.debug("Cannot read directory '{}': {}", parentPath, e.getMessage());
        }

        return matchedPaths;
    }

    private Pattern globToRegexPattern(String glob) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*':
                    regex.append(".*");
                    break;
                case '?':
                    regex.append(".");
                    break;
                case '[':
                    regex.append("[");
                    break;
                case ']':
                    regex.append("]");
                    break;
                case '{':
                    regex.append("(");
                    break;
                case '}':
                    regex.append(")");
                    break;
                case ',':
                    if (isInsideBraces(glob, i)) {
                        regex.append("|");
                    } else {
                        regex.append(",");
                    }
                    break;
                default:
                    if (isSpecialRegexChar(c)) {
                        regex.append("\\").append(c);
                    } else {
                        regex.append(c);
                    }
                    break;
            }
        }
        return Pattern.compile(regex.toString());
    }

    private boolean isInsideBraces(String glob, int index) {
        int braceCount = 0;
        for (int i = 0; i < index; i++) {
            if (glob.charAt(i) == '{') {
                braceCount++;
            } else if (glob.charAt(i) == '}') {
                braceCount--;
            }
        }
        return braceCount > 0;
    }

    private boolean isSpecialRegexChar(char c) {
        return "\\^$.|+()".indexOf(c) != -1;
    }

    private void collectFiles(String path, FileListingCriteria criteria, List<FileInfo> result,
                              int currentCount, Boolean isCheckpointUsed, Timestamp checkpointTime,
                              Integer filePartitionDate) throws IOException {
        if (criteria.getMaxFetchFiles() != null && result.size() >= criteria.getMaxFetchFiles()) {
            return;
        }
        if(isCheckpointUsed) {
            Iterable<DirEntry> entries = sftpClient.readDir(path);
            for (SftpClient.DirEntry entry : entries) {
                String entryPath = path.endsWith("/") ? path + entry.getFilename() : path + "/" + entry.getFilename();
                if (criteria.getMaxFetchFiles() != null && result.size() >= criteria.getMaxFetchFiles()) {
                    break;
                }
                if (entry.getAttributes().isDirectory()) {
                    if (criteria.isRecursive() && !".".equals(entry.getFilename()) && !"..".equals(entry.getFilename())) {
                        collectFiles(entryPath, criteria, result, currentCount, isCheckpointUsed, checkpointTime,filePartitionDate);
                    }
                } else {
                    if (checkpointTime != null) {
                        if (checkpointTime.toInstant().toEpochMilli() <
                                entry.getAttributes().getModifyTime().to(TimeUnit.MILLISECONDS)) {
                            Instant instant = Instant.ofEpochMilli(entry.getAttributes().getCreateTime() != null
                                    ? entry.getAttributes().getCreateTime().toMillis()
                                    : entry.getAttributes().getModifyTime().toMillis());
                            LocalDate date = instant.atZone(ZoneId.systemDefault()).toLocalDate();
                            String yyyyMMdd = date.format(DateTimeFormatter.BASIC_ISO_DATE);
                            if(yyyyMMdd.equals(filePartitionDate.toString())) {
                                if (criteria.getCheckpointTime() != null) {
                                    if (criteria.getCheckpointTime().toMillis() < entry.getAttributes().getModifyTime().toMillis()) {
                                        criteria.setCheckpointTime(entry.getAttributes().getModifyTime());
                                    }
                                } else {
                                    criteria.setCheckpointTime(entry.getAttributes().getModifyTime());
                                }
                                FileInfo fileInfo = createFileInfo(entryPath, entry);
                                if (matchesExtension(fileInfo, criteria.getExtensions()) &&
                                        matchesWildcardPattern(
                                                fileInfo, criteria.getWildcardPatterns(),
                                                criteria.getExtensions().get(0)) &&
                                        matchesDateFilter(fileInfo, criteria.getAfterDate())) {
                                    result.add(fileInfo);
                                }
                            }
                        }
                    } else {
                        Instant instant = Instant.ofEpochMilli(entry.getAttributes().getCreateTime() != null
                                ? entry.getAttributes().getCreateTime().toMillis()
                                : entry.getAttributes().getModifyTime().toMillis());
                        LocalDate date = instant.atZone(ZoneId.systemDefault()).toLocalDate();
                        String yyyyMMdd = date.format(DateTimeFormatter.BASIC_ISO_DATE);
                        if(yyyyMMdd.equals(filePartitionDate.toString())) {
                            if (criteria.getCheckpointTime() != null) {
                                if (criteria.getCheckpointTime().toMillis() > entry.getAttributes().getModifyTime().toMillis()) {
                                    criteria.setCheckpointTime(entry.getAttributes().getModifyTime());
                                }
                            } else {
                                criteria.setCheckpointTime(entry.getAttributes().getModifyTime());
                            }
                            FileInfo fileInfo = createFileInfo(entryPath, entry);
                            if (matchesExtension(fileInfo, criteria.getExtensions()) &&
                                    matchesWildcardPattern(
                                            fileInfo, criteria.getWildcardPatterns(),
                                            criteria.getExtensions().get(0)) &&
                                    matchesDateFilter(fileInfo, criteria.getAfterDate())) {
                                result.add(fileInfo);
                            }
                        }
                    }
                }
            }
        }
        else {
            try {
                Iterable<DirEntry> entries = sftpClient.readDir(path);
                for (DirEntry entry : entries) {
                    if (criteria.getMaxFetchFiles() != null && result.size() >= criteria.getMaxFetchFiles()) {
                        break;
                    }

                    String entryPath = path.endsWith("/") ? path + entry.getFilename() : path + "/" + entry.getFilename();

                    if (entry.getAttributes().isDirectory()) {
                        if (criteria.isRecursive() && !".".equals(entry.getFilename()) && !"..".equals(entry.getFilename())) {
                            collectFiles(entryPath, criteria, result, currentCount,isCheckpointUsed,checkpointTime,filePartitionDate);
                        }
                    } else {
                        FileInfo fileInfo = createFileInfo(entryPath, entry);
                        if (matchesExtension(fileInfo, criteria.getExtensions()) &&
                                matchesWildcardPattern(
                                        fileInfo, criteria.getWildcardPatterns(),
                                        criteria.getExtensions().get(0)) &&
                                matchesDateFilter(fileInfo, criteria.getAfterDate())) {
                            result.add(fileInfo);
                        }
                    }
                }
            } catch (IOException e) {
                log.warn("Cannot read directory '{}': {}", path, e.getMessage());
            }
        }
    }

    private FileInfo createFileInfo(String path, DirEntry entry) {
        LocalDateTime modTime = DateUtil.convertToLocalDateTime(entry.getAttributes().getModifyTime().toMillis());
        return new FileInfo(
                path,
                entry.getFilename(),
                entry.getAttributes().getSize(),
                modTime,
                entry.getAttributes().isDirectory()
        );
    }

    @Override
    public boolean exists(String filePath) throws IOException {
        try {
            sftpClient.stat(filePath);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public FileInfo getFileInfo(String filePath) throws IOException {
        SftpClient.Attributes attrs = sftpClient.stat(filePath);
        String filename = FilenameUtils.getName(filePath);
        LocalDateTime modTime = DateUtil.convertToLocalDateTime(attrs.getModifyTime().toMillis());

        return new FileInfo(filePath, filename, attrs.getSize(), modTime, attrs.isDirectory());
    }

    @Override
    public void close() throws IOException {
        if (sftpClient != null) {
            sftpClient.close();
        }
        if (session != null) {
            session.close();
        }
    }

    private boolean matchesExtension(FileInfo file, List<String> extensions) {
        if (extensions == null || extensions.isEmpty()) {
            return true;
        }

        return extensions.stream()
                .anyMatch(ext -> {
                    if (ext.contains("*") || ext.contains("?")) {
                        return FilenameUtils.wildcardMatch(file.getName(), "*." + ext);
                    } else {
                        String fileExtension = FilenameUtils.getExtension(file.getName());
                        return ext.equals(fileExtension);
                    }
                });
    }

    private boolean matchesWildcardPattern(FileInfo file, List<String> patterns, String fileExtensions) {
        if (patterns == null || patterns.isEmpty()) {
            return true;
        }
        return patterns.stream()
                .anyMatch(pattern -> {
                    if(!pattern.endsWith("." + fileExtensions)) {
                        pattern = pattern + "." + fileExtensions;
                    }
                    Pattern changePattern = globToRegexPattern(pattern);
                    return changePattern.matcher(file.getName()).matches();
                });
    }

    private boolean matchesDateFilter(FileInfo file, LocalDateTime afterDate) {
        if (afterDate == null) {
            return true;
        }
        return file.getModificationTime().isAfter(afterDate);
    }
}
