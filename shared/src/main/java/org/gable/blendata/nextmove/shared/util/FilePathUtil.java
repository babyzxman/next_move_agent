package org.gable.blendata.nextmove.shared.util;

import lombok.extern.slf4j.Slf4j;
import org.gable.blendata.nextmove.shared.constant.AppConst;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class FilePathUtil {

    public static String getExtension(String fileName){
        return fileName.substring(fileName.lastIndexOf(".")+1);
    }

    public static String extractMatchingPrefix(String pattern, String fullPath) {
        try {
            if(!pattern.startsWith("\\/")) {
                pattern = new URI(pattern).getPath();
            }
            if(!fullPath.startsWith("/")){
                fullPath =  new URI(fullPath).getPath();
            }

            String regexPattern = pattern
                    .replace("*", "[^/]*") // แทนที่ * ด้วย [^/]* ก่อน
                    .replace("?", "[^/]"); // หากมี ? ก็แทนที่ด้วย [^/]

            String regex = "^" + Pattern.quote(regexPattern)
                    .replace("\\[^/\\]\\*", "[^/]*") // กันการ escape ของ [ และ ]
                    .replace("\\Q", "") // ลบการ escape ที่ไม่จำเป็น
                    .replace("\\E", "");

            Pattern p = Pattern.compile(regex);
            Matcher m = p.matcher(fullPath);

            if (m.find()) {
                return m.group();
            } else {
                log.debug("No match: regex = {}, fullPath = {}", regex, fullPath);
                return null;
            }
        }catch (Exception e){
            log.error("{} !!!Error cannot extract matching prefix : pattern = {}, full path = {}", AppConst.PREFIX_LOG, pattern, fullPath, e);
            return null;
        }
    }

}
