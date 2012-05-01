/*
 * Created: 19.04.2012
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

import su.comp.bk.arch.cpu.Cpu;

/**
 * Software trap operation.
 */
public abstract class SoftwareTrapOpcode extends BaseOpcode {

    // Software trap instructions execution time (in CPU ticks)
    private static final int EXECUTION_TIME = 68;

    public SoftwareTrapOpcode(Cpu cpu) {
        super(cpu);
    }

    @Override
    public int getExecutionTime() {
        return EXECUTION_TIME;
    }

    @Override
    public void execute() {
        getCpu().processTrap(getTrapVectorAddress(), true);
    }

    /**
     * Get software trap vector address.
     */
    protected abstract int getTrapVectorAddress();

}
