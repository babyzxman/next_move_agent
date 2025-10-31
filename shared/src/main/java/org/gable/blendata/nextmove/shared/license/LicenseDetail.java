package org.gable.blendata.nextmove.shared.license;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Date;
import java.util.Map;

@Data
public class LicenseDetail {
    private String productEdition;
    private String productVersion;
    private String hardwareId;
    private Date expireDate;
    private Date maintenanceExpireDate;
    private Map<String, String> signedFeatures;
    private Date generationDateTime;
    private Date activatedDateTime;
//    private LicenseConstant.LicenseStatus licenseStatus;
    private String myHardwareId;
    private String licenseMessage;
    private String verificationMethod;
    private String companyName;
    private double totalBalance;
    private Map<String, String> creditSettings;
    private String paymentMethod;
    private String verificationUrl;
    private Map<String, String> creditSettingsAssistant;

    private int cpuLicense;
    private int cpuUsage;
    private int billDate;
    private int appPort;
    private double creditLimit;
    private String licenseId;
    private String licenseName;
    private String projectName;
    private String customerName;
    @JsonProperty("isTemporaryLicense")
    private Boolean isTemporaryLicense;
    @JsonProperty("isAssistant")
    private Boolean isAssistant;

}
