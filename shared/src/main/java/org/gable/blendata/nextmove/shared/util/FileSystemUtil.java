package org.gable.blendata.nextmove.shared.util;

import lombok.extern.slf4j.Slf4j;
import org.gable.blendata.nextmove.shared.constant.AppConst;
import org.gable.blendata.nextmove.shared.constant.FileSystemType;

import java.net.URI;

@Slf4j
public class FileSystemUtil {

    public static String getFileSystemKey(String path) {
        try {
            String sanitizedPath = path.replaceAll("[\\s<>\"{}|\\\\^`#%?]", "");
            URI uri = URI.create(sanitizedPath);
            String scheme = uri.getScheme();
            if (scheme.equalsIgnoreCase(FileSystemType.S3A) || scheme.equalsIgnoreCase(FileSystemType.VIEW_FS)) {
                String authority = uri.getAuthority();
                return String.format("%s://%s", scheme, authority);
            } else {
                return scheme;
            }
        }catch (Exception e) {
            log.error("{} !!!Error getting file system key for path: {}", AppConst.PREFIX_LOG, path);
            return FileSystemType.DEFAULT;
        }

    }

    public static String getSchemeAndAuthority(String path) {
        try {
            String sanitizedPath = path.replaceAll("[\\s<>\"{}|\\\\^`#%?]", "");
            URI uri = URI.create(sanitizedPath);
            String scheme = uri.getScheme();
            if (scheme.equalsIgnoreCase(FileSystemType.S3A) || scheme.equalsIgnoreCase(FileSystemType.VIEW_FS)) {
                return String.format("%s://%s", scheme, uri.getAuthority());
            }else if(scheme.equalsIgnoreCase(FileSystemType.LOCAL_FILE)){
                return String.format("%s://", scheme);
            }
        }catch (Exception e) {
            log.error("{} !!!Error get scheme and authority for path: {}", AppConst.PREFIX_LOG, path, e);
        }
        return "";

    }
}
