package org.gable.blendata.nextmove.client.adapter;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class FileListingCriteria {
    private final Integer maxFetchFiles;
    private final Integer maxFiles;
    private final LocalDateTime afterDate;
    private final List<String> extensions;
    private final List<String> wildcardPatterns;
    private final List<String> ctrlExtensions;
    private final boolean recursive;
}

