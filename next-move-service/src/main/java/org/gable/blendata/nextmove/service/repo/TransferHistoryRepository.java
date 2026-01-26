package org.gable.blendata.nextmove.service.repo;

import org.gable.blendata.nextmove.shared.entity.TransferHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransferHistoryRepository extends JpaRepository<TransferHistory, Long>  {

    public List<TransferHistory> findBySourceRootPathAndFilePath(String sourceRootPath, String filePath);

    @Query("select th from TransferHistory th where th.id in (:ID_LIST)")
    public List<TransferHistory> findByIdIn(@Param("ID_LIST") List<Long> ids);

    @Query("select th from TransferHistory th where th.taskId = :taskId")
    List<TransferHistory> findByTaskId(@Param("taskId") String taskId);

    @Query(value =
            "select * from transfer_history where id = any (cast(:ids as bigint[]))",
            nativeQuery = true)
    List<TransferHistory> findByIdAny(@Param("ids") Long[] ids);
}
