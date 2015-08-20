package de.illogical.modo;

import android.app.backup.BackupAgentHelper;
import android.app.backup.BackupDataInputStream;
import android.app.backup.BackupDataOutput;
import android.app.backup.BackupHelper;
import android.app.backup.SharedPreferencesBackupHelper;
import android.os.ParcelFileDescriptor;

final public class DatabaseBackup2 extends BackupAgentHelper {

    class DatabaseHelper implements BackupHelper {
        public void performBackup(ParcelFileDescriptor arg0, BackupDataOutput arg1, ParcelFileDescriptor arg2) {
            PlaylistManager.backupToCloud(DatabaseBackup2.this, arg1);
        }

        public void restoreEntity(BackupDataInputStream arg0) {
            PlaylistManager.restoreFromCloud(DatabaseBackup2.this, arg0);
        }

        public void writeNewStateDescription(ParcelFileDescriptor arg0) {}
    }

    public void onCreate() {
        super.onCreate();
        addHelper("PREFS", new SharedPreferencesBackupHelper(this, "de.illogical.modo_preferences"));
        addHelper("MODO_PLAYLISTS", new DatabaseHelper());
    }
}
