package cn.cqs.xlogcat;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import cn.cqs.toast.ToastUtils;

/**
 * Logcat 显示窗口
 */
public final class LogcatActivity extends Activity implements TextWatcher,View.OnClickListener,
        CompoundButton.OnCheckedChangeListener, LogcatManager.Callback,
        AdapterView.OnItemLongClickListener, AdapterView.OnItemClickListener{

    private final static String[] ARRAY_LOG_LEVEL = {"Verbose", "Debug", "Info", "Warn", "Error"};

    private final static String FILE_TYPE = "Logcat";
    private final static String LOGCAT_TAG_FILTER_FILE = "logcat_tag_filter.txt";

    private final List<LogcatInfo> mLogData = new ArrayList<>();

    private CheckBox mSwitchView;
    private View mSaveView;
    private EditText mSearchView;
    private View mEmptyView;
    private View mCleanView;
    private View mCloseView;
    private ListView mListView;
    private View mDownView;

    private LogcatAdapter mAdapter;

    private String mLogLevel = "V";
    private Spinner spinner;
    /** Tag 过滤规则 */
    private final List<String> mTagFilter = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.logcat_activity);
        spinner = findViewById(R.id.spinner);
        initSpinnerStyle(spinner,ARRAY_LOG_LEVEL);
        mSwitchView = findViewById(R.id.iv_log_switch);
        mSaveView = findViewById(R.id.iv_log_save);
        mSearchView = findViewById(R.id.et_log_search);
        mEmptyView = findViewById(R.id.iv_log_empty);
        mCleanView = findViewById(R.id.iv_log_clean);
        mCloseView = findViewById(R.id.iv_log_close);
        mListView = findViewById(R.id.lv_log_list);
        mDownView = findViewById(R.id.ib_log_down);

        mAdapter = new LogcatAdapter();
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(this);
        mListView.setOnItemLongClickListener(this);
        mSwitchView.setOnCheckedChangeListener(this);
        mSearchView.addTextChangedListener(this);

        mSearchView.setText(LogcatConfig.getLogcatText());
        setLogLevel(LogcatConfig.getLogcatLevel());

        mSaveView.setOnClickListener(this);
        mEmptyView.setOnClickListener(this);
        mCleanView.setOnClickListener(this);
        mCloseView.setOnClickListener(this);
        mDownView.setOnClickListener(this);

        // 开始捕获
        LogcatManager.start(this);
        mListView.postDelayed(new Runnable() {
            @Override
            public void run() {
                mListView.setSelection(mAdapter.getCount() - 1);
            }
        }, 1000);
        initFilter();
    }
    @Override
    public void onReceiveLog(LogcatInfo info) {
        // 这个Tag必须不在过滤列表中，并且这个日志是当前应用打印的
        if (Integer.parseInt(info.getPid()) != android.os.Process.myPid()) {
            return;
        }
        if (!mTagFilter.contains(info.getTag())) {
            mListView.post(new LogRunnable(info));
        }
    }

    /**
     * Spinner统一风格
     *
     * @param items
     * @param spinner
     */
    public void initSpinnerStyle(Spinner spinner, String[] items) {
        setSpinnerDropDownVerticalOffset(spinner);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(spinner.getContext(), R.layout.logcat_dropdown_item, R.id.spinner_item, items);
        adapter.setDropDownViewResource(R.layout.logcat_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String level = ARRAY_LOG_LEVEL[position];
                setLogLevel(String.valueOf(level.charAt(0)));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    /**
     * 设置系统Spinner的下拉偏移
     *
     * @param spinner
     */
    public void setSpinnerDropDownVerticalOffset(Spinner spinner) {
        int itemHeight = dip2px(30f);
        int dropdownOffset = dip2px(1f);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            spinner.setDropDownVerticalOffset(0);
        } else {
            spinner.setDropDownVerticalOffset(itemHeight + dropdownOffset);
        }
    }
    /**
     * 根据手机的分辨率从 dp 的单位 转成为 px(像素)
     */
    public int dip2px(float dpValue) {
        final float scale = getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        mAdapter.onItemClick(position);
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, final int position, long id) {
        String[] items = {getResources().getString(R.string.logcat_options_copy),
                getResources().getString(R.string.logcat_options_share),
                getResources().getString(R.string.logcat_options_share_all),
                getResources().getString(R.string.logcat_options_delete),
                getResources().getString(R.string.logcat_options_shield)};
        new AlertDialog.Builder(this).setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (manager != null) {
                            manager.setPrimaryClip(ClipData.newPlainText("log", mAdapter.getItem(position).getLog()));
                            toast(R.string.logcat_copy_succeed);
                        } else {
                            toast(R.string.logcat_copy_fail);
                        }
                        break;
                    case 1:
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.setType("text/plain");
                        intent.putExtra(Intent.EXTRA_TEXT, mAdapter.getItem(position).getLog());
                        startActivity(Intent.createChooser(intent, getResources().getString(R.string.logcat_options_share)));
                        break;
                    case 2:
                        @SuppressLint("StaticFieldLeak")
                        ExportLogFileTask task = new ExportLogFileTask(getExternalCacheDir()) {
                            @Override
                            protected void onPostExecute(File file) {
                                if (file == null) {
                                    ToastUtils.show(R.string.logcat_create_log_file_failed);
                                } else {
                                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                                    shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    shareIntent.setType("text/plain");
                                    Uri uri = LogcatFileProvider.getUriForFile(getApplicationContext(),getPackageName() + ".logcat_provider", file);
                                    shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                                    if (getPackageManager().queryIntentActivities(shareIntent, 0).isEmpty()) {
                                        ToastUtils.show(R.string.logcat_not_support_on_this_device);
                                    } else {
                                        startActivity(shareIntent);
                                    }
                                }
                            }
                        };
                        task.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,mAdapter.getDataArray());
                        break;
                    case 3:
                        mLogData.remove(mAdapter.getItem(position));
                        mAdapter.removeItem(position);
                        break;
                    case 4:
                        addFilter(mAdapter.getItem(position).getTag());
                        break;
                    default:
                        break;
                }
            }
        }).setCancelable(true).create().show();
        return true;
    }

    @Override
    public void onClick(View v) {
        if (v == mSaveView) {
            saveLogToFile();
        }else if (v == mEmptyView) {
            mSearchView.setText("");
        } else if (v == mCleanView) {
            LogcatManager.clear();
            mAdapter.clearData();
        } else if (v == mCloseView) {
            onBackPressed();
        } else if (v == mDownView) {
            // 滚动到列表最底部
            mListView.smoothScrollToPosition(mAdapter.getCount() - 1);
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isChecked) {
            toast(R.string.logcat_capture_pause);
            LogcatManager.pause();
        } else {
            LogcatManager.resume();
        }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {}

    @Override
    public void afterTextChanged(Editable s) {
        String keyword = s.toString().trim();
        LogcatConfig.setLogcatText(keyword);
        mAdapter.setKeyword(keyword);
        mAdapter.clearData();
        for (LogcatInfo info : mLogData) {
            if ("V".equals(mLogLevel) || info.getLevel().equals(mLogLevel)) {
                if (!"".equals(keyword)) {
                    if (info.getLog().contains(keyword) || info.getTag().contains(keyword)) {
                        mAdapter.addItem(info);
                    }
                } else {
                    mAdapter.addItem(info);
                }
            }
        }
        mListView.setSelection(mAdapter.getCount() - 1);
        mEmptyView.setVisibility("".equals(keyword) ? View.GONE : View.VISIBLE);
    }

    private void setLogLevel(String level) {
        if (level.equals(mLogLevel)) {
            return;
        }
        mLogLevel = level;
        LogcatConfig.setLogcatLevel(level);
        afterTextChanged(mSearchView.getText());
    }
//    @Override
//    public void onConfigurationChanged(Configuration newConfig) {
//        super.onConfigurationChanged(newConfig);
//        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
//            AutoSize.autoConvertDensityOfGlobal(this);
//        }
//    }
//    @Override
//    public boolean isBaseOnWidth() {
//        return false;
//    }
//
//    @Override
//    public float getSizeInDp() {
//        return 800;
//    }

    private class LogRunnable implements Runnable {

        private final LogcatInfo info;

        private LogRunnable(LogcatInfo info) {
            this.info = info;
        }

        @Override
        public void run() {
            if (mLogData.size() > 0) {
                LogcatInfo lastInfo = mLogData.get(mLogData.size() - 1);
                if (info.getLevel().equals(lastInfo.getLevel()) &&
                        info.getTag().equals(lastInfo.getTag())) {
                    lastInfo.addLog(info.getLog());
                    mAdapter.notifyDataSetChanged();
                    return;
                }
            }
            mLogData.add(info);
            String content = mSearchView.getText().toString();
            if ("".equals(content) && "V".equals(mLogLevel)) {
                mAdapter.addItem(info);
                return;
            }

            if (info.getLevel().equals(mLogLevel)) {
                if (info.getLog().contains(content) || info.getTag().contains(content)) {
                    mAdapter.addItem(info);
                }
            }
        }
    }

    /**
     * 初始化 Tag 过滤器
     */
    private void initFilter() {
        File file = new File(getExternalFilesDir(FILE_TYPE), LOGCAT_TAG_FILTER_FILE);
        if (file.exists() && file.isFile()) {
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new InputStreamReader(new FileInputStream(file),
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ? StandardCharsets.UTF_8 : Charset.forName("UTF-8")));
                String tag;
                while ((tag = reader.readLine()) != null) {
                    mTagFilter.add(tag);
                }
            } catch (IOException e) {
                toast(R.string.logcat_read_config_fail);
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException ignored) {}
                }
            }
        }
    }

    /**
     * 添加过滤的 TAG
     */
    private void addFilter(String tag) {
        mTagFilter.add(tag);
        BufferedWriter writer = null;
        try {
            File file = new File(getExternalFilesDir(FILE_TYPE), LOGCAT_TAG_FILTER_FILE);
            if (!file.isFile()) {
                file.delete();
            }
            if (!file.exists()) {
                file.createNewFile();
            }
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, false),
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ? StandardCharsets.UTF_8 : Charset.forName("UTF-8")));
            for (String temp : mTagFilter) {
                writer.write(temp + "\r\n");
            }
            writer.flush();

            // 从列表中删除关于这个 Tag 的日志
            ArrayList<LogcatInfo> removeData = new ArrayList<>();
            List<LogcatInfo> allData = mAdapter.getData();
            for (LogcatInfo info : allData) {
                if (info.getTag().equals(tag)) {
                    removeData.add(info);
                }
            }
            for (LogcatInfo info : removeData) {
                allData.remove(info);
                mAdapter.notifyDataSetChanged();
            }
            toast(getResources().getString(R.string.logcat_shield_succeed) + file.getPath());
        } catch (IOException e) {
            toast(R.string.logcat_shield_fail);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {}
            }
        }
    }

    /**
     * 保存日志到本地
     */
    private void saveLogToFile() {
        BufferedWriter writer = null;
        try {
            File directory = getExternalFilesDir(FILE_TYPE);
            if (!directory.isDirectory()) {
                directory.delete();
            }
            if (!directory.exists()) {
                directory.mkdirs();
            }
            File file = new File(directory, new SimpleDateFormat("yyyyMMdd_kkmmss", Locale.getDefault()).format(new Date()) + ".txt");
            if (!file.isFile()) {
                file.delete();
            }
            if (!file.exists()) {
                file.createNewFile();
            }
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, false), Charset.forName("UTF-8")));
            List<LogcatInfo> data = mAdapter.getData();
            for (LogcatInfo info : data) {
                writer.write(info.toString().replace("\n", "\r\n") + "\r\n\r\n");
            }
            writer.flush();
            toast(getResources().getString(R.string.logcat_save_succeed) + ":"+file.getPath());
        } catch (IOException e) {
            e.printStackTrace();
            toast(getResources().getString(R.string.logcat_save_fail));
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {}
            }
        }
    }

    /**
     * 吐司提示
     */
    private void toast(int stringId) {
        toast(getResources().getString(stringId));
    }

    private void toast(CharSequence text) {
        ToastUtils.show(text);
    }

    @Override
    public void onBackPressed() {
        // 移动到上一个任务栈
        moveTaskToBack(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        LogcatManager.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        LogcatManager.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LogcatManager.destroy();
    }
}