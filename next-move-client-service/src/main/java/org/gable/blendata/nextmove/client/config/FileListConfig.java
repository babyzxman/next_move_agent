package org.gable.blendata.nextmove.client.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
public class FileListConfig {

    @Value("${file.modifyTime.checkSkipDate:true}")
    private Boolean fileModifyTimeCheckSkipDate;

}
