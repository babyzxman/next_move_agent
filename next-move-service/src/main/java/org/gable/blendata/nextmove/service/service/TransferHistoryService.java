package org.gable.blendata.nextmove.service.service;

import lombok.RequiredArgsConstructor;
import org.apache.hadoop.fs.Path;
import org.gable.blendata.nextmove.service.adapter.FileSystemAdapter;
import org.gable.blendata.nextmove.service.config.AppConfig;
import org.gable.blendata.nextmove.service.repo.TransferHistoryRepository;
import org.gable.blendata.nextmove.shared.constant.FileStatus;
import org.gable.blendata.nextmove.shared.dto.FileInfoDTO;
import org.gable.blendata.nextmove.shared.dto.TransferRequestWrapper;
import org.gable.blendata.nextmove.shared.entity.TransferHistory;
import org.gable.blendata.nextmove.shared.util.DateUtil;
import org.gable.blendata.nextmove.shared.util.FilePathUtil;
import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.springframework.data.domain.Example;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TransferHistoryService extends GenericService<TransferHistory, Long> {

    @PersistenceContext
    private EntityManager em;

    private final JdbcTemplate jdbcTemplate;

    private final AppConfig appConfig;
    private final TransferHistoryRepository transferHistoryRepository;

    public Optional<TransferHistory> findByRootPathAndFilePath(String rootPath, String filePathStr){
        List<TransferHistory> transferHistories = transferHistoryRepository.findBySourceRootPathAndFilePath(rootPath, filePathStr);
        if(transferHistories.size() >0){
            return Optional.ofNullable(transferHistories.get(0));
        }
        return Optional.empty();
    }

    private final RowMapper<TransferHistory> ROW_MAPPER = (rs, rowNum) -> mapTransferHistory(rs);

    private Long getLong(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    private Integer getInteger(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    private Date getDate(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts == null ? null : new Date(ts.getTime());
    }

    private TransferHistory mapTransferHistory(ResultSet rs) throws SQLException {
        TransferHistory th = new TransferHistory();
        th.setId(getLong(rs, "id"));
        th.setAppId(rs.getString("app_id"));
        th.setTaskId(rs.getString("task_id"));
        th.setHost(rs.getString("host"));
        th.setServiceId(rs.getString("service_id"));
        th.setSourceType(rs.getString("source_type"));
        th.setSourceRootPath(rs.getString("source_root_path"));
        th.setActualSourceRootPath(rs.getString("actual_source_root_path"));
        th.setFilePath(rs.getString("file_path"));
        th.setDestination(rs.getString("destination"));
        th.setFileSize(getLong(rs, "file_size"));
        th.setProcessTime(getLong(rs, "process_time"));
        th.setStatus(rs.getString("status"));
        th.setErrorNo(rs.getString("error_no"));
        th.setRetry(getInteger(rs, "retry") == null ? 0 : getInteger(rs, "retry"));

        // Date fields
        th.setCreatedDate(getDate(rs, "created_date"));
        th.setModifiedDate(getDate(rs, "modified_date"));

        th.setVersion(getLong(rs, "version"));
        th.setCreatedBy(rs.getString("created_by"));
        th.setModifiedBy(rs.getString("modified_by"));

        // Timestamp
        th.setFileModifiedTime(rs.getTimestamp("file_modified_time"));

        th.setFilePartitionDate(getInteger(rs, "file_partition_date"));
        th.setDestinationRootPath(rs.getString("destination_root_path"));
        th.setErrorMsg(rs.getString("error_msg"));

        return th;
    }

    public List<TransferHistory> findByTaskId(String taskId) {
        return transferHistoryRepository.findByTaskId(taskId);
    }

    public List<TransferHistory> findByIdAny(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return new ArrayList<>();
        }

        final String sql =
                "select * from transfer_history " +
                        "where id = any (?::bigint[])";

        return jdbcTemplate.query(con -> {
            PreparedStatement ps = con.prepareStatement(sql);

            // Create a real PG array of bigint (int8)
            Array arr = con.createArrayOf("int8", ids);
            ps.setArray(1, arr);

            return ps;
        }, ROW_MAPPER);
    }

    @Transactional
    public List<TransferHistory> saveProcessing(TransferRequestWrapper transferRequestWrapper) {
        List<TransferHistory> transferHistories = new ArrayList<>();
        for (String filePathStr : transferRequestWrapper.getFilePathStrs()) {
            String actualSourceRootPath = transferRequestWrapper.getSourceRootPathStr().contains("*")?
                    FilePathUtil.extractMatchingPrefix(transferRequestWrapper.getSourceRootPathStr(), filePathStr)
                    : transferRequestWrapper.getSourceRootPathStr();
            TransferHistory transferHistory = save(TransferHistory.builder()
                    .appId(appConfig.getAppId())
                    .taskId(transferRequestWrapper.getTaskId())
                    .serviceId(appConfig.getServiceId())
                    .filePath(filePathStr)
                    .host(transferRequestWrapper.getHost())
                    .sourceType(transferRequestWrapper.getSourceType().name())
                    .sourceRootPath(new Path(transferRequestWrapper.getSourceRootPathStr()).toString())
                    .destinationRootPath(transferRequestWrapper.getDestinationRootPathStr())
                    .actualSourceRootPath(actualSourceRootPath)
                    .status(FileStatus.PROCESSING.name())
                    .createdDate(DateUtil.getCurrentDateWithTime())
                    .createdBy(appConfig.getAppId())
                    .filePartitionDate(transferRequestWrapper.getFilePartitionDate())
                    .build()
            );
            transferHistories.add(transferHistory);
        }
        return transferHistories;
    }

}
