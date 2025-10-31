package org.gable.blendata.nextmove.client.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.gable.blendata.nextmove.client.service.RetentionService;
import org.gable.blendata.nextmove.shared.constant.AppConst;
import org.gable.blendata.nextmove.shared.util.DateUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

@RequiredArgsConstructor
@Component
@Slf4j
@ConditionalOnProperty(
        name = "app.scheduler.retention.enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class RetentionJob {

    private final RetentionService retentionService;
    @Value("${app.scheduler.retention.keep-days: 3}")
    private int keepDays;

    @Scheduled(cron = "${app.scheduler.retention.cron: 0 0 0 ? * *}")
    public void execute(){
        log.info("{} [RetentionJob] ... executing", AppConst.PREFIX_LOG);
        LocalDateTime beforeDate = LocalDateTime.now().minusDays(keepDays).toLocalDate().atStartOfDay();
        log.info("{} [RetentionJob] ... before date = {}", DateUtil.convertToString(beforeDate, DateUtil.DD_sl_MM_sl_YYYY_HH_mm_ss));
        retentionService.deleteData(beforeDate);
    }
}
