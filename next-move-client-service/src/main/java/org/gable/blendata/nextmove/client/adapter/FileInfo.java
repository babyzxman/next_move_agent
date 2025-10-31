package org.gable.blendata.nextmove.client.adapter;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@AllArgsConstructor
@Getter
public class FileInfo {
    private final String path;
    private final String name;
    private final long size;
    private final LocalDateTime modificationTime;
    private final boolean isDirectory;
}
