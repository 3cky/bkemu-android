package su.comp.bk.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

import su.comp.bk.R;
import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ListActivity;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityCompat.OnRequestPermissionsResultCallback;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatCallback;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.view.ActionMode;
import android.support.v7.view.ActionMode.Callback;
import android.support.v7.widget.Toolbar;
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

    private List<String> path = null;

    protected TextView pathTextView;
    protected EditText fileNameEditText;
    protected ProgressBar progressBar;

    private InputMethodManager inputManager;

    protected String startPath;
    protected String parentPath;
    protected String currentPath;
    protected String filename;

    public final static int REQUEST_CODE_ASK_PERMISSIONS = 0;

    public final static String[] FORMAT_FILTER_BIN_IMAGES = new String[] { ".BIN" };
    public final static String[] FORMAT_FILTER_DISK_IMAGES = new String[] { ".BKD", ".IMG" };
    private String[] formatFilter = null;

    private Mode mode;

    protected final HashMap<String, Integer> lastDirPositions = new HashMap<>();

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
            pathTextView.setText(getText(R.string.fd_location) + ": " + currentPath);
            fileList.notifyDataSetChanged();
            setListAdapter(fileList);
            boolean useAutoSelection = dirPath.length() < currentPath.length();
            Integer position = lastDirPositions.get(parentPath);
            if (position != null && useAutoSelection) {
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

        startPath = getIntent().getStringExtra(INTENT_START_PATH);
        if (startPath == null) {
            startPath = ROOT_PATH;
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
                String resultPath = new File(currentPath, filename).getPath();
                getIntent().putExtra(INTENT_RESULT_PATH, resultPath);
                setResult(RESULT_OK, getIntent());
                finish();
            });

            filename = new File(startPath).getName();
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
        // Save last accessed emulator image file parameters
        //outState.putString(LAST_BIN_IMAGE_FILE_URI, lastBinImageFileUri);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle inState) {
        // Restore last accessed emulator image file parameters
        //lastBinImageFileUri = inState.getString(LAST_BIN_IMAGE_FILE_URI);
        super.onRestoreInstanceState(inState);
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
        File dir = new File(currentPath, dirName);
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
        path = new ArrayList<>();
        ArrayList<HashMap<String, Object>> mList = new ArrayList<>();

        File f = new File(dirPath);
        if (!f.isDirectory()) {
            f = f.getParentFile();
        }
        currentPath = f.getPath();
        File[] files = f.listFiles();
        if (files == null) {
            currentPath = ROOT_PATH;
            f = new File(currentPath);
            files = f.listFiles();
        }

        if (!currentPath.equals(ROOT_PATH)) {
            addItem(mList, ROOT_PATH, R.drawable.ic_folder_white_24dp);
            path.add(ROOT_PATH);

            addItem(mList, "../", R.drawable.ic_folder_white_24dp);
            path.add(f.getParent());
            parentPath = f.getParent();
        }

        TreeMap<String, String> dirsMap = new TreeMap<>();
        TreeMap<String, String> dirsPathMap = new TreeMap<>();
        TreeMap<String, String> filesMap = new TreeMap<>();
        TreeMap<String, String> filesPathMap = new TreeMap<>();
        for (File file : files) {
            if (file.isDirectory()) {
                String dirName = file.getName();
                dirsMap.put(dirName, dirName);
                dirsPathMap.put(dirName, file.getPath());
            } else {
                final String fileName = file.getName();
                if (formatFilter != null) {
                    boolean isFormatMatched = isFileNameFormatMatched(fileName, formatFilter);
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
        path.addAll(dirsPathMap.tailMap("").values());
        path.addAll(filesPathMap.tailMap("").values());

        SimpleAdapter fileList = new SimpleAdapter(this, mList, R.layout.file_dialog_row,
                new String[] { ITEM_KEY, ITEM_IMAGE },
                new int[] { R.id.fd_row_text, R.id.fd_row_image });

        for (String dir : dirsMap.tailMap("").values()) {
            addItem(mList, dir, R.drawable.ic_folder_white_24dp);
        }

        for (String file : filesMap.tailMap("").values()) {
            addItem(mList, file, R.drawable.ic_description_white_24dp);
        }

        return fileList;
    }

    public static boolean isFileNameFormatMatched(final String fileName, final String[] formatExtensions) {
        final String fileNameLwr = fileName.toLowerCase();
        boolean isMatched = false;
        for (String formatExtension : formatExtensions) {
            final String formatLwr = formatExtension.toLowerCase();
            if (fileNameLwr.endsWith(formatLwr)) {
                isMatched = true;
                break;
            }
        }
        return isMatched;
    }

    private void addItem(ArrayList<HashMap<String, Object>> mList, String fileName, int imageId) {
        HashMap<String, Object> item = new HashMap<>();
        item.put(ITEM_KEY, fileName);
        item.put(ITEM_IMAGE, imageId);
        mList.add(item);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        File file = new File(path.get(position));
        setCreateVisibility(v, false);
        if (file.isDirectory()) {
            if (file.canRead()) {
                lastDirPositions.put(currentPath, position);
                new DirListTask().execute((path.get(position)));
            } else {
                new AlertDialog.Builder(this).setIcon(R.drawable.ic_folder_white_24dp)
                        .setTitle("[" + file.getName() + "] " + getText(R.string.fd_cant_read_dir))
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
        if (keyCode == KeyEvent.KEYCODE_BACK
                && currentPath != null
                && !currentPath.equals(ROOT_PATH)) {
            new DirListTask().execute(parentPath);
            return true;
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
