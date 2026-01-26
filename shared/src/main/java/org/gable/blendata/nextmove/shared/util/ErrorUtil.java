package org.gable.blendata.nextmove.shared.util;

import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ErrorUtil {

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
    private static final SecureRandom secureRandom = new SecureRandom();

    public static String getErrorMessage(Exception e){
        return e.getMessage() + "(" + getCauseClassInfo(e.getStackTrace()) + ")";
    }

    public static String getErrorMessage(Throwable e){
        return e.getMessage() + "(" + getCauseClassInfo(e.getStackTrace()) + ")";
    }

    public static String getCauseClassInfo(StackTraceElement[] stes){
        for(StackTraceElement ste : stes){
            if(ste.getClassName().startsWith("org.gable.blendata.nextmove")){
                return ste.getClassName() + " : " +  ste.getMethodName() + "[line " + ste.getLineNumber() + "] ";
            }
        }
        return stes[0].getClassName() + " : " + stes[0].getMethodName() + "[line " + stes[0].getLineNumber() + "] ";
    }
 
    public static String generateErrorNo(String appId) {
        String timestamp = dateFormat.format(new Date());
        byte[] randomBytes = new byte[4];
        secureRandom.nextBytes(randomBytes);
        String randomHex = bytesToHex(randomBytes);
        String code = appId+"-"+timestamp+"-" + randomHex;
        return code;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexStringBuilder = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            hexStringBuilder.append(String.format("%02x", b));
        }
        return hexStringBuilder.toString();
    }
}
