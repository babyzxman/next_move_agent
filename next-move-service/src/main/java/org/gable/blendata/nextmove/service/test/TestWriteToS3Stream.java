package org.gable.blendata.nextmove.service.test;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URISyntaxException;

public class TestWriteToS3Stream {
    public static void main(String[] args) throws URISyntaxException, IOException {
        // S3A path (ensure the bucket exists already)
        String s3Path = "s3a://sftp/test-folder/output.txt";

        // Hadoop configuration
        Configuration conf = new Configuration();
        conf.set("fs.s3a.access.key", "minioadmin");
        conf.set("fs.s3a.secret.key", "minioadmin");
        conf.set("fs.s3a.endpoint", "http://localhost:9000");
        conf.set("fs.s3a.path.style.access", "true"); // must be true for MinIO
        conf.set("fs.s3a.connection.ssl.enabled", "false"); // disable TLS (since you didn’t check TLS)

        // Optional tuning (good for local dev / MinIO)
        conf.set("fs.s3a.connection.timeout", "60000"); // 60s connect timeout
        conf.set("fs.s3a.attempts.maximum", "3");
        conf.set("fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem");   // for MinIO or some S3-compatible stores

        // Get FileSystem for S3A
        FileSystem fs = FileSystem.get(new java.net.URI(s3Path), conf);

        // Create output stream
        Path path = new Path(s3Path);
        try (FSDataOutputStream out = fs.create(path, true);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out))) {

            writer.write("Hello, this is line 1\n");
            writer.write("Hello, this is line 2\n");
            writer.write("Streaming write directly to S3!\n");
        }

        fs.close();
        System.out.println("File written successfully to: " + s3Path);
    }
}
