package org.gable.blendata.nextmove.shared.util;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RequiredArgsConstructor
@Component
public class StringUtil {

    @Value("${const.pattern-variable.current-date: currentDate}")
    private String PATTERN_VARIABLE_CURRENT_DATE;

    public enum Variable {
        CURRENTDATE_FORMAT_X("current_date", "<\\s*(PATTERN_VARIABLE_CURRENT_DATE(?:\\s*\\((.*?)\\)\\s*)?)(?:(\\s*[+-]\\s*\\d*)?)?\\s*>");
        private String pattern;
        private String name;

        Variable(String name, String pattern) {
            this.name = name;
            this.pattern = pattern;
        }

        public String getName() {
            return this.name;
        }

        public String getPattern() {
            return this.pattern;
        }

        public void setPattern(String pattern) {
            this.pattern = pattern;
        }
    }

    public void loadDynamicVariable() {
        StringUtil.Variable.CURRENTDATE_FORMAT_X.setPattern(
                StringUtil.Variable.CURRENTDATE_FORMAT_X.getPattern().replaceAll("PATTERN_VARIABLE_CURRENT_DATE", PATTERN_VARIABLE_CURRENT_DATE)
        );
    }

    public static <T extends Object> String replaceValueWhenNull(T value, String replacement) {
        if (value == null) return replacement;
        return value + "";
    }

    public static StringBuffer replaceVariableCurrentDate(LocalDateTime currentDate, String input) {
        StringBuffer result = new StringBuffer();
        currentDate = (null == currentDate ? LocalDateTime.now() : currentDate);
        String patternStr = Variable.CURRENTDATE_FORMAT_X.getPattern();
        Pattern pattern = Pattern.compile(patternStr);
        Matcher matcher = pattern.matcher(input);
        while (matcher.find()) {
            String match = matcher.group();
            String replacement = getReplacementCurrentDate(match, currentDate);
            matcher.appendReplacement(result, replacement);
        }
        matcher.appendTail(result);
        return result;
    }

    private static String getReplacementCurrentDate(String match, LocalDateTime currentDate) {
        String patternStr = Variable.CURRENTDATE_FORMAT_X.getPattern();
        Pattern pattern = Pattern.compile(patternStr);
        Matcher matcher = pattern.matcher(match);
        if (matcher.find()) {
            String datePattern = DateUtil.YYYYMMDD;
            LocalDateTime date = currentDate;
            if (null != matcher.group(3)) {    //...Subtract number of date
                date = currentDate.plusDays(Integer.parseInt(matcher.group(3).trim().replaceAll(" ", "")));
            }
            if (null != matcher.group(2)) {    //...Format date
                datePattern = StringUtils.removeEnd(StringUtils.removeStart(matcher.group(2), "("), ")");
            }
            return DateUtil.convertToString(date, datePattern);
        }
        return match;
    }

    public static String convertWildcardToRegex(String wildcardPath) {
        String escaped = wildcardPath.replace(".", "\\.");
        String regex = escaped.replace("*", "[^/]*");
        return "^" + regex;
    }

    public static String getRandomAlphanumericString(int length) {
        StringBuilder sb = new StringBuilder(length);
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        for (int i = 0; i < length; i++) {
            int index = (int) (Math.random() * characters.length());
            sb.append(characters.charAt(index));
        }
        return sb.toString();
    }
}

