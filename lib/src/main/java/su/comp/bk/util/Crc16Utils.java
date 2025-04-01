/*
 * Created: 22.10.2012
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
package su.comp.bk.util;

/**
 * CRC-16/CCITT-FALSE (http://reveng.sourceforge.net/crc-catalogue/16.htm#crc.cat.crc-16-ccitt-false) calculation.
 */
public class Crc16Utils {
    /** CRC initialization value */
    public static final short INIT_VALUE = (short) 0xffff;

    /**
     * Calculate next CRC value.
     * Based on algorithm from http://www.ccsinfo.com/forum/viewtopic.php?t=24977
     * @param crcValue current CRC value
     * @param data data value to add to CRC
     * @return next CRC value
     */
    public static short calculate(short crcValue, byte data) {
      short x = (short) (((crcValue >>> 8) ^ data) & 0xff);
      x ^= (x >>> 4);
      return (short) ((crcValue << 8) ^ (x << 12) ^ (x << 5) ^ x);
    }

    /**
     * Calculate CRC value of part of data from byte array.
     * @param data byte array
     * @param offset data offset to calculate CRC value
     * @param length data length to calculate CRC value
     * @return calculated CRC value
     */
    public static short calculate(byte[] data, int offset, int length) {
        short crcValue = INIT_VALUE;
        int counter = length;
        int index = offset;
        while (counter-- > 0) {
            crcValue = calculate(crcValue, data[index++]);
            index %= data.length;
        }
        return crcValue;
    }

    /**
     * Calculate CRC value for byte array.
     * @param data byte array to calculate CRC value
     * @return calculated CRC value
     */
    public static short calculate(byte[] data) {
        return calculate(data, 0, data.length);
    }

    /**
     * Calculate next CRC value for word data.
     * @param crcValue current CRC value
     * @param data word data value to add to CRC
     * @return next CRC value
     */
    public static short calculate(short crcValue, short data) {
        short x = calculate(crcValue, (byte) (data >> 8));
        return calculate(x, (byte) data);
    }

    /**
     * Calculate CRC value of part of data from word array.
     * @param data word array
     * @param offset data offset to calculate CRC value
     * @param length data length to calculate CRC value
     * @return calculated CRC value
     */
    public static short calculate(short[] data, int offset, int length) {
        short crcValue = INIT_VALUE;
        int counter = length;
        int index = offset;
        while (counter-- > 0) {
            crcValue = calculate(crcValue, data[index++]);
            index %= data.length;
        }
        return crcValue;
    }
}
