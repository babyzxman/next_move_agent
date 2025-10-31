package org.gable.blendata.nextmove.license;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.jasypt.util.text.AES256TextEncryptor;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class LicenseGenerator {

    private static final String LICENSE_PATH = "/Users/anucha.r/MyWork/research/next-move-agent/license/src/main/resources/license_off_me.lic";
    private Map<String, String> replacements = new HashMap<String, String>(){{
        put("{HARDWARE_ID}", "e83d51fd-8513-3766-bc3c-a977da471a08");
    }};

    private String generateLicenseFile(){
        String licenseDetail = null;
        try {
            licenseDetail = generateLicenseDetail();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        String licensePath = writeFile(licenseDetail);
        return licensePath;
    }

    private String writeFile(String licenseDetail){
        try (FileOutputStream outputStream = new FileOutputStream(LICENSE_PATH)) {
            IOUtils.write(licenseDetail, outputStream, "UTF-8");
            return LICENSE_PATH;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private String generateLicenseDetail() throws URISyntaxException, IOException {

        String templateContent = IOUtils.resourceToString("/license_off_key_template.txt", Charsets.toCharset("UTF-8"));
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            templateContent = templateContent.replace(entry.getKey(), entry.getValue());
        }
        String licenseContent = "key="+Encryptor.encrypt(templateContent)
                + "\n"
                + "mode=No Paid";
        return licenseContent;
    }

    public static void main(String[] args) {
        new LicenseGenerator().generateLicenseFile();
    }
}
class Encryptor{
    private static final String ENCRYPT_PASSWORD = "BlendataIwrwnCTwUVRDV8Eb0X0S6K7sRroolH6gaVCjsXxoDmONwo6nw";
    protected static String encrypt(String content){
        AES256TextEncryptor encryptor = new AES256TextEncryptor();
        encryptor.setPassword(ENCRYPT_PASSWORD);
        return encryptor.encrypt(content);
    }

    protected static String decrypt(String content){
        AES256TextEncryptor encryptor = new AES256TextEncryptor();
        encryptor.setPassword(ENCRYPT_PASSWORD);
        return encryptor.decrypt(content);
    }

}
