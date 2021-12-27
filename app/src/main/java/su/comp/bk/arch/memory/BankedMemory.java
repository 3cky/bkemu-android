/*
 * Created: 16.01.2013
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
package su.comp.bk.arch.memory;

import android.os.Bundle;
import su.comp.bk.arch.Computer;

/**
 * Banked memory class.
 */
public class BankedMemory implements Memory {

    private final String id;
    private final int size;

    private final Memory[] banks;

    private int activeBankIndex = -1;
    private Memory activeBank;

    /**
     * Create new banked memory with given start address, bank size and number of banks.
     * @param id banked memory ID
     * @param startAddress Memory bank starting address
     * @param size Memory bank size (in words)
     */
    public BankedMemory(String id, int startAddress, int size, int numBanks) {
        this.id = id;
        this.size = size;
        this.banks = new Memory[numBanks];
    }

    /**
     * Set memory bank for given bank index.
     * @param bankIndex Memory bank index to set
     * @param memoryBank Memory bank to set
     */
    public void setBank(int bankIndex, Memory memoryBank) {
        banks[bankIndex] = memoryBank;
    }

    /**
     * Get number of banks.
     * @return Number of banks
     */
    public int getNumBanks() {
        return banks.length;
    }

    /**
     * Get memory bank for given bank index.
     * @param bankIndex Memory bank index to get
     * @return Memory bank for given bank index or <code>null</code>
     * if index is negative or no bank set for given index
     */
    public Memory getBank(int bankIndex) {
        return (bankIndex < 0) ? null : banks[bankIndex];
    }

    /**
     * Get all memory banks.
     * @return Array of memory banks (some of banks can be <code>null</code>)
     */
    public Memory[] getBanks() {
        return banks;
    }

    /**
     * Get active memory bank index.
     * @return active memory bank index (or -1 of no active memory bank selected)
     */
    public synchronized int getActiveBankIndex() {
        return activeBankIndex;
    }

    /**
     * Set active memory bank index.
     * @param bankIndex active memory bank index to set (or -1 to unset active memory bank)
     */
    public synchronized void setActiveBankIndex(int bankIndex) {
        this.activeBankIndex = bankIndex;
        this.activeBank = getBank(bankIndex);
    }

    /**
     * Get active memory bank.
     * @return active memory bank or <code>null</code> if active memory bank is not set
     * or no memory bank set for active bank index
     */
    public synchronized Memory getActiveBank() {
        return activeBank;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public int getSize() {
        return size;
    }

    @Override
    public synchronized short[] getData() {
        return activeBank.getData();
    }

    @Override
    public int read(int offset) {
        return (activeBank != null) ? activeBank.read(offset) : Computer.BUS_ERROR;
    }

    @Override
    public boolean write(boolean isByteMode, int offset, int value) {
        return (activeBank != null) && activeBank.write(isByteMode, offset, value);
    }

    @Override
    public void saveState(Bundle outState) {
        outState.putInt(toString(), getActiveBankIndex());
    }

    @Override
    public void restoreState(Bundle inState) {
        setActiveBankIndex(inState.getInt(toString()));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o) || (o instanceof BankedMemory
                && ((BankedMemory) o).getId().equals(id));
    }

    @Override
    public String toString() {
        return getClass().getName() + "#" + id;
    }

}
