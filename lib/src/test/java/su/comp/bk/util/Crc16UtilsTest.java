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

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * {@link Crc16Utils} tests.
 */
public class Crc16UtilsTest {

    /**
     * Test method for {@link Crc16Utils#calculate(byte[])}.
     * @throws Exception in case of error
     */
    @Test
    public void testCalculateByteArray() throws Exception {
        // According http://reveng.sourceforge.net/crc-catalogue/legend.htm (see "Appendix")
        assertEquals(0x29b1, Crc16Utils.calculate("123456789".getBytes("UTF-8")));
    }

}
