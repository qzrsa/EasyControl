package com.scrcpy.app.helper;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Logger utility - writes logs to files
 * - One log file per day
 * - Auto-delete logs older than 10 days
 */
public final class Logger {
    private static final String TAG = "Logger";
    private static final int MAX_LOG_DAYS = 10;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
    
    private static File logDir;
    private static File currentLogFile;
    private static String currentDate = "";
    private static final Object lock = new Object();
    private static boolean initialized = false;
    
    /**
     * Initialize logger
     */
    public static void init(Context context) {
        if (initialized) return;
        
        try {
            // Create log directory: /storage/emulated/0/Android/data/package/files/logs/
            File externalDir = context.getExternalFilesDir(null);
            if (externalDir != null) {
                logDir = new File(externalDir, "logs");
                if (!logDir.exists()) {
                    logDir.mkdirs();
                }
            }
            
            // Clean old logs
            cleanOldLogs();
            
            initialized = true;
            i(TAG, "Logger initialized, log dir: " + (logDir != null ? logDir.getAbsolutePath() : "null"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Get log file path for today
     */
    public static String getLogFilePath() {
        synchronized (lock) {
            updateLogFile();
            return currentLogFile != null ? currentLogFile.getAbsolutePath() : null;
        }
    }
    
    /**
     * Info level log
     */
    public static void i(String tag, String message) {
        writeLog("I", tag, message);
    }
    
    /**
     * Debug level log
     */
    public static void d(String tag, String message) {
        writeLog("D", tag, message);
    }
    
    /**
     * Warning level log
     */
    public static void w(String tag, String message) {
        writeLog("W", tag, message);
    }
    
    /**
     * Error level log
     */
    public static void e(String tag, String message) {
        writeLog("E", tag, message);
    }
    
    /**
     * Error level log with exception
     */
    public static void e(String tag, String message, Throwable t) {
        writeLog("E", tag, message, t);
    }
    
    /**
     * Verbose level log
     */
    public static void v(String tag, String message) {
        writeLog("V", tag, message);
    }
    
    /**
     * Log method entry
     */
    public static void method(String tag, String methodName) {
        writeLog("M", tag, "-> " + methodName);
    }
    
    /**
     * Log operation with result
     */
    public static void operation(String tag, String operation, boolean success) {
        writeLog("O", tag, operation + " [" + (success ? "SUCCESS" : "FAILED") + "]");
    }
    
    /**
     * Log connection event
     */
    public static void connection(String tag, String event, String device) {
        writeLog("C", tag, "CONNECTION: " + event + " - " + device);
    }
    
    /**
     * Write log to file
     */
    private static void writeLog(String level, String tag, String message) {
        writeLog(level, tag, message, null);
    }
    
    /**
     * Write log to file with optional exception
     */
    private static void writeLog(String level, String tag, String message, Throwable t) {
        if (!initialized || logDir == null) return;
        
        synchronized (lock) {
            try {
                updateLogFile();
                
                if (currentLogFile == null) return;
                
                String timestamp = TIME_FORMAT.format(new Date());
                String logLine = String.format("%s %s/%s: %s", timestamp, level, tag, message);
                
                FileWriter writer = new FileWriter(currentLogFile, true);
                writer.append(logLine).append("\n");
                
                if (t != null) {
                    PrintWriter pw = new PrintWriter(writer);
                    t.printStackTrace(pw);
                    pw.flush();
                }
                
                writer.flush();
                writer.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Update log file based on current date
     */
    private static void updateLogFile() {
        String today = DATE_FORMAT.format(new Date());
        
        if (!today.equals(currentDate)) {
            currentDate = today;
            String fileName = "clientstream_" + today + ".log";
            currentLogFile = new File(logDir, fileName);
        }
    }
    
    /**
     * Clean logs older than MAX_LOG_DAYS days
     */
    private static void cleanOldLogs() {
        if (logDir == null || !logDir.exists()) return;
        
        try {
            long cutoffTime = System.currentTimeMillis() - (MAX_LOG_DAYS * 24L * 60 * 60 * 1000);
            
            File[] logFiles = logDir.listFiles((dir, name) -> name.startsWith("clientstream_") && name.endsWith(".log"));
            
            if (logFiles != null) {
                for (File file : logFiles) {
                    if (file.lastModified() < cutoffTime) {
                        if (file.delete()) {
                            android.util.Log.d(TAG, "Deleted old log: " + file.getName());
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Get list of all log files
     */
    public static File[] getLogFiles() {
        if (logDir == null || !logDir.exists()) return new File[0];
        
        File[] files = logDir.listFiles((dir, name) -> name.startsWith("clientstream_") && name.endsWith(".log"));
        if (files != null) {
            Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        }
        return files != null ? files : new File[0];
    }
    
    /**
     * Get total log size in bytes
     */
    public static long getTotalLogSize() {
        File[] files = getLogFiles();
        long total = 0;
        for (File f : files) {
            total += f.length();
        }
        return total;
    }
    
    /**
     * Delete all log files
     */
    public static void clearAllLogs() {
        File[] files = getLogFiles();
        for (File f : files) {
            f.delete();
        }
    }
}
