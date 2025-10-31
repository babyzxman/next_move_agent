package org.gable.blendata.nextmove.shared.util;

import com.jcraft.jzlib.ZInputStream;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipParameters;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.gable.blendata.nextmove.shared.constant.AppConst;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Builder
@Slf4j
public class CompressFileUtil {

    private static final String[] supportedFormats = {"tar.gz", "tgz", "tar", "gz", "gzip", "zip", "Z"};
    public static Map<String, Boolean> supportedUncompressCommand = new HashMap<>();

    static{
        supportedUncompressCommand = Stream.of(supportedFormats)
                .collect(Collectors.toMap(supportedFormat -> supportedFormat
                        , supportedFormat -> UnixCommandUtil.isSupportUncompressCommand(supportedFormat)));
    }

    public static Boolean isSupportedFormat(String filename) {
        String patternString = "(?<=\\.)(" + String.join("|", supportedFormats) + ")$";
        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(filename);
        return matcher.find();
    }

    public static String getCompressExtension(String filename) {
        String patternString = "(?<=\\.)(" + String.join("|", supportedFormats) + ")$";
        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(filename);

        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public static void decompressFile(String inputFile, String outputDirectory) throws IOException, CompressorException, ArchiveException, InterruptedException {
        String compressExtension = getCompressExtension(FilenameUtils.getName(inputFile));
        if (null != compressExtension) {
            Path outputPath = Paths.get(outputDirectory);
            if(supportedUncompressCommand.get(compressExtension)) {
                log.info(".....Decompress with Unix Command : {} -> {}", inputFile, outputDirectory);
                decompressWithUnixCommand(inputFile, outputDirectory, compressExtension);
            }else {
                log.info(".....Decompress with Java : {} -> {}", inputFile, outputDirectory);
                decompressWithJava(inputFile, outputDirectory, compressExtension, outputPath);
            }
        }
    }

    private static void decompressWithUnixCommand(String inputFile, String outputDirectory, String compressExtension) throws IOException, InterruptedException {
        File directory = new File(outputDirectory);
        if (!directory.exists()) {
            directory.mkdirs();
        }
        UnixCommandUtil.UnixCommand unixCommand = UnixCommandUtil.commandUnCompress.get(compressExtension);
        List<String> commands = new ArrayList<>();
        commands.addAll(unixCommand.getOptions());
        commands.set(unixCommand.getOptionInputIndex(), inputFile);
        if(unixCommand.getOptionTargetIndex() > -1) {
            commands.set(unixCommand.getOptionTargetIndex(), outputDirectory);
        }
        commands.add(0, unixCommand.getMainCommand());
        try {
            new UnixCommandUtil().executeCommand(commands.toArray(new String[0]) );
        } catch (InterruptedException e) {
            throw e;
        }
    }

    private static void decompressWithJava(String inputFile, String outputDirectory, String compressExtension, Path outputPath) throws CompressorException, ArchiveException, IOException {
        try (InputStream is = new FileInputStream(inputFile);
             BufferedInputStream bis = new BufferedInputStream(is)) {
            if (compressExtension.equals("tar.gz") || compressExtension.equals("tgz")) {
                decompressTarGzFormat(compressExtension, outputPath, bis);
            } else if (compressExtension.equals("tar")) {
                decompressTarFormat(outputPath, bis);
            } else if (compressExtension.equals("zip")) {
                decompressZipFile(inputFile, outputDirectory);
            } else if (compressExtension.equals("Z")){
                decompressUnixZ(inputFile, outputDirectory+"/"+FilenameUtils.getBaseName(inputFile));
            } else if (compressExtension.equals("gz") || compressExtension.equals("gzip")) {
//                compressExtension = "gz";
//                outputDirectory += File.separator + FilenameUtils.removeExtension(FilenameUtils.getName(inputFile));
                decompressGzip(inputFile, outputDirectory);
            } else {
                decompressOtherSupportedFormat(outputDirectory, compressExtension, bis);
            }
            FileUtils.deleteQuietly(new File(inputFile));
        } catch (FileNotFoundException e) {
            log.error("{} !!!Error : file not found {} ", AppConst.PREFIX_LOG, inputFile, e);
            throw e;
        } catch (CompressorException | ArchiveException | IOException e) {
            log.error("{} !!!Error : decompress ", AppConst.PREFIX_LOG, e);
            throw e;
        }
    }

    public static void decompressUnixZ(String inputFile, String outputFile) throws IOException, CompressorException {
        try (InputStream fileInputStream = new FileInputStream(inputFile);
             BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);
             CompressorInputStream compressorInputStream =
                     new CompressorStreamFactory().createCompressorInputStream(CompressorStreamFactory.Z, bufferedInputStream);
             OutputStream fileOutputStream = new FileOutputStream(outputFile);
             BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream)) {

            byte[] buffer = new byte[1024];
            int length;
            while ((length = compressorInputStream.read(buffer)) != -1) {
                bufferedOutputStream.write(buffer, 0, length);
            }
        }
    }

    public static void decompressZlib(String inputFile, String outputFile) throws IOException {
        try (InputStream fileInputStream = new FileInputStream(inputFile);
             ZInputStream zInputStream = new ZInputStream(fileInputStream);
             OutputStream fileOutputStream = new FileOutputStream(outputFile)) {

            byte[] buffer = new byte[1024];
            int length;
            while ((length = zInputStream.read(buffer)) != -1) {
                fileOutputStream.write(buffer, 0, length);
            }
        }
    }

    public static void decompressZipFile(String zipFilePath, String outputDirectory) throws IOException {
        try {
            // Open the ZIP file
            ZipFile zipFile = new ZipFile(zipFilePath);

            // Create the output directory if it doesn't exist
            File outputDir = new File(outputDirectory);
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();

            // Iterate through the entries in the ZIP file
            while(entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                String entryName = entry.getName();
                File entryFile = new File(outputDirectory, entryName);

                // If the entry is a directory, create it
                if (entry.isDirectory()) {
                    entryFile.mkdirs();
                } else {
                    if(entry.getName().contains("._.DS_Store")) continue;
                    // If the entry is a file, extract it
                    try (InputStream is = zipFile.getInputStream(entry);
                        FileOutputStream fout = new FileOutputStream(entryFile)) {
                        IOUtils.copy(is, fout);
                    }
                }
            }

            zipFile.close();
        } catch (IOException e) {
            throw e;

        }
    }

    public static void decompressGzip(String inputFilePath, String outputDirectory) throws IOException {
        File outputDir = new File(outputDirectory);
        if (!outputDir.exists() || !outputDir.canWrite()) {
            throw new IOException("Cannot write to output directory: " + outputDirectory);
        }

        try (FileInputStream fis = new FileInputStream(inputFilePath);
             BufferedInputStream bis = new BufferedInputStream(fis);
             GzipCompressorInputStream gis = new GzipCompressorInputStream(bis, true)) {

            GzipParameters params = gis.getMetaData();
            String outputFileName = params.getFilename();
            if (outputFileName == null || outputFileName.isEmpty()) {
                outputFileName = new File(inputFilePath).getName().replaceFirst("\\.gz$", "");
            }

            File outputFile = new File(outputDirectory, outputFileName);
            try (FileOutputStream fos = new FileOutputStream(outputFile);
                 BufferedOutputStream bos = new BufferedOutputStream(fos)) {

                byte[] buffer = new byte[8192];
                int len;
                long totalBytes = 0;
                while ((len = gis.read(buffer)) != -1) {
                    bos.write(buffer, 0, len);
                    totalBytes += len;
                }
            }
        } catch (IOException e) {
            throw e;
        }
    }

    private static void decompressOtherSupportedFormat(String outputDirectory, String format, BufferedInputStream bis) throws CompressorException, IOException {

        try (CompressorInputStream cis = new CompressorStreamFactory().createCompressorInputStream(format, bis);
             OutputStream os = new FileOutputStream(outputDirectory);
             BufferedOutputStream bos = new BufferedOutputStream(os)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = cis.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
        }

    }

    private static void decompressTarFormat(Path outputPath, BufferedInputStream bis) throws ArchiveException, IOException {
        try (ArchiveInputStream ais = new ArchiveStreamFactory().createArchiveInputStream(bis)) {
            ArchiveEntry entry;
            while ((entry = ais.getNextEntry()) != null) {
                if (!ais.canReadEntryData(entry)) {
                    continue;
                }
                String entryFileName = entry.getName();
                File outputFile = outputPath.resolve(entryFileName).toFile();
                if (entry.isDirectory()) {
                    outputFile.mkdirs();
                } else {
                    try (OutputStream os = new FileOutputStream(outputFile);
                         BufferedOutputStream bos = new BufferedOutputStream(os)) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = ais.read(buffer)) != -1) {
                            bos.write(buffer, 0, len);
                        }
                    }
                }
            }
        }
    }

    private static void decompressTarGzFormat(String compressExtension, Path outputPath, BufferedInputStream bis ) throws IOException {
        try (TarArchiveInputStream fin = new TarArchiveInputStream(new GzipCompressorInputStream(bis))) {
            TarArchiveEntry entry;
            while ((entry = fin.getNextTarEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                File curfile = new File(outputPath.toString(), entry.getName());
                File parent = curfile.getParentFile();
                parent.mkdirs();
                try (FileOutputStream fout = new FileOutputStream(curfile)) {
                    IOUtils.copy(fin, fout);
                }
            }
        }
    }
}

