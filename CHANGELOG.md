# 📜 Changelog
All notable changes to this project will be documented in this file.

---

## 🏷️ [2.1.0] - 2025-09-23

### ✨ Added
- **Multiple File System Support (s3a, file, viewfs)**
    - Source and destination paths can now explicitly specify the file system.
    - Examples:
      ```yaml
      source: file:///Users/markMyWork/tmp/test-next-move-src-dest/src/mai*/sub*/zip
      destination: s3a://sftp/direct/compress/zip
      ```
      ```yaml
      source: viewfs://blendata/src-local-02/main/submain/none-*/<current_date(ddMMyyyy)>
      destination: viewfs://blendata/dest-local-02/none-dest/<current_date(ddMMyyyy)+1>
      ```
    - ⚠️ **Important Note**: When using `viewfs`, conflicts may occur if the path is also defined in `core-site.xml`.  
      If the same target is defined in both absolute and relative formats, the system will treat them as different files and process them twice.

- **Support for Multiple Task Configuration Files**
    - You can now specify multiple `task-configure.yml` files using wildcard patterns.
    - Example:
      ```yaml
      path:
        config:
          task: /Users/mark/MyWork/task-configure-*.yml
      ```

      - **New APIs for External Execution**
          - Added APIs for external systems to execute tasks immediately and check results:
              - `POST /api/external/task/create` – Create and execute a task immediately.
                  ```json
                      {
                        "id": "E-Task1",
                        "sourceType": "HADOOP",
                        "rootPath": {
                            "source": "viewfs://blendata/src-local-02/main/submain/api",
                            "destination": "viewfs://blendata/dest-local-02/api"
                        },
                        "type": "NONE_CONTROL",
                        "moveType": "COPY",
                        "filesPerRound": 100,
                        "checkFileDelaySeconds": 10,
                        "retry": 0,
                        "srcExtensions": [
                          "csv"
                        ],
                        "wildcardPatterns": [
                          "api*"
                        ],
                        "checkFileSize": false,
                        "overwrite": false
                      }
                  ```
              - `GET /api/external/task/get/{task-id}` – Retrieve execution result by task ID.
                 ```text
                   http://localhost:8080/api/external/task/get/E-Task1
                 ```
### 🐞 Fixed
- **SFTP OutOfMemory Issue**
    - Fixed memory issue that occurred when tasks with SFTP sources were triggered too frequently.


## 🏷️ [2.0.1] - 2025-09-11

### ✨ Added
- **Add logs: matching files at next-move-client, requested move files at next-move-service.**
  - Next-move-client logs:
      ```
        2025-09-11 17:02:42 INFO  [ : ] o.g.b.n.c.s.MainTaskService.process -  [Blendata] task = task-02, matching file source paths = viewfs://blendata/src-local-02/main/submain/none-src/10092025/exam-3.txt
        viewfs://blendata/src-local-02/main/submain/none-src/10092025/exam-4.txt
      ```
  - Next-move-service logs:
      ```
      2025-09-11 17:02:43 INFO  [ : ] o.g.b.n.s.s.NoneCtrlService.moveFiles -  [Blendata] Starting task C001_task-02_GQYEGMJN with source path /src-local-02/main/submain/none-*/10092025
      2025-09-11 17:02:43 INFO  [ : ] o.g.b.n.s.s.NoneCtrlService.lambda$moveFiles$0 -  [Blendata] Task ID(task-02) Request to move file viewfs://blendata/src-local-02/main/submain/none-src/10092025/exam-4.txt to /dest-local-02/none-dest/11092025
      2025-09-11 17:02:43 INFO  [ : ] o.g.b.n.s.s.NoneCtrlService.lambda$moveFiles$0 -  [Blendata] Task ID(task-02) Request to move file viewfs://blendata/src-local-02/main/submain/none-src/10092025/exam-3.txt to /dest-local-02/none-dest/11092025
      ```
### 🐞 Fixed
- **Improved extraction directory naming by adding a unique ID as a suffix.**
- **Deletes source files on successful MOVE operation.**

## 🏷️ [2.0.0] - 2025-09-01

### ✨ Added
- **SFTP support**
    - Introduced feature to connect and operate via SFTP.

### 🔄 Changed
- **Database schema (`transfer_history`)**
    - Added column `source_type` (supports values: `HADOOP`, `SFTP`).
        - **HADOOP** refers to sources mapped through `viewfs` in the `core-site.xml`.
    - Added column `host` to store the SFTP host.
    - The `overwrite` configuration now applies to both `COPY` and `MOVE` operations (previously only applied to `MOVE`).

### 📌 Migration Notes
- When upgrading from **1.x.x** to **2.0.0**, you must:
    - Add the new columns `source_type` and `host` to the `transfer_history` table.
    - Update existing records by setting the `source_type` value to `HADOOP`.
- For SFTP, source files will **not** be deleted after transfer.

---

## 🏷️ [1.4.1] - 2025-08-20

### 🚀 Improved
- **Simplified retention setup**
    - Configuration is now optional when retention is disabled.
- **License validation**
    - Updated signature content verification process.

---

## 🏷️ [1.4.0] - 2025-07-15

### ✨ Added
- **New Job for data retention**
    - Support retention of data in the database for a specified number of days.
    - Configure at `application.yml`:
      ```yaml
      app:
        id: C001
        scheduler:
          retention:
            keep-days: 3 # Number of days to keep data, default is 3 days.
            cron: 0 0 0 ? * *   # Seconds | Minutes | Hours | Day of Month | Month | Day of Week
      ```
- **New APIs for job deletion**
    - `DELETE /api/job/delete/all`: Deletes all scheduled jobs.
    - `DELETE /api/job/delete/{jobGroup}/{jobName}`: Deletes a specific job by task.
        - `jobGroup`: `<task type in lowercase>_group`
        - `jobName`: `<task id>_job`
    - Both APIs stop scheduled jobs and halt copy/move file services, provided the file has not yet entered the copy/move process to the destination.
- **New command-line option `-Hw`**
    - Added support for `-Hw` option to retrieve the Hardware ID of the machine.
    - Usage:
      ```bash
      java -jar next-move-client-1.4.0.jar -Hw
      java -jar next-move-service-1.4.0.jar -Hw
      ```

### 🔄 Changed
- **Updated HTTP method for reload APIs**
    - Changed `/api/job/reload/all` from `PUT` to `POST`.
    - Changed `/reload/task/{taskId}` from `PUT` to `POST`.

### 🐞 Fixed
- **Improved file extension extraction**
    - Enhanced support for `.gz` and `.gzip` files by merging gzip streams into a single file.
---

## 🏷️ [1.2.0] - 2025-02-25

### ✨ Added
- **Extract file extension Z.**
- **Added an option to automatically create the target directory using the compressed file nameใ**

### 🔄 Changed
- **Database schema**
    - Removed unique constraint on `transfer_history` table to allow multiple transfers of the same file under different conditions.
    - Alter column file_path and destination to varchar(1000)
### 📌 Migration Notes
- When upgrading from versions **before 1.2.0**:
    - Drop the unique constraint from the `transfer_history` table.
    - Alter column file_path and destination to varchar(1000)