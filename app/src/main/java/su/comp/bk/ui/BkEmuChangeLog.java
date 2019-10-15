/*
 * Created: 26.09.2012
 *
 * Copyright (C) 2012 Victor Antonovich (v.antonovich@gmail.com)
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package su.comp.bk.ui;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;

import su.comp.bk.R;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.webkit.WebView;

/**
 * Provides change log dialog (with last changes information or full change log).
 */
public class BkEmuChangeLog {

    protected static final String TAG = BkEmuChangeLog.class.getName();

    private static final String XML_TAG_RELEASES = "releases";
    private static final String XML_TAG_RELEASE = "release";
    private static final String XML_TAG_CHANGES = "changes";
    private static final String XML_TAG_CHANGE = "change";
    private static final String XML_ATTR_VERSION = "version";
    private static final String XML_ATTR_DATE = "date";

    private final Context context;

    private static final String PREFS_KEY_LAST_VERSION_NAME = "su.comp.bk.ui.p:LAST_VERSION";

    // Last application run version name (like "0.1.0", or empty string if this
    // is first application run)
    private String lastVersionName;
    // Current application run version name (like "0.1.1")
    private String currentVersionName;

    /**
     * Change log constructor.
     * @param context context of application
     */
    public BkEmuChangeLog(Context context) {
        this.context = context;
        // Get current version name from manifest
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        setLastVersionName(prefs.getString(PREFS_KEY_LAST_VERSION_NAME, ""));
        Log.d(TAG, "BkEmu last version: " + getLastVersionName());
        // Get stored last version name
        try {
            setCurrentVersionName(context.getPackageManager().getPackageInfo(
                    context.getPackageName(), 0).versionName);
        } catch (NameNotFoundException e) {
            Log.e(TAG, "can't get version name", e);
            setCurrentVersionName("");
        }
        Log.d(TAG, "BkEmu current version: " + getCurrentVersionName());
    }

    public String getLastVersionName() {
        return lastVersionName;
    }

    public void setLastVersionName(String versionName) {
        this.lastVersionName = versionName;
    }

    public String getCurrentVersionName() {
        return currentVersionName;
    }

    public void setCurrentVersionName(String versionName) {
        this.currentVersionName = versionName;
    }

    public void saveCurrentVersionName() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor prefsEditor = prefs.edit();
        prefsEditor.putString(PREFS_KEY_LAST_VERSION_NAME, getCurrentVersionName());
        prefsEditor.apply();
    }

    public boolean isFirstRun() {
        return getLastVersionName().length() == 0;
    }

    public boolean isCurrentVersionGreaterThanLast() {
        return isFirstVersionGreaterThanSecond(getCurrentVersionName(), getLastVersionName());
    }

    protected static boolean isFirstVersionGreaterThanSecond(String firstVersionName,
            String secondVersionName) {
        return compareVersionNames(firstVersionName, secondVersionName) > 0;
    }

    protected static int compareVersionNames(String firstVersionName, String secondVersionName) {
        return firstVersionName.compareTo(secondVersionName);
    }

    /**
     * Get change log dialog.
     * @param isFullChangelog <code>true</code> to get full change log, <code>false</code>
     * to get changes from last version
     * @return change log {@link Dialog}
     */
    public Dialog getDialog(boolean isFullChangelog) {
        WebView dialogView = new WebView(this.context);
        dialogView.setBackgroundColor(0);
        String logData = this.getLogText(isFullChangelog);
        dialogView.loadDataWithBaseURL(null, logData, "text/html", "UTF-8", null);
        AlertDialog.Builder builder = new AlertDialog.Builder(this.context);
        builder.setTitle(context.getResources().getString(isFullChangelog
                    ? R.string.changelog_title_full : R.string.changelog_title))
                .setView(dialogView)
                .setCancelable(true)
                .setPositiveButton(context.getResources().getString(R.string.ok),
                        (dialog, id) -> dialog.cancel());
        if (!isFullChangelog) {
            builder.setNegativeButton(R.string.changelog_show_full,
                    (dialog, id) -> {
                        dialog.cancel();
                        getDialog(true).show();
                    });
        }
        return builder.create();
    }

    private String getLogText(boolean isFullChangelog) {
        InputStream changelogTemplateStream = context.getResources()
                .openRawResource(R.raw.changelog_template);
        BufferedReader changelogTemplateReader = new BufferedReader(
                new InputStreamReader(changelogTemplateStream));
        Template changelogTemplate = Mustache.compiler().compile(changelogTemplateReader);
        return changelogTemplate.execute(getLogData(isFullChangelog));
    }

    @SuppressWarnings("null")
    private Object getLogData(boolean isFullChangelog) {
        Map<String, Object> changelogTemplateData = null;
        List<Map<String, Object>> releasesList = null;
        Map<String, Object> releaseData = null;
        List<String> releaseChangesList = null;
        try {
            XmlPullParserFactory xmlParserFactory = XmlPullParserFactory.newInstance();
            xmlParserFactory.setNamespaceAware(true);
            XmlPullParser xmlParser = xmlParserFactory.newPullParser();
            InputStream changelogXmlStream = context.getResources()
                    .openRawResource(R.raw.changelog_data);
            xmlParser.setInput(changelogXmlStream, null);
            int eventType = xmlParser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                String tagName;
                switch (eventType) {
                    case XmlPullParser.START_DOCUMENT:
                        changelogTemplateData = new HashMap<>();
                        break;
                    case XmlPullParser.START_TAG:
                        tagName = xmlParser.getName();
                        if (XML_TAG_RELEASES.equalsIgnoreCase(tagName)) {
                            releasesList = new ArrayList<>();
                        } else if (XML_TAG_RELEASE.equalsIgnoreCase(tagName)) {
                            releaseData = new HashMap<>();
                            releaseData.put("version", xmlParser
                                    .getAttributeValue(null, XML_ATTR_VERSION));
                            String date = xmlParser
                                    .getAttributeValue(null, XML_ATTR_DATE);
                            boolean hasDate = (date != null);
                            releaseData.put("hasDate", hasDate);
                            if (hasDate) {
                                releaseData.put("date", date);
                            }
                        } else if (XML_TAG_CHANGES.equalsIgnoreCase(tagName)) {
                            releaseChangesList = new ArrayList<>();
                        } else if (XML_TAG_CHANGE.equalsIgnoreCase(tagName)) {
                            releaseChangesList.add(xmlParser.nextText());
                        } else {
                            Log.w(TAG, "unknown changelog XML start tag: '" + tagName + "'");
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        tagName = xmlParser.getName();
                        if (XML_TAG_RELEASES.equalsIgnoreCase(tagName)) {
                            changelogTemplateData.put("releases", releasesList);
                        } else if (XML_TAG_RELEASE.equalsIgnoreCase(tagName)) {
                            if (isFullChangelog || isFirstVersionGreaterThanSecond(
                                    (String) releaseData.get("version"),
                                    getLastVersionName())) {
                                releasesList.add(releaseData);
                            }
                        } else if (XML_TAG_CHANGES.equalsIgnoreCase(tagName)) {
                            releaseData.put("changes", releaseChangesList);
                        } else if (XML_TAG_CHANGE.equalsIgnoreCase(tagName)) {
                            // Do nothing
                        } else {
                            Log.w(TAG, "unknown changelog XML end tag: '" + tagName + "'");
                        }
                        break;
                    default:
                        break;
                }
                eventType = xmlParser.next();
            }
        } catch (Exception e) {
            Log.e(TAG, "can't parse changelog XML data", e);
        }
        return changelogTemplateData;
    }
}
