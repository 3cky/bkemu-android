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
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Objects;

public class FileUtils {
    /** Internal I/O buffer default size */
    private static final int BUFFER_SIZE = 8 * 1024;

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
        if (ContentResolver.SCHEME_CONTENT.equals(uriScheme)) {
            try (Cursor cursor = context.getContentResolver().query(uri,
                    null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            }
        }
        return (result != null) ? result : uri.getLastPathSegment();
    }

    /**
     * Get file length for given URI.
     * @param context context reference
     * @param uri file URI
     * @return file length or -1 if file length can't be get
     */
    public static long getUriFileLength(Context context, Uri uri) {
        long length = -1L;
        try (AssetFileDescriptor descriptor = context.getContentResolver()
                .openAssetFileDescriptor(uri, "r")) {
            if (descriptor != null) {
                length = descriptor.getLength();
            }
        } catch (Exception ignored) {
        }
        if (length < 0) {
            if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
                try (Cursor cursor = context.getContentResolver().query(uri,
                        null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        length = cursor.getLong(cursor.getColumnIndex(OpenableColumns.SIZE));
                    }
                }
            }
        }
        return length;
    }

    /**
     * Write data fully from InputStream to OutputStream.
     *
     * @param is InputStream to read
     * @param os OutputStream to write
     * @return number of bytes written
     * @throws IOException in case of I/O error
     */
    public static long writeFully(InputStream is, OutputStream os) throws IOException {
        return writeFully(is, os, BUFFER_SIZE);
    }

    /**
     * Write data fully from InputStream to OutputStream.
     *
     * @param is InputStream to read
     * @param os OutputStream to write
     * @param bufferSize buffer size to use
     * @return number of bytes written
     * @throws IOException in case of I/O error
     */
    public static long writeFully(InputStream is, OutputStream os, int bufferSize)
            throws IOException {
        int bytesRead;
        long bytesWritten = 0;
        byte[] buf = new byte[bufferSize];
        while (is.available() > 0) {
            bytesRead = is.read(buf, 0, buf.length);
            if (bytesRead > 0) {
                os.write(buf, 0, bytesRead);
                bytesWritten += bytesRead;
            }
        }
        return bytesWritten;
    }

    /**
     * Ellipsize file name, if max file name length is exceeded.
     *
     * @param fileName file name to ellipsize
     * @param maxLength max file name length
     * @param suffixLength max file name suffix length
     * @return ellipsized file name
     */
    public static String ellipsizeFileName(String fileName, int maxLength, int suffixLength) {
        if (fileName.length() > maxLength) {
            int nameDotIndex = fileName.lastIndexOf('.');
            if (nameDotIndex < 0) {
                nameDotIndex = fileName.length();
            }
            int nameSuffixIndex = nameDotIndex - suffixLength;
            int namePrefixIndex = maxLength - (fileName.length() - nameSuffixIndex);
            fileName = fileName.substring(0, namePrefixIndex).concat("...")
                    .concat(fileName.substring(nameSuffixIndex));
        }
        return fileName;
    }
}
