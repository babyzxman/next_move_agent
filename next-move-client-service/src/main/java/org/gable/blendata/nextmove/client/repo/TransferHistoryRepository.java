package org.gable.blendata.nextmove.client.repo;

import org.gable.blendata.nextmove.client.dto.TransferHistoryView;
import org.gable.blendata.nextmove.shared.constant.TaskConst;
import org.gable.blendata.nextmove.shared.entity.TransferHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface TransferHistoryRepository extends JpaRepository<TransferHistory, Long>  {

    public List<TransferHistory> findByTaskId(String taskId);

    @Query("select th.filePath from TransferHistory th where (sourceRootPath = :SOURCE_ROOT_PATH or actualSourceRootPath = :SOURCE_ROOT_PATH) and status in :STATUS")
    public Optional<List<String>> findFilePathBySourceRootPathAndStatus(@Param("SOURCE_ROOT_PATH") String sourceRootPath, @Param("STATUS")List<String> status);

    @Query(value =
            "SELECT DISTINCT ON (th.file_path) " +
                    "  th.file_path AS filePath, " +
                    "  th.status AS status, " +
                    "  th.file_modified_time AS fileModifiedTime " +
                    "FROM transfer_history th " +
                    "WHERE th.source_type = 'HADOOP' " +
                    "  AND (th.source_root_path = :SOURCE_ROOT_PATH " +
                    "       OR th.actual_source_root_path = :SOURCE_ROOT_PATH) " +
                    "  AND th.file_partition_date = :filePartitionDate " +
                    "  AND th.status IN (:STATUS) " +
                    "ORDER BY th.file_path, th.file_modified_time DESC",
            nativeQuery = true)
    List<TransferHistoryView> findHadoopFilePathBySourceRootPathAndStatus(
            @Param("SOURCE_ROOT_PATH") String sourceRootPath,
            @Param("STATUS") List<String> status,
            @Param("filePartitionDate") Integer filePartitionDate
    );

    @Query(value =
            "SELECT DISTINCT ON (th.file_path) " +
                    "  th.file_path AS filePath, " +
                    "  th.status AS status, " +
                    "  th.file_modified_time AS fileModifiedTime " +
                    "FROM transfer_history th " +
                    "WHERE th.source_type = 'SFTP' " +
                    "  AND (th.source_root_path = :SOURCE_ROOT_PATH " +
                    "       OR th.actual_source_root_path = :SOURCE_ROOT_PATH) " +
                    "  AND th.status IN (:STATUS) " +
                    "  AND th.host = :HOST " +
                    "  AND th.file_partition_date = :filePartitionDate " +
                    "ORDER BY th.file_path, th.file_modified_time DESC",
            nativeQuery = true)
    List<TransferHistoryView> findSftpFilePathBySourceRootPathAndStatus(
            @Param("SOURCE_ROOT_PATH") String sourceRootPath,
            @Param("HOST") String host,
            @Param("STATUS") List<String> status,
            @Param("filePartitionDate") Integer filePartitionDate
    );


    @Modifying
    @Query("delete from TransferHistory th where th.createdDate < :BEFORE_DATE")
    public void deleteByCreatedDateLessThan(@Param("BEFORE_DATE") Date beforeDate);

    @Query(value = "select t.taskId as taskId,t.filePath as filePath,t.destination as destination, " +
            "t.status as status,t.processTime as processTime,t.fileSize as fileSize," +
            "t.errorNo as errorNo,t.errorMsg as errorMsg from TransferHistory t where t.taskId = :taskId",
            countQuery = "select count(t) from TransferHistory t where t.taskId = :taskId"
    )
    Page<TransferHistoryView> findTransferHistoryViewByTaskId(@Param("taskId") String taskId, Pageable pageable);

    @Query("select case when count(t) > 0 then false else true end from TransferHistory t where t.taskId = :taskId and t.status = 'PROCESSING'")
    boolean existsProcessingByTaskId(@Param("taskId") String taskId);

}
