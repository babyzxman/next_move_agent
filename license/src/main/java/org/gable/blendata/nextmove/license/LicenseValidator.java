package org.gable.blendata.nextmove.license;

import lombok.Builder;
import lombok.Data;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.jasypt.util.text.AES256TextEncryptor;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Class responsible for validating licenses based on hardware ID, digital signature, and license properties.
 */
public class LicenseValidator {
    /** Flag to enable or disable license validation. */
    private static final boolean ENABLE_VALIDATE_LICENSE = false;

    /** Configuration utility for reading properties files. */
    private static Configurations configurations = new Configurations();

    /** RSA public key for signature validation. */
    private static final String PUBLIC_KEY = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQC0Qavcr8fH/fpS8m93DNL+LdlinbE0mk0sMJdTyKsPU6mMX+PEa2I6zaC7SYD/OpjDrKuT2czde6XhGD59SB6YB1q50NS94EZ7S/LcdWDrTt8rwaDyR2L89+adGvyF+e1DFJA3xJkQz+QF43sRhUok14svZtb057hl9LPwBxqHTQIDAQAB";

    /**
     * Enum representing various license details and their corresponding property keys.
     */
    private enum LICENSE_DETAIL {
        ADDRESS("addr"), ASSISTANT("isass"), BILLING_DAATE("bdate"), BILLING_MODE("bmode"),
        COMPANY_NAME("comn"), CPU_CORE("cpuc"), CREDIT_LIMIT("cclimit"), CREDIT_SETTINGS("csett-"),
        CREDIT_SETTINGS_ASSISTANT("csetta-"), CUSTOM_FEATURES("custf-"), CREDIT_PROFILE_ASSISTANT("cprofa-"),
        CREDIT_PROFILE("cprof-"), CUSTOMER_NAME("cusn"), DESCRIPTION("desc"), EMAIL("email"),
        HARDWARE_ID("hids"), LICENSE_ID("id"), LICENSE_END_DATE("lendd"), LICENSE_EXPIRATION_DURATION("lexpdu"),
        LICENSE_GENERATE_TIME("lgen"), LICENSE_NAME("lname"), LICENSE_PERIOD_BY("lpby"),
        LICENSE_SIGNATURE("lsign"), LICENSE_START_DATE("lstrd"), LICENSE_TYPE("ltype"),
        MAINTENANCE_PERIOD("mtper"), PAYMENT_METHOD("pmeth"), PRODUCT_VERSION("pver"),
        PROJECT_NAME("pname"), SERVER_ID("servid"), SIGNATURE_ALGORITHM("salgor"),
        TELEPHONE("tel"), TEMPORARY_LICENSE("istemp"), TOTAL_BALANCE("tbalance"),
        VALIDATE_LICENSE_URL("vurl"), VERIFICATION_METHOD("verm"), ISINCD("isincd"),
        ACTUAL("actual-");

        private String val;

        LICENSE_DETAIL(String val) {
            this.val = val;
        }

        /**
         * Returns the property key associated with the license detail.
         *
         * @return Property key as a string.
         */
        public String val() {
            return this.val;
        }
    }

    /**
     * Validates the license file located at the given path.
     *
     * @param licensePath Path to the license file.
     * @return true if the license is valid, false otherwise.
     */
    public static boolean validateLicense(String licensePath) {
        if (!ENABLE_VALIDATE_LICENSE) return true; // Skip validation if disabled
        File licenseFile = new File(licensePath);
        PropertiesConfiguration config = null;
        try {
            config = configurations.properties(licenseFile); // Load license file properties
        } catch (ConfigurationException e) {
            throw new RuntimeException(String.format("!!!Error : Cannot read license file %s", licensePath));
        }
        try {
            if (null != config) {
                String key = config.getString("key"); // Retrieve encrypted key from license file
                PropertiesConfiguration licensePropertiesConfig = getLicensePropertiesConfig(KeyDecryptor.decrypt(key)); // Decrypt key and load properties
                validateSignature(getContentToSign(licensePropertiesConfig), // Validate digital signature
                        licensePropertiesConfig.getProperty(LICENSE_DETAIL.SIGNATURE_ALGORITHM.val()).toString(),
                        PUBLIC_KEY,
                        licensePropertiesConfig.getProperty(LICENSE_DETAIL.LICENSE_SIGNATURE.val()).toString());
                LicenseDetail licenseDetail = getLicenseDetail(licensePropertiesConfig); // Extract license details
                validateHardwareId(licenseDetail.getHardwareIds()); // Validate hardware ID
            }
        } catch (ConfigurationException | IOException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    /**
     * Generates the content to be signed by filtering and sorting license properties.
     *
     * @param licensePropertiesConfig License properties configuration.
     * @return Content string to be signed.
     */
    public static String getContentToSign(PropertiesConfiguration licensePropertiesConfig) {
        return StreamSupport.stream(((Iterable<String>) () -> licensePropertiesConfig.getKeys()).spliterator(), false)
                .filter(key -> !key.startsWith(LICENSE_DETAIL.CUSTOM_FEATURES.val()) &&
                        !key.equals(LICENSE_DETAIL.LICENSE_SIGNATURE.val()) &&
                        !key.equals(LICENSE_DETAIL.ASSISTANT.val()) &&
                        !key.equals(LICENSE_DETAIL.TEMPORARY_LICENSE.val()) &&
                        !key.startsWith(LICENSE_DETAIL.PAYMENT_METHOD.val()) &&
                        !key.startsWith(LICENSE_DETAIL.CREDIT_SETTINGS.val()) &&
                        !key.startsWith(LICENSE_DETAIL.CREDIT_SETTINGS_ASSISTANT.val()) &&
                        !key.startsWith(LICENSE_DETAIL.CREDIT_PROFILE.val()) &&
                        !key.startsWith(LICENSE_DETAIL.CREDIT_PROFILE_ASSISTANT.val()) &&
                        !key.startsWith(LICENSE_DETAIL.ISINCD.val()) &&
                        !key.startsWith(LICENSE_DETAIL.ACTUAL.val()))
                .sorted()
                .map(sortedKey -> licensePropertiesConfig.getProperty(sortedKey).toString())
                .collect(Collectors.joining("\n")) + "\n";
    }

    /**
     * Validates the digital signature of the license.
     *
     * @param contentToSign Content to be signed.
     * @param signatureAlg Signature algorithm.
     * @param publicKey RSA public key.
     * @param signatureBytes Signature bytes.
     */
    private static void validateSignature(String contentToSign, String signatureAlg, String publicKey, String signatureBytes) {
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            PublicKey _publicKey = factory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKey)));
            Signature signature = Signature.getInstance(signatureAlg);
            signature.initVerify(_publicKey);
            signature.update(contentToSign.getBytes("UTF-8"));
            if (!signature.verify(Base64.getDecoder().decode(signatureBytes))) {
                throw new RuntimeException("!!!Error : [License] signature is invalid ");
            }
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException | SignatureException | UnsupportedEncodingException ex) {
            throw new SecurityException(ex.getMessage());
        }
    }

    /**
     * Validates the hardware ID against the license's hardware IDs.
     *
     * @param licenseHardwareIds List of hardware IDs from the license.
     * @throws UnknownHostException if the host cannot be determined.
     * @throws SocketException if there is an error accessing the network interface.
     */
    private static void validateHardwareId(List<String> licenseHardwareIds) throws UnknownHostException, SocketException {
        String myHardwareId = SystemUtil.getHardwareId(); // Retrieve the current hardware ID
        boolean isMatch = licenseHardwareIds.stream().anyMatch(licenseHardwareId -> myHardwareId.equalsIgnoreCase(licenseHardwareId));
        if (!isMatch) {
            throw new RuntimeException(String.format("!!!Error: [License] The hardware ID %s does not match", myHardwareId));
        }
    }

    /**
     * Parses the decrypted license key into a properties configuration.
     *
     * @param decryptedKey Decrypted license key.
     * @return Properties configuration containing license details.
     * @throws ConfigurationException if there is an error reading the configuration.
     * @throws IOException if there is an I/O error.
     */
    private static PropertiesConfiguration getLicensePropertiesConfig(String decryptedKey) throws ConfigurationException, IOException {
        PropertiesConfiguration config = new PropertiesConfiguration();
        config.read(new StringReader(decryptedKey));
        return config;
    }

    /**
     * Extracts license details from the properties configuration.
     *
     * @param licensePropertiesConfig License properties configuration.
     * @return LicenseDetail object containing extracted details.
     */
    private static LicenseDetail getLicenseDetail(PropertiesConfiguration licensePropertiesConfig) {
        List<String> hardwareIds = licensePropertiesConfig.getProperty(LICENSE_DETAIL.HARDWARE_ID.val()) == null ?
                new ArrayList<>() : Arrays.asList(licensePropertiesConfig.getProperty(LICENSE_DETAIL.HARDWARE_ID.val()).toString().split(","));
        return LicenseDetail.builder()
                .hardwareIds(hardwareIds)
                .build();
    }

    /**
     * Retrieves the hardware ID of the current system.
     *
     * @return Hardware ID as a string.
     * @throws UnknownHostException if the host cannot be determined.
     * @throws SocketException if there is an error accessing the network interface.
     */
    public static String getHardwareId() throws UnknownHostException, SocketException {
        return SystemUtil.getHardwareId();
    }
}

/**
 * Class representing license details.
 */
@Data
@Builder
class LicenseDetail {
    /** List of hardware IDs associated with the license. */
    private List<String> hardwareIds;

    /** Signature algorithm used for validation. */
    private String signatureAlgorithm;
}

/**
 * Utility class for decrypting license keys.
 */
class KeyDecryptor {
    /** Encryption password used for AES-256 decryption. */
    private static final String ENCRYPT_PASSWORD = "BlendataIwrwnCTwUVRDV8Eb0X0S6K7sRroolH6gaVCjsXxoDmONwo6nw";

    /**
     * Decrypts the given content using AES-256 encryption.
     *
     * @param content Encrypted content.
     * @return Decrypted content.
     */
    protected static String decrypt(String content) {
        AES256TextEncryptor encryptor = new AES256TextEncryptor();
        encryptor.setPassword(ENCRYPT_PASSWORD);
        return encryptor.decrypt(content);
    }
}

/**
 * Utility class for system-related operations.
 */
class SystemUtil {
    /**
     * Retrieves the hardware ID of the current system.
     *
     * @return Hardware ID as a string.
     * @throws UnknownHostException if the host cannot be determined.
     * @throws SocketException if there is an error accessing the network interface.
     */
    protected static String getHardwareId() throws UnknownHostException, SocketException {
        InetAddress inetAddress = InetAddress.getLocalHost();
        NetworkInterface networkInterface = NetworkInterface.getByInetAddress(inetAddress);
        return UUID.nameUUIDFromBytes(networkInterface.getHardwareAddress()).toString();
    }
}