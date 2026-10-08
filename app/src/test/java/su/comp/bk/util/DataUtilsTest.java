package su.comp.bk.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import su.comp.bk.ui.BkEmuActivity;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 36})
public class DataUtilsTest {
    private static final int LIMIT = BkEmuActivity.MAX_BIN_IMAGE_FILE_SIZE;
    private static final Uri URI = Uri.parse("content://test.provider/image.bin");

    @Test
    public void readsContentAtSizeLimit() throws IOException {
        byte[] data = new byte[LIMIT];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        TrackingInputStream input = new TrackingInputStream(data);
        Context context = register(input);
        assertArrayEquals(data, DataUtils.getUriContentData(context, URI, LIMIT));
        assertTrue(input.closed);
    }

    @Test
    public void readsEmptyContentWithZeroLimit() throws IOException {
        TrackingInputStream input = new TrackingInputStream(new byte[0]);
        Context context = register(input);
        assertArrayEquals(new byte[0], DataUtils.getUriContentData(context, URI, 0));
        assertTrue(input.closed);
    }

    @Test
    public void rejectsContentOneByteOverLimitAndClosesStream() {
        TrackingInputStream input = new TrackingInputStream(new byte[LIMIT + 1]);
        Context context = register(input);
        assertThrows(IOException.class, () -> DataUtils.getUriContentData(context, URI, LIMIT));
        assertTrue(input.closed);
    }

    @Test
    public void stopsReadingUnboundedContentWithoutSizeMetadata() {
        InputStream input = new InputStream() {
            int bytesRead;

            @Override
            public int read() {
                if (++bytesRead > LIMIT + 8192) {
                    throw new AssertionError("Read beyond size limit and one buffer");
                }
                return 0;
            }
        };
        Context context = register(input);
        assertThrows(IOException.class, () -> DataUtils.getUriContentData(context, URI, LIMIT));
    }

    @Test
    public void rejectsNegativeLimit() {
        Context context = RuntimeEnvironment.getApplication();
        assertThrows(IllegalArgumentException.class,
                () -> DataUtils.getUriContentData(context, URI, -1));
    }

    private Context register(InputStream input) {
        Context context = RuntimeEnvironment.getApplication();
        Shadows.shadowOf(context.getContentResolver()).registerInputStream(URI, input);
        return context;
    }

    private static class TrackingInputStream extends ByteArrayInputStream {
        boolean closed;

        TrackingInputStream(byte[] data) {
            super(data);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
