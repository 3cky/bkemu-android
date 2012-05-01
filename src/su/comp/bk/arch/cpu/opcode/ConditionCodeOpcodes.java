/*
 * Created: 05.04.2012
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
 * Condition-code operations:
 *   CLC, CLV, CLZ, CLN, CCC (clear relevant condition code)
 *   SEC, SEV, SEZ, SEN, SCC (set relevant condition code)
 *   NOP (no operation)
 */
public class ConditionCodeOpcodes extends ZeroOperandOpcode {

    public final static int OPCODE_NOP = 0240;
    public final static int OPCODE_CLC = 0241;
    public final static int OPCODE_CLV = 0242;
    public final static int OPCODE_CLVC = 0243;
    public final static int OPCODE_CLZ = 0244;
    public final static int OPCODE_CLZC = 0245;
    public final static int OPCODE_CLZV = 0246;
    public final static int OPCODE_CLZVC = 0247;
    public final static int OPCODE_CLN = 0250;
    public final static int OPCODE_CLNC = 0251;
    public final static int OPCODE_CLNV = 0252;
    public final static int OPCODE_CLNVC = 0253;
    public final static int OPCODE_CLNZ = 0254;
    public final static int OPCODE_CLNZC = 0255;
    public final static int OPCODE_CLNZV = 0256;
    public final static int OPCODE_CCC = 0257;
    public final static int OPCODE_NOP260 = 0260;
    public final static int OPCODE_SEC = 0261;
    public final static int OPCODE_SEV = 0262;
    public final static int OPCODE_SEVC = 0263;
    public final static int OPCODE_SEZ = 0264;
    public final static int OPCODE_SEZC = 0265;
    public final static int OPCODE_SEZV = 0266;
    public final static int OPCODE_SEZVC = 0267;
    public final static int OPCODE_SEN = 0270;
    public final static int OPCODE_SENC = 0271;
    public final static int OPCODE_SENV = 0272;
    public final static int OPCODE_SENVC = 0273;
    public final static int OPCODE_SENZ = 0274;
    public final static int OPCODE_SENZC = 0275;
    public final static int OPCODE_SENZV = 0276;
    public final static int OPCODE_SCC = 0277;

    public ConditionCodeOpcodes(Cpu cpu) {
        super(cpu);
    }

    @Override
    public int getOpcode() {
        return getInstruction();
    }

    @Override
    public int getExecutionTime() {
        return getBaseExecutionTime();
    }

    @Override
    public void execute() {
        int instruction = getInstruction();
        int psw = getCpu().getPswState();
        int conditionMask = instruction & 017;
        psw = (instruction & 020) != 0 ? (psw | conditionMask) : (psw & ~conditionMask);
        getCpu().setPswState((short) psw);
    }

}
