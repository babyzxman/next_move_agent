package org.gable.blendata.nextmove.shared.util;

import com.amazonaws.util.StringUtils;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class UnixCommandUtil {

    public static Map<String, UnixCommand> commandUnCompress = new HashMap<String, UnixCommand>(){{
        put("tar.gz", UnixCommand.builder()
                .mainCommand("tar")
                .options(Arrays.asList("-xzvf", "<input-file>", "-C", "<target>"))
                .optionInputIndex(1)
                .optionTargetIndex(3)
                .build());
        put("tgz", UnixCommand.builder()
                .mainCommand("tar")
                .options(Arrays.asList("-xzvf", "<input-file>", "-C", "<target>"))
                .optionInputIndex(1)
                .optionTargetIndex(3)
                .build());
        put("tar", UnixCommand.builder()
                .mainCommand("tar")
                .options(Arrays.asList("-xvf", "<input-file>", "-C", "<target>"))
                .optionInputIndex(1)
                .optionTargetIndex(3)
                .build());
        put("zip", UnixCommand.builder()
                .mainCommand("unzip")
                .options(Arrays.asList("<input-file>", "-d", "<target>"))
                .optionInputIndex(0)
                .optionTargetIndex(2)
                .build());
    }};

    public void executeCommand(String...commands) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(commands);
        log.info("...[Unix Command] Extracting -> {}", StringUtils.join(" ", commands));
        BufferedReader errorReader = null;
        try {
            Process process = processBuilder.start();
            errorReader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream())
            );
            String error = null;
            int numLine = 0;
            while (( error = errorReader.readLine()) != null && numLine < 1) {
                numLine++;
            }
            int exitCode = process.waitFor();
            if(exitCode != 0){
                throw new IOException(error==null ? "No error detail" : error);
            }
        } catch (IOException | InterruptedException e) {
            throw e;
        }finally{
            if(null != errorReader) errorReader.close();
        }
    }

    public static boolean isSupportUncompressCommand(String supportedFormat) {
        try {
            if(!commandUnCompress.containsKey(supportedFormat)) return false;
            ProcessBuilder processBuilder = new ProcessBuilder(commandUnCompress.get(supportedFormat).getMainCommand(), "--help");
            processBuilder.redirectErrorStream(true); // Redirect error stream to standard output
            Process process = processBuilder.start();

            // Wait for the process to complete and check exit code
            int exitCode = process.waitFor();
            return exitCode == 0; // Exit code 0 indicates success
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    @Builder
    @Data
    public static class UnixCommand{
        private String mainCommand;
        private int optionInputIndex;
        private int optionTargetIndex;
        private List<String> options;
    }
}
