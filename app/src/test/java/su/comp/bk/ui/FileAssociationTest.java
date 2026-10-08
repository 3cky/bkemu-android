package su.comp.bk.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 36})
public class FileAssociationTest {
    @Test
    public void opensBinaryContentWithOpaqueDocumentId() {
        assertTrue(canOpen("content://com.android.providers.downloads.documents/document/12345",
                "application/octet-stream"));
    }

    @Test
    public void opensTypedFilesWithoutHostOrFilenamePatternRestrictions() {
        for (String fileName : new String[] {
                "game.Bin", "disk.bKd", "disk.Hdi", "disk.iMg", "snapshot.BkEmu_State",
                "game.v1.release.backup.copy.bin"
        }) {
            assertTrue(fileName, canOpen("file:///storage/emulated/0/" + fileName,
                    "application/octet-stream"));
        }
    }

    @Test
    public void opensLegacyFilesWithoutMimeType() {
        assertTrue(canOpen("file:///storage/emulated/0/game.bin", null));
    }

    @Test
    public void doesNotAdvertiseWebLinks() {
        for (String scheme : new String[] {"http", "https"}) {
            String uri = scheme + "://example.com/game.bin";
            assertFalse(canOpen(uri, null));
            assertFalse(canOpen(uri, "application/octet-stream"));
        }
    }

    @Test
    public void doesNotAdvertiseUnrelatedMimeTypes() {
        assertFalse(canOpen("content://example.provider/document/12345", "image/jpeg"));
        assertFalse(canOpen("file:///storage/emulated/0/photo.jpg", "image/jpeg"));
    }

    private boolean canOpen(String uri, String mimeType) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.parse(uri), mimeType);
        intent.setPackage(RuntimeEnvironment.getApplication().getPackageName());
        PackageManager packageManager = RuntimeEnvironment.getApplication().getPackageManager();
        for (ResolveInfo info : packageManager.queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY)) {
            if (BkEmuActivity.class.getName().equals(info.activityInfo.name)) {
                return true;
            }
        }
        return false;
    }
}
