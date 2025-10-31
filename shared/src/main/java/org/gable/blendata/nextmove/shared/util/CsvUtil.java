package org.gable.blendata.nextmove.shared.util;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

public class CsvUtil {

    public static void writeToCsv(String filePath, String[] headers, List<String[]> data) throws IOException {
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader(headers)
                .build();
        try (
                Writer writer = new FileWriter(filePath);
                CSVPrinter csvPrinter = new CSVPrinter(writer, csvFormat)
        ) {
            for (String[] record : data) {
                csvPrinter.printRecord((Object[]) record);
            }
            csvPrinter.flush();
        }
    }
}
