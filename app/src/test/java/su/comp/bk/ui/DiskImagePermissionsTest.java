package su.comp.bk.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowContentResolver;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 36}, shadows = DiskImagePermissionsTest.PermissionResolver.class)
public class DiskImagePermissionsTest {
    private static final Uri URI = Uri.parse("content://test.provider/disk.bkd");

    @Test
    public void persistsOnlyReadPermissionForReadOnlyGrant() {
        BkEmuActivity activity = Robolectric.buildActivity(BkEmuActivity.class).get();
        PermissionResolver resolver = Shadow.extract(activity.getContentResolver());
        activity.persistDiskImageUriPermission(URI, Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        assertTrue(resolver.called);
        assertEquals(URI, resolver.uri);
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, resolver.flags);
    }

    @Test
    public void persistsReadAndWritePermissionWhenBothAreGranted() {
        BkEmuActivity activity = Robolectric.buildActivity(BkEmuActivity.class).get();
        PermissionResolver resolver = Shadow.extract(activity.getContentResolver());
        int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        activity.persistDiskImageUriPermission(URI,
                flags | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        assertTrue(resolver.called);
        assertEquals(flags, resolver.flags);
    }

    @Test
    public void skipsNonPersistableEmptyAndFileGrants() {
        BkEmuActivity activity = Robolectric.buildActivity(BkEmuActivity.class).get();
        PermissionResolver resolver = Shadow.extract(activity.getContentResolver());
        activity.persistDiskImageUriPermission(URI, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.persistDiskImageUriPermission(URI, Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        activity.persistDiskImageUriPermission(Uri.parse("file:///disk.bkd"),
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        assertFalse(resolver.called);
    }

    @Test
    public void toleratesDeniedPermissionPersistence() {
        BkEmuActivity activity = Robolectric.buildActivity(BkEmuActivity.class).get();
        PermissionResolver resolver = Shadow.extract(activity.getContentResolver());
        resolver.denyPermission = true;
        activity.persistDiskImageUriPermission(URI, Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        assertTrue(resolver.called);
    }

    @Implements(ContentResolver.class)
    public static class PermissionResolver extends ShadowContentResolver {
        boolean called;
        boolean denyPermission;
        Uri uri;
        int flags;

        @Implementation
        @Override
        protected void takePersistableUriPermission(Uri uri, int flags) {
            called = true;
            this.uri = uri;
            this.flags = flags;
            if (denyPermission) {
                throw new SecurityException("Permission persistence denied");
            }
        }
    }
}
