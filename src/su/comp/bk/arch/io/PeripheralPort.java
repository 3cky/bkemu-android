/*
 * Created: 23.04.2012
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
package su.comp.bk.arch.io;

/**
 * BK-0010 peripheral port.
 */
public class PeripheralPort implements Device {

    public final static int DATA_REGISTER_ADDRESS = 0177714;

    private final static int[] ADDRESSES = { DATA_REGISTER_ADDRESS };

    public PeripheralPort() {
    }

    @Override
    public int[] getAddresses() {
        return ADDRESSES;
    }

    @Override
    public void init() {
        // TODO
    }

    @Override
    public int read(int address) {
        return 0; // TODO
    }

    @Override
    public void write(boolean isByteMode, int address, int value) {
        // TODO
    }

}
