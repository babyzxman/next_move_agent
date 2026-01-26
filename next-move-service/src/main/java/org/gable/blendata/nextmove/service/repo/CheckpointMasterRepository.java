package org.gable.blendata.nextmove.service.repo;

import org.gable.blendata.nextmove.shared.entity.CheckPointMasterFileList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Repository
public interface CheckpointMasterRepository extends JpaRepository<CheckPointMasterFileList, Long> {

    @Query("select c.filePath from CheckPointMasterFileList c where c.sourceRootPath = :sourceRootPath " +
            "and status = 'READY_TO_PROCESS' and c.host = :host and c.port = :port and c.filePartitionDate = :filePartitionDate")
    Set<String> getLeftOverFileProcess(String sourceRootPath);

    @Modifying
    @Transactional
    @Query("update CheckPointMasterFileList c set c.status = 'FINISHED' " +
            "where c.sourceRootPath = :sourceRootPath and c.filePath in (:pathList) " +
            "and c.host = :host and c.port = :port and c.filePartitionDate = :filePartitionDate")
    void updateCheckpointMasterFileListFinished(String sourceRootPath, List<String> pathList,
                                                String host,Integer port,
                                                Integer filePartitionDate);
}
