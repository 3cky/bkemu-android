/*
 * Copyright (C) 2022 Victor Antonovich (v.antonovich@gmail.com)
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

import java.io.UnsupportedEncodingException;

public class StringUtils {
    /**
     * Get tape file name as string from its BK-Monitor 16 bytes array representation.
     *
     * @param fileNameData internal file name array data
     * @return string file name representation
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
     * Checks is file name matches some of given extensions (ignoring case).
     *
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

    /**
     * Gets a substring from the specified String avoiding exceptions.
     *
     * @param str   the String to get the substring from, may be null
     * @param start the position to start from, negative means
     *              count back from the end of the String by this many characters
     * @param end   the position to end at (exclusive), negative means
     *              count back from the end of the String by this many characters
     * @return substring from start position to end positon,
     * <code>null</code> if null String input
     */
    public static String substring(String str, int start, int end) {
        return org.apache.commons.lang.StringUtils.substring(str, start, end);
    }
}
