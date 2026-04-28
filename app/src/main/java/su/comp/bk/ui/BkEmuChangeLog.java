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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.preference.PreferenceManager;
import android.webkit.WebView;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;

import org.yaml.snakeyaml.Yaml;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import su.comp.bk.R;
import timber.log.Timber;

/**
 * Provides change log dialog (with last changes information or full change log).
 */
public class BkEmuChangeLog {
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
        Timber.d("BkEmu last version: %s", getLastVersionName());
        // Get stored last version name
        try {
            setCurrentVersionName(context.getPackageManager().getPackageInfo(
                    context.getPackageName(), 0).versionName);
        } catch (NameNotFoundException e) {
            Timber.e(e, "can't get version name");
            setCurrentVersionName("");
        }
        Timber.d("BkEmu current version: %s", getCurrentVersionName());
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
        return getLastVersionName().isEmpty();
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

    @SuppressWarnings("unchecked")
    private Object getLogData(boolean isFullChangelog) {
        try {
            InputStream changelogYamlStream = context.getResources()
                    .openRawResource(R.raw.changelog_data);
            Map<String, Object> yamlData = new Yaml().load(changelogYamlStream);
            List<Map<String, Object>> yamlReleases =
                    (List<Map<String, Object>>) yamlData.get("releases");
            List<Map<String, Object>> releasesList = new ArrayList<>();
            for (Map<String, Object> yamlRelease : yamlReleases) {
                String version = (String) yamlRelease.get("version");
                if (isFullChangelog || isFirstVersionGreaterThanSecond(version, getLastVersionName())) {
                    Map<String, Object> releaseData = new HashMap<>(yamlRelease);
                    releaseData.put("hasDate", releaseData.containsKey("date"));
                    releasesList.add(releaseData);
                }
            }
            Map<String, Object> templateData = new HashMap<>();
            templateData.put("releases", releasesList);
            return templateData;
        } catch (Exception e) {
            Timber.e(e, "can't parse changelog YAML data");
            return null;
        }
    }
}
