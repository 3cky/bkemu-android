/*
 * Created: 01.04.2012
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
package su.comp.bk.arch.cpu.opcode;

/**
 * PDP-11 operation code interface.
 */
public interface Opcode {

    /** Opcode byte operation flag */
    static int BYTE_OPERATION_FLAG = 0100000;

    /**
     * Get opcode value.
     * @return opcode value
     */
    int getOpcode();

    /**
     * Decode instruction.
     * @param instruction instruction word to decode
     */
    void decode(int instruction);

    /**
     * Execute decoded instruction.
     */
    void execute();

}
