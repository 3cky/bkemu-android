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
import android.net.Uri;
import android.os.Environment;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.Objects;

public class FileUtils {
    /**
     * Replace last path element with given new element.
     * @param path path to replace element
     * @param newElement new element to replace last path element
     * @return path with replaced last element
     */
    public static String replaceLastPathElement(String path, String newElement) {
        int pos = path.lastIndexOf('/');
        if (pos < 0) {
            throw new IllegalArgumentException("Path contains no elements to replace: " + path);
        }
        return path.substring(0, pos + 1).concat(newElement);
    }

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
     * Get safe directory path for given file URI.
     * @param fileUriString file URI as string or <code>null</code>
     * @return directory path or external storage path if file URI is <code>null</code>
     * or given file directory doesn't exist
     */
    public static String getSafeFileUriDirectoryPath(String fileUriString) {
        String fileUriPath = null;
        if (fileUriString != null) {
            Uri fileUri = Uri.parse(fileUriString);
            fileUriPath = fileUri.getPath();
        }
        return getSafeFileDirectoryPath(fileUriPath);
    }

    /**
     * Get safe directory path for given file path.
     * @param filePathString file path or <code>null</code>
     * @return directory path or external storage path if file URI is <code>null</code>
     * or given file directory doesn't exist
     */
    public static String getSafeFileDirectoryPath(String filePathString) {
        String directoryPath = Environment.getExternalStorageDirectory().getPath();
        if (filePathString != null) {
            File filePath = new File(filePathString);
            File fileDir = filePath.getParentFile();
            if (fileDir != null && fileDir.isDirectory()) {
                directoryPath = fileDir.getPath();
            }
        }
        return directoryPath;
    }

    /**
     * Get local file for given URI. If URI scheme is not "file',
     * its content will be cached to local file.
     * @param context context reference
     * @param uriString URI as string
     * @return local file for given URI
     * @throws IOException if given URI content can't be read
     */
    public static File getUriLocalFile(Context context, String uriString)
            throws IOException {
        File file = null;
        Uri uri = Uri.parse(uriString);
        if ("file".equals(uri.getScheme())) {
            // Open file directly
            file = new File(URI.create(uriString));
        } else {
            // Cache resource from given URI to file
            file = getUriCachedContentFile(context, uri);
        }
        return file;
    }

    private static File getUriCachedContentFile(Context context, Uri uri)
            throws IOException {
        String filePrefix = "bkemu";
        String fileSuffix = null;
        String fileName = uri.getLastPathSegment();
        if (fileName != null) {
            int pos = fileName.lastIndexOf('.');
            if (pos > 0) {
                filePrefix = fileName.substring(0, pos);
                fileSuffix = fileName.substring(pos + 1);
            } else {
                filePrefix = fileName;
            }
        }
        File fileDir = context.getCacheDir();
        System.out.println("getCacheDir: " + fileDir);
        File file = File.createTempFile(filePrefix, fileSuffix, fileDir);
        writeUriContentToFile(context, uri, file);
        return file;
    }

    private static void writeUriContentToFile(Context context, Uri uri, File file)
            throws IOException {
        try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
            writeUriContentToStream(context, uri, fileOutputStream);
        }
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
     * @param uriString URI as string
     * @return read URI content as byte array
     * @throws IOException if given URI content can't be read
     */
    public static byte[] getUriContentData(Context context, String uriString)
            throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        writeUriContentToStream(context, Uri.parse(uriString), byteArrayOutputStream);
        return byteArrayOutputStream.toByteArray();
    }

    public static boolean isDirectoryReadable(String dirName) {
        if (dirName == null) {
            return false;
        }
        return isDirectoryReadable(new File(dirName));
    }

    public static boolean isDirectoryReadable(File dir) {
        if (dir == null) {
            return false;
        }
        return dir.exists() && dir.listFiles() != null;
    }

    /**
     * Checks is file name matches some of given format extensions (ignoring case).
     * @param fileName file name to check
     * @param formatExtensions array of file format extensions
     * @return true if some of file format extensions matched for given file name, false otherwise
     */
    public static boolean isFileNameFormatMatched(final String fileName,
                                                  final String[] formatExtensions) {
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
}
