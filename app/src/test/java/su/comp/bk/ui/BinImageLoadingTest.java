package su.comp.bk.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.net.Uri;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import su.comp.bk.arch.Computer;
import su.comp.bk.arch.memory.RandomAccessMemory;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 36})
public class BinImageLoadingTest {
    private static final int LIMIT = BkEmuActivity.MAX_BIN_IMAGE_FILE_SIZE;

    private BkEmuActivity activity;

    @Before
    public void setUp() {
        activity = Robolectric.buildActivity(BkEmuActivity.class).get();
        activity.computer = new Computer();
        activity.computer.addMemory(01000, new RandomAccessMemory("TestMemory",
                new byte[] {11, 22, 33, 44}, RandomAccessMemory.Type.K565RU6));
        activity.lastBinImageLength = 17;
        activity.lastBinImageFileUri = "content://test.provider/previous.bin";
    }

    @Test
    public void loadsValidImage() throws IOException {
        assertEquals(01000, activity.loadBinImage(new byte[] {0, 2, 2, 0, 42, (byte) 255}));
        assertEquals(2, activity.lastBinImageLength);
        assertEquals(42, activity.computer.readMemory(true, 01000));
        assertEquals(255, activity.computer.readMemory(true, 01001));
        assertEquals(33, activity.computer.readMemory(true, 01002));
    }

    @Test
    public void preservesExplicitLoadAddressAndAcceptsTrailingData() throws IOException {
        activity.lastBinImageAddress = 01002;
        assertEquals(01002, activity.loadBinImage(new byte[] {0, 2, 1, 0, 42, 99}));
        assertEquals(11, activity.computer.readMemory(true, 01000));
        assertEquals(42, activity.computer.readMemory(true, 01002));
    }

    @Test
    public void rejectsShortHeaderWithoutChangingStateOrMemory() {
        assertThrows(IllegalArgumentException.class,
                () -> activity.loadBinImage(new byte[] {0, 2, 2}));
        assertUnchanged();
    }

    @Test
    public void rejectsTruncatedPayloadWithoutChangingStateOrMemory() {
        assertThrows(IOException.class,
                () -> activity.loadBinImage(new byte[] {0, 2, 2, 0, 42}));
        assertUnchanged();
    }

    @Test
    public void rejectsTruncatedPayloadWithExplicitLoadAddress() {
        activity.lastBinImageAddress = 01002;
        assertThrows(IOException.class,
                () -> activity.loadBinImage(new byte[] {0, 2, 2, 0, 42}));
        assertEquals(01002, activity.lastBinImageAddress);
        assertEquals(17, activity.lastBinImageLength);
        assertEquals(33, activity.computer.readMemory(true, 01002));
    }

    @Test
    public void loadsUriAtSizeLimit() throws Exception {
        byte[] data = Arrays.copyOf(new byte[] {0, 2, 1, 0, 42}, LIMIT);
        Uri uri = register(data);
        assertEquals(01000, activity.loadBinImageFile(uri));
        assertEquals(uri.toString(), activity.lastBinImageFileUri);
        assertEquals(42, activity.computer.readMemory(true, 01000));
    }

    @Test
    public void rejectsOversizedUriWithoutChangingStateOrMemory() {
        Uri uri = register(new byte[LIMIT + 1]);
        assertThrows(IOException.class, () -> activity.loadBinImageFile(uri));
        assertUnchanged();
    }

    @Test
    public void rejectsTruncatedUriWithoutRememberingIt() {
        Uri uri = register(new byte[] {0, 2, 2, 0, 42});
        assertThrows(IOException.class, () -> activity.loadBinImageFile(uri));
        assertUnchanged();
    }

    private Uri register(byte[] data) {
        Uri uri = Uri.parse("content://test.provider/image.bin");
        Shadows.shadowOf(activity.getContentResolver())
                .registerInputStream(uri, new ByteArrayInputStream(data));
        return uri;
    }

    private void assertUnchanged() {
        assertEquals(0, activity.lastBinImageAddress);
        assertEquals(17, activity.lastBinImageLength);
        assertEquals("content://test.provider/previous.bin", activity.lastBinImageFileUri);
        for (int i = 0; i < 4; i++) {
            assertEquals((i + 1) * 11, activity.computer.readMemory(true, 01000 + i));
        }
    }
}
