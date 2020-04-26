package su.comp.bk.ui;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;

import su.comp.bk.R;
import su.comp.bk.util.FileUtils;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ListActivity;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatCallback;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.view.ActionMode.Callback;
import androidx.appcompat.widget.Toolbar;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleAdapter;
import android.widget.TextView;

/**
 * Emulator image file selector dialog.
 * Based on code from http://code.google.com/p/android-file-dialog/ project.
 */
public class BkEmuFileDialog extends ListActivity implements AppCompatCallback,
        OnRequestPermissionsResultCallback {

    private static final String ITEM_KEY = "key";
    private static final String ITEM_IMAGE = "image";

    private static final String ROOT_PATH = "/";

    public enum Mode {
        LOAD,
        SAVE
    }

    public static final String INTENT_PREFIX = BkEmuFileDialog.class.getName();
    public static final String INTENT_MODE = INTENT_PREFIX + "#MODE";
    public static final String INTENT_START_PATH = INTENT_PREFIX + "#START_PATH";
    public static final String INTENT_FORMAT_FILTER = INTENT_PREFIX + "#FORMAT_FILTER";
    public static final String INTENT_RESULT_PATH = INTENT_PREFIX + "#RESULT_PATH";

    // State save/restore: File name
    private static final String STATE_FILE_NAME = BkEmuFileDialog.class.getName() + "#file_name";
    // State save/restore: Directory navigation history
    private static final String STATE_DIR_HISTORY = BkEmuFileDialog.class.getName() + "#dir_history";
    // State save/restore: Last navigated directory positions
    private static final String STATE_LAST_DIR_POSITIONS = BkEmuFileDialog.class.getName() + "#last_dir_positions";

    protected TextView pathTextView;
    protected EditText fileNameEditText;
    protected ProgressBar progressBar;

    private InputMethodManager inputManager;

    private final LinkedList<String> dirHistory = new LinkedList<>();
    private List<String> currentDirElements = null;
    protected String startPath;
    protected String filename;

    private final HashMap<String, Integer> lastDirPositions = new HashMap<>();

    public final static int REQUEST_CODE_ASK_PERMISSIONS = 0;

    public final static String[] FORMAT_FILTER_BIN_IMAGES = new String[] { ".BIN" };
    public final static String[] FORMAT_FILTER_DISK_IMAGES = new String[] { ".BKD", ".IMG" };
    private String[] formatFilter = null;

    private Mode mode;

    private AppCompatDelegate delegate;

    class DirListTask extends AsyncTask<String, Void, SimpleAdapter> {
        public static final long PROGRESS_BAR_DELAY = 500L;
        private CountDownTimer progressBarTimer;
        private String dirPath;

        @Override
        protected void onPreExecute() {
            progressBarTimer = new CountDownTimer(PROGRESS_BAR_DELAY, PROGRESS_BAR_DELAY + 1) {
                @Override
                public void onTick(long millisUntilFinished) {
                    // Do nothing
                }
                @Override
                public void onFinish() {
                    progressBar.setVisibility(View.VISIBLE);
                }
            };
            progressBarTimer.start();
        }

        @Override
        protected SimpleAdapter doInBackground(String... dirPaths) {
            dirPath = dirPaths[0];
            return getDirList(dirPath);
        }

        @Override
        protected void onPostExecute(SimpleAdapter fileList) {
            progressBarTimer.cancel();
            progressBar.setVisibility(View.INVISIBLE);
            String currentDir = peekDirFromHistory();
            pathTextView.setText(getText(R.string.fd_location) + ": " + currentDir);
            fileList.notifyDataSetChanged();
            setListAdapter(fileList);
            Integer position = getLastDirPosition(currentDir);
            if (position != null) {
                getListView().setSelection(position);
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED, getIntent());

        delegate = AppCompatDelegate.create(this, this);
        delegate.onCreate(savedInstanceState);
        delegate.setContentView(R.layout.file_dialog);
        Toolbar toolbar = findViewById(R.id.fd_toolbar);
        delegate.setSupportActionBar(toolbar);

        inputManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);

        getListView().setFastScrollEnabled(true);

        pathTextView = findViewById(R.id.path);
        fileNameEditText = findViewById(R.id.fd_edit_text_filename);
        progressBar = findViewById(R.id.fd_progress_bar);

        LinearLayout layoutFilename = findViewById(R.id.fd_layout_filename);
        Button saveButton = findViewById(R.id.fd_btn_save);
        Button createDirButton = findViewById(R.id.fd_btn_create_dir);

        if (savedInstanceState != null) {
            String[] values = savedInstanceState.getStringArray(STATE_DIR_HISTORY);
            if (values != null) {
                dirHistory.addAll(Arrays.asList(values));
            }
            Serializable data = savedInstanceState.getSerializable(STATE_LAST_DIR_POSITIONS);
            if (data != null) {
                lastDirPositions.putAll((HashMap<String, Integer>) data);
            }
        }

        if (isDirHistoryNotEmpty()) {
            startPath = popDirFromHistory();
        } else {
            startPath = getIntent().getStringExtra(INTENT_START_PATH);
            if (startPath == null) {
                startPath = Environment.getExternalStorageDirectory().getPath();
            }
        }

        mode = (Mode) getIntent().getSerializableExtra(INTENT_MODE);
        if (mode == Mode.LOAD) {
            layoutFilename.setVisibility(View.GONE);
            saveButton.setVisibility(View.GONE);
            createDirButton.setVisibility(View.GONE);
            formatFilter = getIntent().getStringArrayExtra(INTENT_FORMAT_FILTER);
            if (formatFilter == null) {
                formatFilter = FORMAT_FILTER_BIN_IMAGES;
            }
            delegate.setTitle(getResources().getString(R.string.fd_title_load,
                    getFormatFilterString(formatFilter)));
        } else {
            layoutFilename.setVisibility(View.GONE); // FIXME probably file name editor should be deleted at all
            createDirButton.setVisibility(View.VISIBLE);
            createDirButton.setOnClickListener(v -> showCreateDirDialog());
            saveButton.setVisibility(View.VISIBLE);
            saveButton.setOnClickListener(v -> {
                String resultPath = new File(peekDirFromHistory(), filename).getPath();
                getIntent().putExtra(INTENT_RESULT_PATH, resultPath);
                setResult(RESULT_OK, getIntent());
                finish();
            });

            if (savedInstanceState != null) {
                filename = savedInstanceState.getString(STATE_FILE_NAME);
            }
            if (filename == null)  {
                filename = new File(startPath).getName();
            }
            formatFilter = new String[] { filename };
            delegate.setTitle(getResources().getString(R.string.fd_title_save, filename));
        }

        final Button cancelButton = findViewById(R.id.fd_btn_cancel);
        cancelButton.setOnClickListener(v -> finish());

        showStartPathWithPermissionsCheck();
    }

    protected void showStartPathWithPermissionsCheck() {
        int hasExternalStorageAccessPermissions = ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (hasExternalStorageAccessPermissions != PackageManager.PERMISSION_GRANTED) {
            if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                showPermissionsRationaleDialog();
            } else {
                requestPermissions();
            }
        } else {
            new DirListTask().execute(startPath);
        }
    }

    protected void showPermissionsRationaleDialog() {
        new AlertDialog.Builder(this)
            .setMessage(R.string.fd_permissions_rationale)
            .setPositiveButton(R.string.ok, (dialog, which) -> requestPermissions())
            .setNegativeButton(R.string.cancel, (dialog, which) -> finish())
            .setIcon(R.drawable.ic_folder_white_24dp)
            .show();
    }

    protected void requestPermissions() {
        ActivityCompat.requestPermissions(BkEmuFileDialog.this,
                new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE },
                REQUEST_CODE_ASK_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ASK_PERMISSIONS: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, show files
                    new DirListTask().execute(startPath);
                } else {
                    // permission denied, close activity
                    finish();
                }
                break;
            }
            default:
                // unknown request code, close activity
                finish();
                break;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(STATE_FILE_NAME, filename);
        outState.putStringArray(STATE_DIR_HISTORY, dirHistory.toArray(new String[0]));
        outState.putSerializable(STATE_LAST_DIR_POSITIONS, lastDirPositions);
        super.onSaveInstanceState(outState);
    }

    protected void pushDirToHistory(String dir) {
        dirHistory.push(dir);
    }

    protected String peekDirFromHistory() {
        return dirHistory.peek();
    }

    protected String popDirFromHistory() {
        return dirHistory.pop();
    }

    protected boolean isDirHistoryNotEmpty() {
        return !dirHistory.isEmpty();
    }

    protected void setLastDirPosition(String dir, int position) {
        lastDirPositions.put(dir, position);
    }

    protected Integer getLastDirPosition(String dir) {
        return lastDirPositions.get(dir);
    }

    @SuppressLint("InflateParams")
    protected void showCreateDirDialog() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        dialogBuilder.setTitle(R.string.fd_create_dir_title);
        final View createDirView = LayoutInflater.from(this).inflate(R.layout.file_dialog_create_dir, null);
        dialogBuilder.setView(createDirView);
        dialogBuilder.setPositiveButton(R.string.ok, (dialog, which) -> {
            EditText dirNameEditText = createDirView.findViewById(R.id.fd_create_dir_name);
            String dirName = dirNameEditText.getText().toString().trim();
            if (!dirName.isEmpty()) {
                createDir(dirName);
            }
        });
        dialogBuilder.setNegativeButton(R.string.cancel, null);
        dialogBuilder.setIcon(R.drawable.ic_folder_white_24dp);
        dialogBuilder.show();
    }

    protected void createDir(String dirName) {
        File dir = new File(peekDirFromHistory(), dirName);
        if (dir.mkdir()) {
            new DirListTask().execute(dir.getPath());
        } else {
            new AlertDialog.Builder(this).setIcon(R.drawable.ic_folder_white_24dp)
                .setTitle("[" + dirName + "] " + getText(R.string.fd_cant_create_dir))
                .setPositiveButton("OK", (dialog, which) -> {}).show();
        }
    }

    private static String getFormatFilterString(String[] formatFilterArray) {
        StringBuilder formatFilterStringBuilder = new StringBuilder();
        for (int idx = 0; idx < formatFilterArray.length; idx++) {
            formatFilterStringBuilder.append(formatFilterArray[idx]);
            if (idx < (formatFilterArray.length - 1)) {
                formatFilterStringBuilder.append(", ");
            }
        }
        return formatFilterStringBuilder.toString();
    }

    protected SimpleAdapter getDirList(final String dirPath) {
        File[] files = null;
        currentDirElements = new ArrayList<>();
        ArrayList<HashMap<String, Object>> dirItems = new ArrayList<>();

        File f = new File(dirPath);
        if (!f.isDirectory()) {
            f = f.getParentFile();
        }
        if (!FileUtils.isDirectoryReadable(f)) {
            f = Environment.getExternalStorageDirectory();
        }
        if (!FileUtils.isDirectoryReadable(f)) {
            f = getApplicationContext().getExternalFilesDir(null);
        }

        if (f != null) {
            files = f.listFiles();
            String currentPath = f.getPath();
            pushDirToHistory(currentPath);
            if (!currentPath.equals(ROOT_PATH)) {
                if (FileUtils.isDirectoryReadable(ROOT_PATH)) {
                    addDirItem(dirItems, ROOT_PATH, R.drawable.ic_folder_white_24dp);
                    currentDirElements.add(ROOT_PATH);
                }
                if (FileUtils.isDirectoryReadable(f.getParent())) {
                    addDirItem(dirItems, "../", R.drawable.ic_folder_white_24dp);
                    currentDirElements.add(f.getParent());
                }
            }
        }

        TreeMap<String, String> dirsMap = new TreeMap<>();
        TreeMap<String, String> dirsPathMap = new TreeMap<>();
        TreeMap<String, String> filesMap = new TreeMap<>();
        TreeMap<String, String> filesPathMap = new TreeMap<>();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    String dirName = file.getName();
                    dirsMap.put(dirName, dirName);
                    dirsPathMap.put(dirName, file.getPath());
                } else {
                    final String fileName = file.getName();
                    if (formatFilter != null) {
                        boolean isFormatMatched = FileUtils.isFileNameFormatMatched(fileName, formatFilter);
                        if (isFormatMatched) {
                            filesMap.put(fileName, fileName);
                            filesPathMap.put(fileName, file.getPath());
                        }
                    } else {
                        filesMap.put(fileName, fileName);
                        filesPathMap.put(fileName, file.getPath());
                    }
                }
            }
        }
        currentDirElements.addAll(dirsPathMap.tailMap("").values());
        currentDirElements.addAll(filesPathMap.tailMap("").values());

        SimpleAdapter fileList = new SimpleAdapter(this, dirItems, R.layout.file_dialog_row,
                new String[] { ITEM_KEY, ITEM_IMAGE },
                new int[] { R.id.fd_row_text, R.id.fd_row_image });

        for (String dir : dirsMap.tailMap("").values()) {
            addDirItem(dirItems, dir, R.drawable.ic_folder_white_24dp);
        }

        for (String file : filesMap.tailMap("").values()) {
            addDirItem(dirItems, file, R.drawable.ic_description_white_24dp);
        }

        return fileList;
    }

    private void addDirItem(ArrayList<HashMap<String, Object>> dirItems, String itemName, int itemImageId) {
        HashMap<String, Object> item = new HashMap<>();
        item.put(ITEM_KEY, itemName);
        item.put(ITEM_IMAGE, itemImageId);
        dirItems.add(item);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        File file = new File(currentDirElements.get(position));
        setCreateVisibility(v, false);
        if (file.isDirectory()) {
            if (file.canRead()) {
                setLastDirPosition(peekDirFromHistory(), position);
                new DirListTask().execute((currentDirElements.get(position)));
            } else {
                new AlertDialog.Builder(this).setIcon(R.drawable.ic_folder_white_24dp)
                        .setTitle("[" + file.getPath() + "] " + getText(R.string.fd_cant_read_dir))
                        .setPositiveButton("OK", (dialog, which) -> {}).show();
            }
        } else {
            v.setSelected(true);
            getIntent().putExtra(INTENT_RESULT_PATH, file.getPath());
            setResult(RESULT_OK, getIntent());
            finish();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && isDirHistoryNotEmpty()) {
            popDirFromHistory();
            if (isDirHistoryNotEmpty()) {
                new DirListTask().execute(popDirFromHistory());
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * Set create menu visibility state.
     * @param v action button view reference
     * @param isCreateVisible <code>true</code> to set visible, <code>false</code> to set invisible
     */
    protected void setCreateVisibility(View v, boolean isCreateVisible) {
        inputManager.hideSoftInputFromWindow(v.getWindowToken(), 0); // FIXME
    }

    @Override
    public void onSupportActionModeFinished(ActionMode arg0) {
        // Do nothing
    }

    @Override
    public void onSupportActionModeStarted(ActionMode arg0) {
        // Do nothing
    }

    @Override
    public ActionMode onWindowStartingSupportActionMode(Callback arg0) {
        return null;
    }
}
