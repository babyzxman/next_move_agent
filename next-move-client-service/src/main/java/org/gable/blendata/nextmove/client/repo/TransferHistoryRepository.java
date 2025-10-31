package org.gable.blendata.nextmove.client.repo;

import org.gable.blendata.nextmove.client.dto.TransferHistoryView;
import org.gable.blendata.nextmove.shared.constant.TaskConst;
import org.gable.blendata.nextmove.shared.entity.TransferHistory;
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

    @Query("select th.filePath from TransferHistory th " +
            "where sourceType = 'HADOOP' " +
            "and (sourceRootPath = :SOURCE_ROOT_PATH or actualSourceRootPath = :SOURCE_ROOT_PATH) " +
            "and status in :STATUS ")
    public Optional<Set<String>> findHadoopFilePathBySourceRootPathAndStatus(@Param("SOURCE_ROOT_PATH") String sourceRootPath, @Param("STATUS")List<String> status);

    @Query("select th.filePath from TransferHistory th " +
            "where sourceType = 'SFTP' " +
            "and (sourceRootPath = :SOURCE_ROOT_PATH or actualSourceRootPath = :SOURCE_ROOT_PATH) " +
            "and host = :HOST " +
            "and status in :STATUS ")
    public Optional<Set<String>> findSftpFilePathBySourceRootPathAndStatus(@Param("SOURCE_ROOT_PATH") String sourceRootPath, @Param("HOST") String host, @Param("STATUS")List<String> status);


    @Modifying
    @Query("delete from TransferHistory th where th.createdDate < :BEFORE_DATE")
    public void deleteByCreatedDateLessThan(@Param("BEFORE_DATE") Date beforeDate);

    @Query("select t.taskId as taskId,t.filePath as filePath,t.destination as destination, " +
            "t.status as status,t.processTime as processTime,t.fileSize as fileSize," +
            "t.errorNo as errorNo from TransferHistory t where t.taskId = :taskId")
    List<TransferHistoryView> findTransferHistoryViewByTaskId(@Param("taskId") String taskId);

}
