package su.comp.bk.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;

import su.comp.bk.ui.BkEmuActivity;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 36})
public class DataUtilsTest {
    private static final int LIMIT = BkEmuActivity.MAX_BIN_IMAGE_FILE_SIZE;
    private static final Uri URI = Uri.parse("content://test.provider/image.bin");

    @Test
    public void fallsBackToUriSegmentWhenNameQueryIsDenied() {
        MetadataProvider provider = registerMetadataProvider();
        provider.denyQuery = true;
        assertEquals("image.bin", DataUtils.resolveUriFileName(
                RuntimeEnvironment.getApplication(), URI));
    }

    @Test
    public void returnsUnknownLengthWhenDescriptorAndQueryAreDenied() {
        MetadataProvider provider = registerMetadataProvider();
        provider.denyQuery = true;
        assertEquals(-1L, DataUtils.getUriFileLength(RuntimeEnvironment.getApplication(), URI));
    }

    @Test
    public void resolvesDisplayNameAndClosesCursor() {
        MetadataProvider provider = registerMetadataProvider();
        provider.cursor = new MatrixCursor(new String[] { OpenableColumns.DISPLAY_NAME });
        provider.cursor.addRow(new Object[] { "disk.bkd" });
        assertEquals("disk.bkd", DataUtils.resolveUriFileName(
                RuntimeEnvironment.getApplication(), URI));
        assertArrayEquals(new String[] { OpenableColumns.DISPLAY_NAME }, provider.projection);
        assertTrue(provider.cursor.isClosed());
    }

    @Test
    public void resolvesLengthFromMetadataAndClosesCursor() {
        MetadataProvider provider = registerMetadataProvider();
        provider.cursor = new MatrixCursor(new String[] { OpenableColumns.SIZE });
        provider.cursor.addRow(new Object[] { 819200L });
        assertEquals(819200L, DataUtils.getUriFileLength(RuntimeEnvironment.getApplication(), URI));
        assertArrayEquals(new String[] { OpenableColumns.SIZE }, provider.projection);
        assertTrue(provider.cursor.isClosed());
    }

    @Test
    public void fallsBackWhenProviderReturnsNoCursor() {
        registerMetadataProvider();
        Context context = RuntimeEnvironment.getApplication();
        assertEquals("image.bin", DataUtils.resolveUriFileName(context, URI));
        assertEquals(-1L, DataUtils.getUriFileLength(context, URI));
    }

    private MetadataProvider registerMetadataProvider() {
        MetadataProvider provider = new MetadataProvider();
        ProviderInfo info = new ProviderInfo();
        info.authority = URI.getAuthority();
        provider.attachInfo(RuntimeEnvironment.getApplication(), info);
        ShadowContentResolver.registerProviderInternal(info.authority, provider);
        return provider;
    }

    private static class MetadataProvider extends ContentProvider {
        boolean denyQuery;
        MatrixCursor cursor;
        String[] projection;

        @Override
        public boolean onCreate() {
            return true;
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection,
                            String[] selectionArgs, String sortOrder) {
            this.projection = projection;
            if (denyQuery) {
                throw new SecurityException("Metadata access denied");
            }
            return cursor;
        }

        @Override
        public AssetFileDescriptor openAssetFile(Uri uri, String mode) {
            throw new SecurityException("Descriptor access denied");
        }

        @Override
        public String getType(Uri uri) {
            return "application/octet-stream";
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int delete(Uri uri, String selection, String[] selectionArgs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
            throw new UnsupportedOperationException();
        }
    }

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
