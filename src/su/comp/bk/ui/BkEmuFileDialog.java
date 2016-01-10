package su.comp.bk.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

import su.comp.bk.R;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
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
public class BkEmuFileDialog extends ListActivity {

    private static final String ITEM_KEY = "key";
    private static final String ITEM_IMAGE = "image";

    private static final String ROOT_PATH = "/";

    public static final String INTENT_START_PATH = "START_PATH";
    public static final String INTENT_FORMAT_FILTER = "FORMAT_FILTER";
    public static final String INTENT_RESULT_PATH = "RESULT_PATH";

    private List<String> path = null;

    protected TextView pathTextView;
    protected EditText fileNameEditText;
    protected ProgressBar progressBar;

    private InputMethodManager inputManager;

    private LinearLayout layoutCreate;

    protected String parentPath;
    protected String currentPath;

    public final static String[] FORMAT_FILTER_BIN_IMAGES = new String[] { ".BIN" };
    public final static String[] FORMAT_FILTER_DISK_IMAGES = new String[] { ".BKD", ".IMG" };
    private String[] formatFilter = null;

    protected HashMap<String, Integer> lastDirPositions = new HashMap<String, Integer>();

    class DirListTask extends AsyncTask<String, Void, SimpleAdapter> {
        public static final long PROGRESS_BAR_DELAY = 500l;
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

        setContentView(R.layout.file_dialog);

        getListView().setFastScrollEnabled(true);

        pathTextView = (TextView) findViewById(R.id.path);
        fileNameEditText = (EditText) findViewById(R.id.fd_edit_text_file);
        progressBar = (ProgressBar) findViewById(R.id.fd_progress_bar);

        inputManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);

        layoutCreate = (LinearLayout) findViewById(R.id.fd_layout_create);
        layoutCreate.setVisibility(View.GONE);

        formatFilter = getIntent().getStringArrayExtra(INTENT_FORMAT_FILTER);
        if (formatFilter == null) {
            formatFilter = FORMAT_FILTER_BIN_IMAGES;
        }
        setTitle(getResources().getString(R.string.fd_title, getFormatFilterString(formatFilter)));

        final Button cancelButton = (Button) findViewById(R.id.fd_btn_cancel);
        cancelButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setCreateVisibility(v, false);
            }
        });

        final Button createButton = (Button) findViewById(R.id.fd_btn_create);
        createButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (fileNameEditText.getText().length() > 0) {
                    getIntent().putExtra(INTENT_RESULT_PATH, currentPath +
                            "/" + fileNameEditText.getText());
                    setResult(RESULT_OK, getIntent());
                    finish();
                }
            }
        });

        String startPath = getIntent().getStringExtra(INTENT_START_PATH);
        startPath = (startPath != null) ? startPath : ROOT_PATH;

        new DirListTask().execute(startPath);
    }

    private static String getFormatFilterString(String[] formatFilterArray) {
        StringBuffer formatFilterStringBuf = new StringBuffer();
        for (int idx = 0; idx < formatFilterArray.length; idx++) {
            formatFilterStringBuf.append(formatFilterArray[idx]);
            if (idx < (formatFilterArray.length - 1)) {
                formatFilterStringBuf.append(", ");
            }
        }
        return formatFilterStringBuf.toString();
    }

    protected SimpleAdapter getDirList(final String dirPath) {
        currentPath = dirPath;

        final List<String> item = new ArrayList<String>();
        path = new ArrayList<String>();
        ArrayList<HashMap<String, Object>> mList = new ArrayList<HashMap<String, Object>>();

        File f = new File(currentPath);
        File[] files = f.listFiles();
        if (files == null) {
            currentPath = ROOT_PATH;
            f = new File(currentPath);
            files = f.listFiles();
        }

        if (!currentPath.equals(ROOT_PATH)) {
            item.add(ROOT_PATH);
            addItem(mList, ROOT_PATH, R.drawable.ic_folder_white_24dp);
            path.add(ROOT_PATH);

            item.add("../");
            addItem(mList, "../", R.drawable.ic_folder_white_24dp);
            path.add(f.getParent());
            parentPath = f.getParent();
        }

        TreeMap<String, String> dirsMap = new TreeMap<String, String>();
        TreeMap<String, String> dirsPathMap = new TreeMap<String, String>();
        TreeMap<String, String> filesMap = new TreeMap<String, String>();
        TreeMap<String, String> filesPathMap = new TreeMap<String, String>();
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
        item.addAll(dirsMap.tailMap("").values());
        item.addAll(filesMap.tailMap("").values());
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
        for (int i = 0; i < formatExtensions.length; i++) {
            final String formatLwr = formatExtensions[i].toLowerCase();
            if (fileNameLwr.endsWith(formatLwr)) {
                isMatched = true;
                break;
            }
        }
        return isMatched;
    }

    private void addItem(ArrayList<HashMap<String, Object>> mList, String fileName, int imageId) {
        HashMap<String, Object> item = new HashMap<String, Object>();
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
                new AlertDialog.Builder(this).setIcon(R.drawable.icon)
                        .setTitle("[" + file.getName() + "] " + getText(R.string.fd_cant_read_folder))
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                }).show();
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
        if (keyCode == KeyEvent.KEYCODE_BACK && !currentPath.equals(ROOT_PATH)) {
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
        layoutCreate.setVisibility(isCreateVisible ? View.VISIBLE : View.GONE);
        inputManager.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }
}
