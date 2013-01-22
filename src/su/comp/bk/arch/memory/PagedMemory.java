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
 * Paged memory class.
 */
public class PagedMemory implements Memory {

    private final String id;
    private final int startAddress;
    private final int endAddress;
    private final int size;

    private final Memory[] pages;

    private int activePageIndex = -1;
    private Memory activePage;

    /**
     * Create new paged memory with given start address, page size and number of pages.
     * @param id paged memory ID
     * @param startAddress Memory page starting address
     * @param size Memory page size (in words)
     */
    public PagedMemory(String id, int startAddress, int size, int numPages) {
        this.id = id;
        this.startAddress = startAddress;
        this.endAddress = startAddress + (size << 1) - 1;
        this.size = size;
        this.pages = new Memory[numPages];
    }

    /**
     * Set memory page for given page index.
     * @param pageIndex Memory page index to set
     * @param memoryPage Memory page to set
     */
    public void setPage(int pageIndex, Memory memoryPage) {
        pages[pageIndex] = memoryPage;
    }

    /**
     * Get memory page for given page index.
     * @param pageIndex Memory page index to get
     * @return Memory page for given page index or <code>null</code>
     * if index is negative or no page set for given index
     */
    public Memory getPage(int pageIndex) {
        return (pageIndex < 0) ? null : pages[pageIndex];
    }

    /**
     * Get all memory pages.
     * @return Array of memory pages (some of pages can be <code>null</code>)
     */
    public Memory[] getPages() {
        return pages;
    }

    /**
     * Get active memory page index.
     * @return active memory page index (or -1 of no active memory page selected)
     */
    public synchronized int getActivePageIndex() {
        return activePageIndex;
    }

    /**
     * Set active memory page index.
     * @param pageIndex active memory page index to set (or -1 to unset active memory page)
     */
    public synchronized void setActivePageIndex(int pageIndex) {
        this.activePageIndex = pageIndex;
        this.activePage = getPage(pageIndex);
    }

    /**
     * Get active memory page.
     * @return active memory page or <code>null</code> if active memory page is not set
     * or no memory page set for active page index
     */
    public synchronized Memory getActivePage() {
        return activePage;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public int getStartAddress() {
        return startAddress;
    }

    @Override
    public int getSize() {
        return size;
    }

    @Override
    public synchronized short[] getData() {
        return activePage.getData();
    }

    @Override
    public boolean isRelatedAddress(int address) {
        return (address >= startAddress) && (address <= endAddress);
    }

    @Override
    public int read(boolean isByteMode, int address) {
        return (activePage != null) ? activePage.read(isByteMode,
                address - startAddress) : Computer.BUS_ERROR;
    }

    @Override
    public boolean write(boolean isByteMode, int address, int value) {
        return (activePage != null) ? activePage.write(isByteMode,
                address - startAddress, value) : false;
    }

    @Override
    public void saveState(Bundle outState) {
        outState.putInt(toString(), getActivePageIndex());
    }

    @Override
    public void restoreState(Bundle inState) {
        setActivePageIndex(inState.getInt(toString()));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o) || (o instanceof PagedMemory
                && ((PagedMemory) o).getId().equals(id));
    }

    @Override
    public String toString() {
        return getClass().getName() + "#" + id;
    }

}
