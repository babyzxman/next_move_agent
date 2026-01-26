package org.gable.blendata.nextmove.client.repo;

import org.gable.blendata.nextmove.shared.entity.PathCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;

@Repository
public interface CheckpointRepository  extends JpaRepository<PathCheckpoint, Long> {

    @Query("select p.latestModifiedTime from PathCheckpoint p where p.path = :path " +
            "and p.host = :host and p.port = :port and p.filePartitionDate = :filePartitionDate")
    Timestamp getCheckpointTimeByPath(String path, String host, Integer port,Integer filePartitionDate);

    @Query("select p from PathCheckpoint p where p.path = :path and p.host = :host and p.port = :port and p.filePartitionDate = :filePartitionDate")
    PathCheckpoint findByPathHostAndPort(String path, String host, Integer port, Integer filePartitionDate);
}
