/*
 * Created: 20.08.2018
 *
 * Copyright (C) 2018 Victor Antonovich (v.antonovich@gmail.com)
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
package su.comp.bk;

import java.io.File;
import java.net.URL;

/**
 * Base class for tests using file resources (ROM/Disk images/etc).
 */
public class ResourceFileTestBase {
    /**
     * Get resource as file.
     * @param resourceName resource name to get as file
     * @return resource file
     */
    public File getTestResourceFile(String resourceName) {
        ClassLoader classLoader = this.getClass().getClassLoader();
        URL resource = classLoader.getResource(resourceName);
        return new File(resource.getPath());
    }
}
