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

import java.io.IOException;

public interface ResourceManager {
    String ROM_MONITOR_10 = "Monitor10";
    String ROM_BASIC_10_1 = "Basic10:1";
    String ROM_BASIC_10_2 = "Basic10:2";
    String ROM_BASIC_10_3 = "Basic10:3";
    String ROM_FOCAL_10 = "Focal10";
    String ROM_MSTD_10 = "MSTD10";
    String ROM_MSTD_11M = "MSTD11M";
    String ROM_BASIC_11M_0 = "Basic11M:0";
    String ROM_BASIC_11M_1 = "Basic11M:1";
    String ROM_BOS_11M = "BOS11M";
    String ROM_EXT_BOS_11M = "ExtBOS11M";
    String ROM_FLOPPY_BIOS = "FloppyBios";
    String ROM_SMK_BIOS = "SmkBiosRom";

    /**
     * Get ROM data.
     * @return ROM data
     * @throws IOException in case of ROM data loading error
     */
    byte[] getReadOnlyMemoryData(String romId) throws IOException;
}
