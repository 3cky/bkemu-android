/*
 * Created: 01.01.2013
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
package su.comp.bk.arch;

import static org.junit.Assert.*;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import android.os.Bundle;

import su.comp.bk.arch.cpu.Cpu;
import su.comp.bk.arch.io.Device;
import su.comp.bk.arch.memory.RandomAccessMemory;
import su.comp.bk.arch.memory.ReadOnlyMemory;

/**
 * {@link Computer} class factory unit tests.
 */
public class ComputerFactoryTest {

    private final static String TEST_BASEDIR = "target/test-classes/";

    private static final long MAX_EXECUTION_TIME = 10000L;

    private Computer computer;

    private RandomAccessMemory workMemory;

    private Terminal terminal;

    static class Terminal implements Device {

        private static final int READ_CONTROL_REGISTER_ADDRESS = 0177560;
        private static final int READ_DATA_REGISTER_ADDRESS = 0177562;
        private static final int WRITE_CONTROL_REGISTER_ADDRESS = 0177564;
        private static final int WRITE_DATA_REGISTER_ADDRESS = 0177566;

        private static final int CONTROL_DATA_READY = 0200;

        private static final int MAX_DATA_LENGTH = 16384;

        private StringBuffer writtenData = new StringBuffer();

        private final  int[] ADDRESSES = {
            READ_CONTROL_REGISTER_ADDRESS, READ_DATA_REGISTER_ADDRESS,
            WRITE_CONTROL_REGISTER_ADDRESS, WRITE_DATA_REGISTER_ADDRESS
        };

        public String getWrittenData() {
            return StringUtils.abbreviate(writtenData.toString(), MAX_DATA_LENGTH);
        }

        @Override
        public int[] getAddresses() {
            return ADDRESSES;
        }

        @Override
        public void init(long cpuTime) {
            // Do nothing
        }

        @Override
        public void saveState(Bundle outState) {
            // Do nothing
        }

        @Override
        public void restoreState(Bundle inState) {
            // Do nothing
        }

        @Override
        public int read(long cpuTime, int address) {
            switch (address) {
                case WRITE_CONTROL_REGISTER_ADDRESS:
                    return CONTROL_DATA_READY;
                default:
                    return 0;
            }
        }

        @Override
        public boolean write(long cpuTime, boolean isByteMode, int address, int value) {
            switch (address) {
                case WRITE_DATA_REGISTER_ADDRESS:
                    writtenData.append((char) value);
                    break;
                default:
                    break;
            }
            return true;
        }
    }

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        computer = new Computer();
        computer.setClockFrequency(Computer.CLOCK_FREQUENCY_BK0010);
        workMemory = new RandomAccessMemory("TestWorkMemory", 0, 020000);
        computer.addMemory(workMemory);
        RandomAccessMemory videoMemory = new RandomAccessMemory("TestVideoMemory", 040000, 020000);
        computer.addMemory(videoMemory);
        computer.addMemory(new ReadOnlyMemory("TestReadOnlyMemory", 0100000, new byte[010000]));
        terminal = new Terminal();
        computer.addDevice(terminal);
        computer.getCpu().setPswState(0);
        computer.getCpu().writeRegister(false, Cpu.SP, 020000);
        computer.getCpu().writeRegister(false, Cpu.PC, 4);
    }

    private void setupTestData(String testName) throws Exception {
        byte[] testData = FileUtils.readFileToByteArray(new File(TEST_BASEDIR + testName));
        for (int idx = 0; idx < testData.length; idx++) {
            workMemory.write(true, workMemory.getStartAddress() + idx, testData[idx]);
        }
    }

    protected boolean execute(int address, String expectedOutput) {
        boolean isSuccess = false;
        Cpu cpu = computer.getCpu();
        cpu.writeRegister(false, Cpu.PC, address);
        long startTime = System.currentTimeMillis();
        while ((System.currentTimeMillis() - startTime) < MAX_EXECUTION_TIME
                    || terminal.getWrittenData().length() > Terminal.MAX_DATA_LENGTH) {
            try {
                cpu.executeNextOperation();
                if (cpu.isHaltMode()) {
                    throw new IllegalStateException("HALT mode");
                }
            } catch (Exception e) {
                e.printStackTrace();
                fail("can't execute operation, PC: 0" + Integer.toOctalString(cpu
                        .readRegister(false, Cpu.PC)));
            }
            if (expectedOutput.equals(terminal.getWrittenData())) {
                isSuccess = true;
                break;
            }
        }
        return isSuccess;
    }

    @Test
    public void test791401() throws Exception {
        setupTestData("791401");
        assertTrue(execute(0200, "\r\n\016k prohod"));
    }

    @Test
    @Ignore
    public void test791404() throws Exception {
        setupTestData("791404");
        assertTrue(execute(0200, "\r\np"));
    }

    @Test
    public void test791323() throws Exception {
        setupTestData("791323");
        assertTrue(execute(0200, "\r\npAMqTx\r\n000000-077776\r\nTCT13 bAHK   00\r\n" +
                		"TCT13 bAHK   01\r\nTCT13 bAHK   02\r\nTCT13 bAHK   03\r\n" +
                		"pEPEM\r\nTCT13 bAHK   00\r\nK pPOXOd #   01"));
    }

}
