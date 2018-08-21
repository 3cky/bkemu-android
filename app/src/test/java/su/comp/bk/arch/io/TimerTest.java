package su.comp.bk.arch.io;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import su.comp.bk.arch.Computer;

public class TimerTest {

    private Computer computer;

    @Before
    public void setUp() {
        computer = new Computer();
        computer.addDevice(new Sel1RegisterSystemBits(0100000));
        computer.addDevice(new Timer());
        computer.reset();
    }

    @Test
    public void testTimerInit() {
        // Check timer register states after reset
        assertEquals(Timer.CONTROL_REGISTER_INITIAL_STATE  | 0177400,
                computer.readMemory(false, Timer.CONTROL_REGISTER_ADDRESS));
        assertEquals(Timer.PRESET_REGISTER_INITIAL_STATE,
                computer.readMemory(false, Timer.PRESET_REGISTER_ADDRESS));
        assertEquals(Timer.PRESET_REGISTER_INITIAL_STATE,
                computer.readMemory(false, Timer.COUNTER_REGISTER_ADDRESS));
    }

    @Test
    public void testTimerNormalMode() {
        assertTrue(computer.writeMemory(false, Timer.PRESET_REGISTER_ADDRESS, 10));
        assertTrue(computer.writeMemory(false, Timer.CONTROL_REGISTER_ADDRESS,
                Timer.CONTROL_EXPIRY_MONITOR | Timer.CONTROL_TIMER_STARTED));
        assertEquals(10, computer.readMemory(false, Timer.COUNTER_REGISTER_ADDRESS));
        // Check timer state after 128 CPU cycles
        computer.getCpu().setTime(computer.getCpu().getTime() + Timer.PRESCALER);
        assertEquals(9, computer.readMemory(false, Timer.COUNTER_REGISTER_ADDRESS));
        assertEquals(0177400 | Timer.CONTROL_EXPIRY_MONITOR | Timer.CONTROL_TIMER_STARTED,
                computer.readMemory(false, Timer.CONTROL_REGISTER_ADDRESS));
        // Check timer expiry flag
        computer.getCpu().setTime(computer.getCpu().getTime() + Timer.PRESCALER * 9);
        assertEquals(10, computer.readMemory(false, Timer.COUNTER_REGISTER_ADDRESS));
        assertEquals(0177400 | Timer.CONTROL_EXPIRY_MONITOR | Timer.CONTROL_TIMER_STARTED
                | Timer.CONTROL_TIMER_EXPIRED,
                computer.readMemory(false, Timer.CONTROL_REGISTER_ADDRESS));
        // Check timer state after 128 CPU cycles
        computer.getCpu().setTime(computer.getCpu().getTime() + Timer.PRESCALER);
        assertEquals(9, computer.readMemory(false, Timer.COUNTER_REGISTER_ADDRESS));
        assertEquals(0177400 | Timer.CONTROL_EXPIRY_MONITOR | Timer.CONTROL_TIMER_STARTED
                | Timer.CONTROL_TIMER_EXPIRED,
                computer.readMemory(false, Timer.CONTROL_REGISTER_ADDRESS));
    }

    @Test
    public void testTimerWrapAroundMode() {
        assertTrue(computer.writeMemory(false, Timer.PRESET_REGISTER_ADDRESS, 10));
        assertTrue(computer.writeMemory(false, Timer.CONTROL_REGISTER_ADDRESS,
                 Timer.CONTROL_TIMER_STARTED | Timer.CONTROL_WRAPAROUND_MODE));
        assertEquals(10, computer.readMemory(false, Timer.COUNTER_REGISTER_ADDRESS));
        // Check timer state after 128 CPU cycles
        computer.getCpu().setTime(computer.getCpu().getTime() + Timer.PRESCALER);
        assertEquals(9, computer.readMemory(false, Timer.COUNTER_REGISTER_ADDRESS));
        assertEquals(0177400 | Timer.CONTROL_TIMER_STARTED | Timer.CONTROL_WRAPAROUND_MODE,
                computer.readMemory(false, Timer.CONTROL_REGISTER_ADDRESS));
        // Check timer expiry flag
        computer.getCpu().setTime(computer.getCpu().getTime() + Timer.PRESCALER * 9);
        assertEquals(0, computer.readMemory(false, Timer.COUNTER_REGISTER_ADDRESS));
        assertEquals(0177400 | Timer.CONTROL_TIMER_STARTED | Timer.CONTROL_WRAPAROUND_MODE,
                computer.readMemory(false, Timer.CONTROL_REGISTER_ADDRESS));
        // Check timer state after 128 CPU cycles
        computer.getCpu().setTime(computer.getCpu().getTime() + Timer.PRESCALER);
        assertEquals(0177777, computer.readMemory(false, Timer.COUNTER_REGISTER_ADDRESS));
        assertEquals(0177400 | Timer.CONTROL_TIMER_STARTED | Timer.CONTROL_WRAPAROUND_MODE,
                computer.readMemory(false, Timer.CONTROL_REGISTER_ADDRESS));
    }

    @Test
    public void testTimerOneShotMode() {
        assertTrue(computer.writeMemory(false, Timer.PRESET_REGISTER_ADDRESS, 1));
        assertTrue(computer.writeMemory(false, Timer.CONTROL_REGISTER_ADDRESS,
                Timer.CONTROL_ONESHOT_MODE | Timer.CONTROL_EXPIRY_MONITOR
                    | Timer.CONTROL_TIMER_STARTED));
        assertEquals(1, computer.readMemory(false, Timer.COUNTER_REGISTER_ADDRESS));
        // Check timer state after 128 CPU cycles
        computer.getCpu().setTime(computer.getCpu().getTime() + Timer.PRESCALER);
        assertEquals(1, computer.readMemory(false, Timer.COUNTER_REGISTER_ADDRESS));
        assertEquals(0177400 | Timer.CONTROL_ONESHOT_MODE | Timer.CONTROL_EXPIRY_MONITOR
                        | Timer.CONTROL_TIMER_EXPIRED,
                computer.readMemory(false, Timer.CONTROL_REGISTER_ADDRESS));
    }

}
