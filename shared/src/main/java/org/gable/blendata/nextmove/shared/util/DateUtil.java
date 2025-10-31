package org.gable.blendata.nextmove.shared.util;

import org.apache.commons.lang3.StringUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class DateUtil {
    public static final String DD_sl_MM_sl_YYYY_HH_mm_ss = "dd/MM/yyyy HH:mm:ss";
    public static final String YYYYMMDD = "yyyyMMdd";
    public static final String YYYYMMDDHHmmssSSS = "yyyyMMddHHmmssSSS";
    public static String convertToString(Date date, String pattern){
        return date==null? null : new SimpleDateFormat(pattern).format(date);
    }
    public static String convertToString(LocalDate date, String pattern){
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return date==null? null : date.format(formatter);
    }
    public static String convertToString(LocalDateTime date, String pattern){
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return date==null? null : date.format(formatter);
    }

    public static Date convertToDate(String date, String pattern){
        try {
            return StringUtils.isEmpty(date)? null : new SimpleDateFormat(pattern).parse(date);
        } catch (ParseException e) {
            return null;
        }
    }

    public static Date convertToDate(LocalDateTime date){
        return Date.from(date.atZone(ZoneId.systemDefault()).toInstant());
    }

    public static LocalDateTime calculateDateBySubtractingDays(long days){
        //...Ex. 15/07/2024 , days = 3, return 12/07/2024
        LocalDate currentDate = LocalDate.now().minusDays(days);
        LocalTime midnight = LocalTime.MIDNIGHT;
        return LocalDateTime.of(currentDate, midnight);
    }

    public static LocalDateTime calculateDateBySubtractingHours(long hours){
        //...Ex. 15/07/2024 10:51:00, hours = 3, return 15/07/2024 07:00:00
        LocalDateTime currentDateTime = LocalDateTime.now().minusHours(hours);
        return currentDateTime.withMinute(0).withSecond(0).withNano(0);
    }

    public static LocalDateTime convertToLocalDateTime(String date, String pattern){
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return LocalDateTime.parse(date, formatter);
    }

    public static LocalDateTime convertToLocalDateTime(Long epochMilli){
        Instant instant = Instant.ofEpochMilli(epochMilli);
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    public static Date getCurrentDateWithTime(){
        return Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant());
    }


}
