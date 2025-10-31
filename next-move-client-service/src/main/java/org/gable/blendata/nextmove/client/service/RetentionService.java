package org.gable.blendata.nextmove.client.service;

import lombok.RequiredArgsConstructor;
import org.gable.blendata.nextmove.shared.entity.TransferHistory;
import org.gable.blendata.nextmove.shared.util.DateUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class RetentionService {

    private final TransferHistoryService transferHistoryService;

    @Transactional
    public void deleteData(LocalDateTime beforeDate){
        transferHistoryService.deleteByCreatedDateLessThan(DateUtil.convertToDate(beforeDate));
    }
}
