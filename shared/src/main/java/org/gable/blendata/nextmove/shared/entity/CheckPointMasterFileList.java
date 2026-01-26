package org.gable.blendata.nextmove.shared.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.sql.Timestamp;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class CheckPointMasterFileList {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String host;//...SFTP
    private Integer port;
    @Column(length = 255)
    private String sourceRootPath;
    @Column(length = 1000)
    private String filePath;
    private Integer filePartitionDate;
    private String status;
}
