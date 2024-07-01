/*
 * Copyright (C) 2024 Victor Antonovich (v.antonovich@gmail.com)
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

package su.comp.bk.resource;

import android.content.res.Resources;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import su.comp.bk.R;

/**
 * {@link ResourceManager} implementation for accessing the data stored in application resources.
 */
public class AppResourceManager implements ResourceManager {
    private final Map<String, Integer> romRawResourceIds = new HashMap<>();

    private final Resources resources;

    public AppResourceManager(Resources resources) {
        this.resources = resources;
        addReadOnlyMemoryRawResourceIds();
    }

    private void addReadOnlyMemoryRawResourceIds() {
        addReadOnlyMemoryRawResourceId(ROM_MONITOR_10, R.raw.monit10);
        addReadOnlyMemoryRawResourceId(ROM_BASIC_10_1, R.raw.basic10_1);
        addReadOnlyMemoryRawResourceId(ROM_BASIC_10_2, R.raw.basic10_2);
        addReadOnlyMemoryRawResourceId(ROM_BASIC_10_3, R.raw.basic10_3);
        addReadOnlyMemoryRawResourceId(ROM_FOCAL_10, R.raw.focal);
        addReadOnlyMemoryRawResourceId(ROM_MSTD_10, R.raw.tests);
        addReadOnlyMemoryRawResourceId(ROM_FLOPPY_BIOS, R.raw.disk_327);
        addReadOnlyMemoryRawResourceId(ROM_MSTD_11M, R.raw.mstd11m);
        addReadOnlyMemoryRawResourceId(ROM_SMK_BIOS, R.raw.disk_smk512_v205);
        addReadOnlyMemoryRawResourceId(ROM_BASIC_11M_0, R.raw.basic11m_0);
        addReadOnlyMemoryRawResourceId(ROM_BASIC_11M_1, R.raw.basic11m_1);
        addReadOnlyMemoryRawResourceId(ROM_EXT_BOS_11M, R.raw.ext11m);
        addReadOnlyMemoryRawResourceId(ROM_BOS_11M, R.raw.bos11m);
    }

    private void addReadOnlyMemoryRawResourceId(String romId, int romRawResourceId) {
        romRawResourceIds.put(romId, romRawResourceId);
    }

    private Integer getReadOnlyMemoryRawResourceId(String romId) {
        return romRawResourceIds.get(romId);
    }

    @Override
    public byte[] getReadOnlyMemoryData(String romId) throws IOException {
        Integer romRawResourceId = getReadOnlyMemoryRawResourceId(romId);
        if (romRawResourceId == null) {
            throw new IOException("Unknown ROM ID: " + romId);
        }
        return loadRawResourceData(romRawResourceId);
    }

    /**
     * Load data of raw resource.
     * @param resourceId raw resource ID
     * @return read raw resource data
     * @throws IOException in case of loading error
     */
    private byte[] loadRawResourceData(int resourceId) throws IOException {
        byte[] resourceData;
        try (InputStream resourceDataStream = resources.openRawResource(resourceId)) {
            resourceData = new byte[resourceDataStream.available()];
            resourceDataStream.read(resourceData);
        }
        return resourceData;
    }
}
