package org.gable.blendata.nextmove.client.service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class ReturnListFile {

    private List<String> fileList = new ArrayList<>();

    private Timestamp latestModifiedTime;

    public List<String> getFileList() {
        return fileList;
    }

    public void setFileList(List<String> fileList) {
        this.fileList = fileList;
    }

    public Timestamp getLatestModifiedTime() {
        return latestModifiedTime;
    }

    public void setLatestModifiedTime(Timestamp latestModifiedTime) {
        this.latestModifiedTime = latestModifiedTime;
    }
}
