/*
 * Copyright (C) 2020 Victor Antonovich (v.antonovich@gmail.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package su.comp.bk.util;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.OpenableColumns;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FileUtils {
    /** Array of file extensions for binary images */
    public final static String[] FILE_EXT_BINARY_IMAGES = new String[] { ".BIN" };
    /** Array of file extensions for floppy disk images */
    public final static String[] FILE_EXT_FLOPPY_DISK_IMAGES = new String[] { ".BKD", ".IMG" };

    /**
     * Get tape file name as string from its 16 bytes array presentation.
     * @param fileNameData internal file name array data
     * @return string file name presentation
     */
    public static String getTapeFileName(byte[] fileNameData) {
        String fileName;
        if (fileNameData[0] != 0) { // BK0011 flag for any file
            try {
                fileName = new String(fileNameData, "koi8-r");
            } catch (UnsupportedEncodingException e) {
                fileName = new String(fileNameData);
            }
            fileName = fileName.trim().toUpperCase();
            // Strip spaces before extension (like in "NAME  .COD" in Basic)
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex > 0) {
                fileName = fileName.substring(0, dotIndex).trim().concat(
                        fileName.substring(dotIndex));
            }
        } else {
            fileName = "";
        }
        return fileName;
    }

    /**
     * Get local file URI for given URI. If URI is not local,
     * its content will be cached to local file.
     * @param context context reference
     * @param uriString URI as string
     * @return local file URI for given URI
     * @throws IOException if given URI content can't be read
     */
    public static Uri getLocalFileUri(Context context, String uriString) throws IOException {
        // TODO check URI is local, cache locally otherwise
        return Uri.parse(uriString);
    }

    private static void writeUriContentToStream(Context context, Uri uri, OutputStream stream)
            throws IOException {
        ContentResolver contentResolver = context.getContentResolver();
        try (BufferedInputStream bufferedInputStream = new BufferedInputStream(
                Objects.requireNonNull(contentResolver.openInputStream(uri)))) {
            int readByte;
            while ((readByte = bufferedInputStream.read()) != -1) {
                stream.write(readByte);
            }
        }
    }

    /**
     * Get URI content as byte array.
     * @param context context reference
     * @param uri URI to get content
     * @return read URI content as byte array
     * @throws IOException if given URI content can't be read
     */
    public static byte[] getUriContentData(Context context, Uri uri)
            throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        writeUriContentToStream(context, uri, byteArrayOutputStream);
        return byteArrayOutputStream.toByteArray();
    }

    /**
     * Checks is file name matches some of given extensions (ignoring case).
     * @param fileName file name to check
     * @param fileExtensions array of file extensions
     * @return true if some of extensions matched for given file name, false otherwise
     */
    public static boolean isFileNameExtensionMatched(final String fileName,
                                                     final String[] fileExtensions) {
        boolean isMatched = false;
        if (fileName != null) {
            final String fileNameLwr = fileName.toLowerCase();
            for (String fileExtension : fileExtensions) {
                final String formatLwr = fileExtension.toLowerCase();
                if (fileNameLwr.endsWith(formatLwr)) {
                    isMatched = true;
                    break;
                }
            }
        }
        return isMatched;
    }

    /**
     * Resolve file name for given URI.
     * @param context context reference
     * @param uri file URI
     * @return resolved file name or null if file name can't be resolved
     */
    public static String resolveUriFileName(Context context, Uri uri) {
        String result = null;
        String uriScheme = uri.getScheme();
        if (uriScheme != null && uriScheme.equals("content")) {
            try (Cursor cursor = context.getContentResolver().query(uri,
                    null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            }
        }
        return (result != null) ? result : uri.getLastPathSegment();
    }
}
