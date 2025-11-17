```java
// This is the full method from SftpConnectionManager.java
public FileSystemAdapter ensureConnectionAlive(FileSystemAdapter currentAdapter, FileSystem fs, String taskKey) throws IOException {

    // This part handles the case where there is no adapter to begin with.
    if (currentAdapter == null) {
        log.warn("{} Connection is null, creating new connection for task {}", AppConst.PREFIX_LOG, taskKey);
        return createConnection();
    }

    // The core logic is in this try...catch block.
    try {

        // LINE 97: This is where the check happens. If the connection is a "zombie"
        // from the race condition, this isConnectionAlive() call will throw an Exception.
        if (!isConnectionAlive(currentAdapter, fs)) {
            // This code block is for a different case (e.g., a connection that was once live
            // but has timed out). It also retries the connection.
            log.warn("{} Connection appears to be dead, recreating for task {}", AppConst.PREFIX_LOG, taskKey);
            closeConnectionSafely(currentAdapter, taskKey);
            return createConnection();
        }

    } catch (Exception e) {

        // --- THIS IS THE HIDDEN RETRY LOGIC ---

        // LINE 105: The Exception from the failed isConnectionAlive() check is caught here.
        // This is the point where the "zombie" connection reveals itself.

        // LINE 106: A WARNING is logged. Because it's not an ERROR, it doesn't stop the
        // application or attract as much attention.
        log.warn("{} Connection test failed, recreating for task {}: {}", AppConst.PREFIX_LOG, taskKey, e.getMessage());

        // LINE 107: The code attempts to clean up the failed connection.
        closeConnectionSafely(currentAdapter, taskKey);

        // LINE 108: THIS IS THE AUTOMATIC RETRY.
        // The method immediately calls createConnection() again and returns the result.
        // This second call succeeds because the race condition is over.
        return createConnection();
    }

    // If the try block succeeds, the original, valid adapter is returned.
    return currentAdapter;
}
```
