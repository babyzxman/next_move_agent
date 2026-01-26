package org.gable.blendata.nextmove.client.repo;

import org.gable.blendata.nextmove.shared.entity.CheckPointMasterFileList;
import org.gable.blendata.nextmove.shared.entity.PathCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Set;

public interface CheckpointMasterRepository extends JpaRepository<CheckPointMasterFileList, Long> {

    @Query("select c.filePath from CheckPointMasterFileList c where " +
            "c.sourceRootPath = :sourceRootPath and status = 'READY_TO_PROCESS' " +
            "and host = :host and port = :port and filePartitionDate = :filePartitionDate")
    Set<String> getLeftOverFileProcess(String sourceRootPath, String host, Integer port,
                                       Integer filePartitionDate);
}
