/*
 * Copyright (C) 2020 Victor Antonovich (v.antonovich@gmail.com)
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

package su.comp.bk.arch.io.audio;

import su.comp.bk.arch.Computer;
import su.comp.bk.arch.cpu.Cpu;
import timber.log.Timber;

/**
 * AY-3-8910 / YM2149 sound chip attached to the peripheral port.
 * Based on https://github.com/georgemoralis/arcadeflex036/blob/master/emulator/src/sound/ay8910.java
 */
public class Ay8910 extends AudioOutput<Ay8910.Ay8910Command> {
    private final static int[] ADDRESSES = {Cpu.REG_SEL2};

    public static final String OUTPUT_NAME = "ay8910";

    /** Default AY8910 clock frequency (12 MHz / 7) */
    public static final int DEFAULT_CLOCK_FREQUENCY = 12000000 / 7;

    // Register ID's
    private static final int AY_AFINE = 0;
    private static final int AY_ACOARSE = 1;
    private static final int AY_BFINE = 2;
    private static final int AY_BCOARSE = 3;
    private static final int AY_CFINE = 4;
    private static final int AY_CCOARSE = 5;
    private static final int AY_NOISEPER = 6;
    private static final int AY_ENABLE = 7;
    private static final int AY_AVOL = 8;
    private static final int AY_BVOL = 9;
    private static final int AY_CVOL = 10;
    private static final int AY_EFINE = 11;
    private static final int AY_ECOARSE = 12;
    private static final int AY_ESHAPE = 13;
    private static final int AY_PORTA = 14;
    private static final int AY_PORTB = 15;

    private static final int STEP = 0x8000;

    private /*unsigned*/ char[] regs = new char[16];
    private int periodA, periodB, periodC, periodN, periodE;
    private int countA, countB, countC, countN, countE;
    private /*unsigned*/ int volA, volB, volC, volE;
    private /*unsigned*/ char envelopeA, envelopeB, envelopeC;
    private /*unsigned*/ char outputA, outputB, outputC, outputN;
    private /*signed char*/ int countEnv;
    private /*unsigned*/ char hold, alternate, attack, holding;
    private int rng;
    private /*unsigned*/ int updateStep;
    private /*unsigned*/ int[] volTable = new int[32];
    private int register;

    static class Ay8910Command extends AudioOutputUpdate {
        // Command register
        private int register;
        // Command value
        private int value;
    }

    public Ay8910(Computer computer, int clockFrequency) {
        super(computer);
        buildMixerTable();
        setClockFrequency(clockFrequency);
    }

    public Ay8910(Computer computer) {
        this(computer, DEFAULT_CLOCK_FREQUENCY);
    }

    private void buildMixerTable() {
        int i;
        double out;
        /* calculate the volume.voltage conversion table */
        /* The AY-3-8910 has 16 levels, in a logarithmic scale (3dB per step) */
        /* The YM2149 still has 16 levels for the tone generators, but 32 for */
        /* the envelope generator (1.5dB per step). */
        out = MAX_OUTPUT;
        for (i = 31; i > 0; i--) {
            this.volTable[i] = (int) (out + 0.5);	/* round to nearest */
            out /= 1.188502227;	/* = 10 ^ (1.5/20) = 1.5dB */
        }
        this.volTable[0] = 0;
    }

    public void setClockFrequency(int clockFrequency) {
        /* the step clock for the tone and noise generators is the chip clock    */
        /* divided by 8; for the envelope generator of the AY-3-8910, it is half */
        /* that much (clock/16), but the envelope of the YM2149 goes twice as    */
        /* fast, therefore again clock/8.                                        */
        /* Here we calculate the number of steps which happen during one sample  */
        /* at the given sample rate. No. of events = sample rate / (clock/8).    */
        /* STEP is a multiplier used to turn the fraction into a fixed point     */
        /* number.                                                               */
        this.updateStep = (int) (((double) STEP * getSampleRate() * 8) / clockFrequency);
    }

    @Override
    public String getName() {
        return OUTPUT_NAME;
    }

    @Override
    protected Ay8910Command[] createAudioOutputUpdates(int numAy8910Commands) {
        Ay8910Command[] ay8910Commands = new Ay8910Command[numAy8910Commands];
        for (int i = 0; i < numAy8910Commands; i++) {
            ay8910Commands[i] = new Ay8910Command();
        }
        return ay8910Commands;
    }

    @Override
    public int[] getAddresses() {
        return ADDRESSES;
    }

    @Override
    public synchronized void init(long cpuTime) {
        super.init(cpuTime);
        this.register = 0;
        this.rng = 1;
        this.outputA = 0;
        this.outputB = 0;
        this.outputC = 0;
        this.outputN = 0xff;
        for (int r = 0; r < AY_PORTA; r++) {
            writeRegister(r, 0);
        }
    }

    private synchronized void putCommand(int cmdRegister, int cmdValue, long cmdTimestamp) {
        Ay8910Command ay8910Command = putAudioOutputUpdate();
        if (ay8910Command != null) {
            ay8910Command.register = cmdRegister;
            ay8910Command.value = cmdValue;
            ay8910Command.timestamp = cmdTimestamp;
        }
    }

    @Override
    public boolean write(long cpuTime, boolean isByteMode, int address, int value) {
        int v = (value ^ 0xff) & 0xff;
        if (isByteMode) {
            putCommand(register, v, cpuTime);
        } else {
            // Set register number
            register = v & 0x0f;
        }
        return true;
    }

    private synchronized void writeRegister(int r, int v) {
        int old;

        this.regs[r] = (char) v;

        /* A note about the period of tones, noise and envelope: for speed reasons,*/
        /* we count down from the period to 0, but careful studies of the chip     */
        /* output prove that it instead counts up from 0 until the counter becomes */
        /* greater or equal to the period. This is an important difference when the*/
        /* program is rapidly changing the period to modulate the sound.           */
        /* To compensate for the difference, when the period is changed we adjust  */
        /* our internal counter.                                                   */
        /* Also, note that period = 0 is the same as period = 1. This is mentioned */
        /* in the YM2203 data sheets. However, this does NOT apply to the Envelope */
        /* period. In that case, period = 0 is half as period = 1. */
        switch (r) {
            case AY_AFINE:
            case AY_ACOARSE:
                this.regs[AY_ACOARSE] &= 0x0f;
                old = this.periodA;
                this.periodA = ((this.regs[AY_AFINE] + 256 * this.regs[AY_ACOARSE]) * this.updateStep);
                if (this.periodA == 0) {
                    this.periodA = this.updateStep;
                }
                this.countA += this.periodA - old;
                if (this.countA <= 0) {
                    this.countA = 1;
                }
                break;
            case AY_BFINE:
            case AY_BCOARSE:
                this.regs[AY_BCOARSE] &= 0x0f;
                old = this.periodB;
                this.periodB = ((this.regs[AY_BFINE] + 256 * this.regs[AY_BCOARSE]) * this.updateStep);
                if (this.periodB == 0) {
                    this.periodB = this.updateStep;
                }
                this.countB += this.periodB - old;
                if (this.countB <= 0) {
                    this.countB = 1;
                }
                break;
            case AY_CFINE:
            case AY_CCOARSE:
                this.regs[AY_CCOARSE] &= 0x0f;
                old = this.periodC;
                this.periodC = ((this.regs[AY_CFINE] + 256 * this.regs[AY_CCOARSE]) * this.updateStep);
                if (this.periodC == 0) {
                    this.periodC = this.updateStep;
                }
                this.countC += this.periodC - old;
                if (this.countC <= 0) {
                    this.countC = 1;
                }
                break;
            case AY_NOISEPER:
                this.regs[AY_NOISEPER] &= 0x1f;
                old = this.periodN;
                this.periodN = (this.regs[AY_NOISEPER] * this.updateStep);
                if (this.periodN == 0) {
                    this.periodN = this.updateStep;
                }
                this.countN += this.periodN - old;
                if (this.countN <= 0) {
                    this.countN = 1;
                }
                break;
            case AY_AVOL:
                this.regs[AY_AVOL] &= 0x1f;
                this.envelopeA = (char) (this.regs[AY_AVOL] & 0x10);
                this.volA = this.envelopeA != 0 ? this.volE : this.volTable[this.regs[AY_AVOL] != 0
                        ? this.regs[AY_AVOL] * 2 + 1 : 0];
                break;
            case AY_BVOL:
                this.regs[AY_BVOL] &= 0x1f;
                this.envelopeB = (char) (this.regs[AY_BVOL] & 0x10);
                this.volB = this.envelopeB != 0 ? this.volE : this.volTable[this.regs[AY_BVOL] != 0
                        ? this.regs[AY_BVOL] * 2 + 1 : 0];
                break;
            case AY_CVOL:
                this.regs[AY_CVOL] &= 0x1f;
                this.envelopeC = (char) (this.regs[AY_CVOL] & 0x10);
                this.volC = this.envelopeC != 0 ? this.volE : this.volTable[this.regs[AY_CVOL] != 0
                        ? this.regs[AY_CVOL] * 2 + 1 : 0];
                break;
            case AY_EFINE:
            case AY_ECOARSE:
                old = this.periodE;
                this.periodE = (((this.regs[AY_EFINE] + 256 * this.regs[AY_ECOARSE])) * this.updateStep);
                if (this.periodE == 0) {
                    this.periodE = this.updateStep / 2;
                }
                this.countE += this.periodE - old;
                if (this.countE <= 0) {
                    this.countE = 1;
                }
                break;
            case AY_ESHAPE:
                /* envelope shapes:
                 C AtAlH
                 0 0 x x  \___

                 0 1 x x  /___

                 1 0 0 0  \\\\

                 1 0 0 1  \___

                 1 0 1 0  \/\/
                 ___
                 1 0 1 1  \

                 1 1 0 0  ////
                 ___
                 1 1 0 1  /

                 1 1 1 0  /\/\

                 1 1 1 1  /___

                 The envelope counter on the AY-3-8910 has 16 steps. On the YM2149 it
                 has twice the steps, happening twice as fast. Since the end result is
                 just a smoother curve, we always use the YM2149 behaviour.
                 */
                this.regs[AY_ESHAPE] &= 0x0f;
                this.attack = (this.regs[AY_ESHAPE] & 0x04) != 0 ? (char) 0x1f : (char) 0x00;
                if ((this.regs[AY_ESHAPE] & 0x08) == 0) {
                    /* if Continue = 0, map the shape to the equivalent one which has Continue = 1 */
                    this.hold = 1;
                    this.alternate = this.attack;
                } else {
                    this.hold = (char) (this.regs[AY_ESHAPE] & 0x01);
                    this.alternate = (char) (this.regs[AY_ESHAPE] & 0x02);
                }
                this.countE = this.periodE;
                this.countEnv = 0x1f;
                this.holding = 0;
                this.volE = this.volTable[this.countEnv ^ this.attack];
                if (this.envelopeA != 0) {
                    this.volA = this.volE;
                }
                if (this.envelopeB != 0) {
                    this.volB = this.volE;
                }
                if (this.envelopeC != 0) {
                    this.volC = this.volE;
                }
                break;
            case AY_PORTA:
                if ((this.regs[AY_ENABLE] & 0x40) == 0) {
                    Timber.w("write to 8910 Port A set as input");
                }
                Timber.w("write %02x to Port A", v);
                break;
            case AY_PORTB:
                if ((this.regs[AY_ENABLE] & 0x80) == 0) {
                    Timber.w("write to 8910 Port B set as input");
                }
                Timber.w("write %02x to Port B", v);
                break;
        }
    }

    @Override
    protected void handleAudioOutputUpdate(Ay8910Command ay8910Command) {
        writeRegister(ay8910Command.register, ay8910Command.value);
    }

    @Override
    protected synchronized int writeSamples(short[] samplesBuffer, int sampleIndex, int length) {
        /* The 8910 has three outputs, each output is the mix of one of the three */
        /* tone generators and of the (single) noise generator. The two are mixed */
        /* BEFORE going into the DAC. The formula to mix each channel is: */
        /* (ToneOn | ToneDisable) & (NoiseOn | NoiseDisable). */
        /* Note that this means that if both tone and noise are disabled, the output */
        /* is 1, not 0, and can be modulated changing the volume. */
        /* If the channels are disabled, set their output to 1, and increase the */
        /* counter, if necessary, so they will not be inverted during this update. */
        /* Setting the output to 1 is necessary because a disabled channel is locked */
        /* into the ON state (see above); and it has no effect if the volume is 0. */
        /* If the volume is 0, increase the counter, but don't touch the output. */
        if ((this.regs[AY_ENABLE] & 0x01) != 0) {
            if (this.countA <= length * STEP) {
                this.countA += length * STEP;
            }
            this.outputA = 1;
        } else if (this.regs[AY_AVOL] == 0) {
            /* note that I do count += length, NOT count = length + 1. You might think */
            /* it's the same since the volume is 0, but doing the latter could cause */
            /* interferencies when the program is rapidly modulating the volume. */
            if (this.countA <= length * STEP) {
                this.countA += length * STEP;
            }
        }
        if ((this.regs[AY_ENABLE] & 0x02) != 0) {
            if (this.countB <= length * STEP) {
                this.countB += length * STEP;
            }
            this.outputB = 1;
        } else if (this.regs[AY_BVOL] == 0) {
            if (this.countB <= length * STEP) {
                this.countB += length * STEP;
            }
        }
        if ((this.regs[AY_ENABLE] & 0x04) != 0) {
            if (this.countC <= length * STEP) {
                this.countC += length * STEP;
            }
            this.outputC = 1;
        } else if (this.regs[AY_CVOL] == 0) {
            if (this.countC <= length * STEP) {
                this.countC += length * STEP;
            }
        }

        /* for the noise channel we must not touch OutputN - it's also not necessary */
        /* since we use outn. */
        if ((this.regs[AY_ENABLE] & 0x38) == 0x38) /* all off */ {
            if (this.countN <= length * STEP) {
                this.countN += length * STEP;
            }
        }

        int outn = (this.outputN | this.regs[AY_ENABLE]);
        /* buffering loop */
        while (length != 0) {
            int vola, volb, volc;
            int left;


            /* vola, volb and volc keep track of how long each square wave stays */
            /* in the 1 position during the sample period. */
            vola = volb = volc = 0;

            left = STEP;
            do {
                int nextevent;

                if (this.countN < left) {
                    nextevent = this.countN;
                } else {
                    nextevent = left;
                }
                if ((outn & 0x08) != 0) {
                    if (this.outputA != 0) {
                        vola += this.countA;
                    }
                    this.countA -= nextevent;
                    /* PeriodA is the half period of the square wave. Here, in each */
                    /* loop I add PeriodA twice, so that at the end of the loop the */
                    /* square wave is in the same status (0 or 1) it was at the start. */
                    /* vola is also incremented by PeriodA, since the wave has been 1 */
                    /* exactly half of the time, regardless of the initial position. */
                    /* If we exit the loop in the middle, OutputA has to be inverted */
                    /* and vola incremented only if the exit status of the square */
                    /* wave is 1. */
                    while (this.countA <= 0) {
                        this.countA += this.periodA;
                        if (this.countA > 0) {
                            this.outputA ^= 1;
                            if (this.outputA != 0) {
                                vola += this.periodA;
                            }
                            break;
                        }
                        this.countA += this.periodA;
                        vola += this.periodA;
                    }
                    if (this.outputA != 0) {
                        vola -= this.countA;
                    }
                } else {
                    this.countA -= nextevent;
                    while (this.countA <= 0) {
                        this.countA += this.periodA;
                        if (this.countA > 0) {
                            this.outputA ^= 1;
                            break;
                        }
                        this.countA += this.periodA;
                    }
                }
                if ((outn & 0x10) != 0) {
                    if (this.outputB != 0) {
                        volb += this.countB;
                    }
                    this.countB -= nextevent;
                    while (this.countB <= 0) {
                        this.countB += this.periodB;
                        if (this.countB > 0) {
                            this.outputB ^= 1;
                            if (this.outputB != 0) {
                                volb += this.periodB;
                            }
                            break;
                        }
                        this.countB += this.periodB;
                        volb += this.periodB;
                    }
                    if (this.outputB != 0) {
                        volb -= this.countB;
                    }
                } else {
                    this.countB -= nextevent;
                    while (this.countB <= 0) {
                        this.countB += this.periodB;
                        if (this.countB > 0) {
                            this.outputB ^= 1;
                            break;
                        }
                        this.countB += this.periodB;
                    }
                }
                if ((outn & 0x20) != 0) {
                    if (this.outputC != 0) {
                        volc += this.countC;
                    }
                    this.countC -= nextevent;
                    while (this.countC <= 0) {
                        this.countC += this.periodC;
                        if (this.countC > 0) {
                            this.outputC ^= 1;
                            if (this.outputC != 0) {
                                volc += this.periodC;
                            }
                            break;
                        }
                        this.countC += this.periodC;
                        volc += this.periodC;
                    }
                    if (this.outputC != 0) {
                        volc -= this.countC;
                    }
                } else {
                    this.countC -= nextevent;
                    while (this.countC <= 0) {
                        this.countC += this.periodC;
                        if (this.countC > 0) {
                            this.outputC ^= 1;
                            break;
                        }
                        this.countC += this.periodC;
                    }
                }
                this.countN -= nextevent;
                if (this.countN <= 0) {
                    /* Is noise output going to change? */
                    if (((this.rng + 1) & 2) != 0) /* (bit0^bit1)? */ {
                        this.outputN = (char) (~this.outputN);
                        outn = (this.outputN | this.regs[AY_ENABLE]);
                    }

                    /* The Random Number Generator of the 8910 is a 17-bit shift */
                    /* register. The input to the shift register is bit0 XOR bit2 */
                    /* (bit0 is the output). */

                    /* The following is a fast way to compute bit 17 = bit0^bit2. */
                    /* Instead of doing all the logic operations, we only check */
                    /* bit 0, relying on the fact that after two shifts of the */
                    /* register, what now is bit 2 will become bit 0, and will */
                    /* invert, if necessary, bit 16, which previously was bit 18. */
                    if ((this.rng & 1) != 0) {
                        this.rng ^= 0x28000;
                    }
                    this.rng >>= 1;
                    this.countN += this.periodN;
                }

                left -= nextevent;
            } while (left > 0);
            /* update envelope */
            if (this.holding == 0) {
                this.countE -= STEP;
                if (this.countE <= 0) {
                    do {
                        this.countEnv--;
                        this.countE += this.periodE;
                    } while (this.countE <= 0);

                    /* check envelope current position */
                    if (this.countEnv < 0) {
                        if (this.hold != 0) {
                            if (this.alternate != 0) {
                                this.attack ^= 0x1f;
                            }
                            this.holding = 1;
                            this.countEnv = 0;
                        } else {
                            /* if CountEnv has looped an odd number of times (usually 1), */
                            /* invert the output. */
                            if (this.alternate != 0 && (this.countEnv & 0x20) != 0) {
                                this.attack ^= 0x1f;
                            }

                            this.countEnv &= 0x1f;
                        }
                    }
                    this.volE = this.volTable[this.countEnv ^ this.attack];
                    /* reload volume */
                    if (this.envelopeA != 0) {
                        this.volA = this.volE;
                    }
                    if (this.envelopeB != 0) {
                        this.volB = this.volE;
                    }
                    if (this.envelopeC != 0) {
                        this.volC = this.volE;
                    }
                }
            }

            short buf1 = (short) ((vola * this.volA) / STEP);
            short buf2 = (short) ((volb * this.volB) / STEP);
            short buf3 = (short) ((volc * this.volC) / STEP);

            samplesBuffer[sampleIndex++] = (short) ((buf1 + buf2 + buf3) / 3);

            length--;
        }

        return sampleIndex;
    }
}
