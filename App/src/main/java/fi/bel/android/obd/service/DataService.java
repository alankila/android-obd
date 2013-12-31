package fi.bel.android.obd.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQuery;
import android.database.sqlite.SQLiteStatement;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import fi.bel.android.obd.ContainerActivity;
import fi.bel.android.obd.R;
import fi.bel.android.obd.fragment.ConnectionFragment;
import fi.bel.android.obd.thread.BluetoothRunnable;
import fi.bel.android.obd.util.OBD;

/**
 * Created by alankila on 30.12.2013.
 */
public class DataService extends Service {
    protected static final String TAG = DataService.class.getSimpleName();

    protected static final int COLLECT_INTERVAL_MS = 10000;

    public static SQLiteDatabase openDatabase(Context context) {
        context.getDatabasePath(".").getParentFile().mkdirs();
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("data"), null);
        db.execSQL("CREATE TABLE IF NOT EXISTS data (timestamp long, pid varchar(2), value float)");
        db.execSQL("CREATE INDEX IF NOT EXISTS i_data_pid ON data (pid)");
        return db;
    }

    protected PowerManager.WakeLock wakelock;

    protected SQLiteDatabase db;

    protected SQLiteStatement idStatement;

    protected SQLiteStatement valueStatement;

    protected SQLiteStatement insertStatement;

    protected ConnectionFragment connectionFragment;

    protected long nextCollectTime;

    protected Handler handler;

    protected final Runnable collect = new Runnable() {
        @Override
        public void run() {
            collect();

            nextCollectTime += COLLECT_INTERVAL_MS;
            long sleepTime = nextCollectTime - System.currentTimeMillis();
            if (sleepTime < 0) {
                sleepTime = 0;
            }
            handler.postDelayed(this, sleepTime);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Starting BG service");

        Notification.Builder notification = new Notification.Builder(this);
        notification.setSmallIcon(R.drawable.ic_launcher);
        notification.setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, ContainerActivity.class), 0));
        notification.setContentTitle("Active connection to BT device");
        startForeground(1, notification.build());

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Sensor Data Collection Over BT");
        wakelock.acquire();

        db = openDatabase(this);
        insertStatement = db.compileStatement("INSERT INTO data (timestamp, pid, value) VALUES (?, ?, ?)");

        connectionFragment = (ConnectionFragment) ContainerActivity.FRAGMENTS.get(0);
        handler = new Handler();

        nextCollectTime = System.currentTimeMillis();
        handler.post(collect);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Stopping service");

        handler.removeCallbacks(collect);
        db.close();
        wakelock.release();
    }

    private void collect() {
        for (final String pid : connectionFragment.pid()) {
            String cmd = String.format("%02x %s %d", 1, pid, 1);
            connectionFragment.sendCommand(new BluetoothRunnable.Transaction(cmd) {
                @Override
                protected void success(String response) {
                    float newValue = OBD.convert(pid, response);

                    Cursor cursor = db.rawQuery("SELECT value FROM data WHERE rowid = (SELECT max(rowid) FROM data WHERE pid = ?)", new String[] { pid });
                    if (cursor.moveToFirst()) {
                        float dbValue = cursor.getFloat(0);
                        if (dbValue == newValue) {
                            return;
                        }
                    }
                    cursor.close();

                    insertStatement.bindLong(1, System.currentTimeMillis());
                    insertStatement.bindString(2, pid);
                    insertStatement.bindDouble(3, newValue);
                    insertStatement.executeInsert();
                }
            });
        }
    }
}
