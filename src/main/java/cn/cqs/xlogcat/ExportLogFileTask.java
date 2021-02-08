package cn.cqs.xlogcat;

import android.os.AsyncTask;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 异步任务创建当前全部日志的log文件
 */
public class ExportLogFileTask extends AsyncTask<LogcatInfo, Integer, File> {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
    private File mCacheDir;

    public ExportLogFileTask(File cacheDir) {
        mCacheDir = cacheDir;
    }

    @Override
    protected File doInBackground(LogcatInfo[] logs) {
        if (mCacheDir == null || mCacheDir.isFile() || logs == null || logs.length == 0) {
            return null;
        }

        File logFile = new File(mCacheDir, DATE_FORMAT.format(new Date()) + ".log");
        if (logFile.exists()) {
            if (!logFile.delete()) {
                return null;
            }
        }
        try {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(logFile)));
            for (LogcatInfo log : logs) {
                writer.write(log.getLog() + "\n");
            }
            writer.close();
            return logFile;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
