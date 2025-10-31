package org.gable.blendata.nextmove.shared.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.Date;


@Entity
@Table(name="transfer_history"
//  Drop uniqueConstraints because of overwrite old file feature for MOVE process
//        , uniqueConstraints = {
//            @UniqueConstraint(columnNames = {"sourceRootPath", "filePath"})
//        }
// MySQL : index : all varchar length cannot be greater than 150
//        , indexes = {
//            @Index(name = "idx_file", columnList = "rootPath, filePath")
//        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String appId;
    private String taskId;
    private String host;        //...SFTP
    private String serviceId;
    private String sourceType;
    @Column(length = 255)
    private String sourceRootPath;
    @Column(length = 1000)
    private String actualSourceRootPath;
    @Column(length = 1000)
    private String filePath;
    @Column(length = 1000)
    private String destination;
    private Long fileSize;
    private Long processTime;
    private String status;
    private String errorNo;
    private int retry;
    @Column(updatable = false, nullable = false)
    @CreationTimestamp
    private Date createdDate;
    @UpdateTimestamp
    private Date modifiedDate;
    @Version
    private Long version;
    @Column(updatable = false)
    private String createdBy;
    private String modifiedBy;


}
