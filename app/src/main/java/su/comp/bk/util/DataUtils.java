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
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class DataUtils {
    /** Internal I/O buffer default size */
    private static final int BUFFER_SIZE = 8 * 1024;

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
     * Write data to file.
     *
     * @param file File to write
     * @param data data to write
     * @throws IOException in case of I/O error
     */
    public static void writeDataFile(File file, byte[] data) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            bos.write(data);
            bos.flush();
        }
    }

    /**
     * Read data from file.
     *
     * @param file File to read
     * @return read data
     * @throws IOException in case of I/O error
     */
    public static byte[] readDataFile(File file) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (FileInputStream fis = new FileInputStream(file)) {
            writeFully(fis, baos);
        }
        return baos.toByteArray();
    }

    /**
     * Compress data.
     *
     * @param data data to compress
     * @return compressed data in ZLIB format
     * @throws IOException in case of compressing error
     */
    public static byte[] compressData(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try (DeflaterOutputStream dos = new DeflaterOutputStream(baos, deflater)) {
            dos.write(data);
        }
        return baos.toByteArray();
    }

    /**
     * Decompress data.
     *
     * @param data data to decompress in ZLIB format
     * @return decompressed data
     * @throws IOException in case of decompressing error
     */
    public static byte[] decompressData(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length);
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        try (InflaterInputStream iis = new InflaterInputStream(bais)) {
            writeFully(iis, baos);
        }
        return baos.toByteArray();
    }
}
