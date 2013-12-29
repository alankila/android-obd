package fi.bel.android.obd.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;

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

    protected PowerManager.WakeLock wakelock;

    protected SQLiteDatabase db;

    protected SQLiteStatement idStatement;

    protected SQLiteStatement valueStatement;

    protected SQLiteStatement insertStatement;

    protected ConnectionFragment connectionFragment;

    protected long nextCollectTime;

    protected Handler handler;

    protected boolean running;

    protected final Runnable collect = new Runnable() {
        @Override
        public void run() {
            if (running) {
                collect();

                nextCollectTime += COLLECT_INTERVAL_MS;
                long sleepTime = nextCollectTime - System.currentTimeMillis();
                if (sleepTime < 0) {
                    sleepTime = 0;
                }
                handler.postDelayed(this, sleepTime);
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Notification notification = new Notification();
        notification.flags |= Notification.FLAG_FOREGROUND_SERVICE;
        notification.icon = R.drawable.ic_launcher;
        notification.contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, ContainerActivity.class), 0);
        notification.tickerText = "Active connection to BT device";
        startForeground(1, notification);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, null);
        wakelock.acquire();

        db = SQLiteDatabase.openOrCreateDatabase(getDatabasePath("data"), null);
        db.execSQL("CREATE TABLE IF NOT EXISTS data (timestamp long, pid varchar(2), value float)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS i_data_pid ON data (pid, rowid)");
        idStatement = db.compileStatement("SELECT max(rowid) FROM data WHERE pid = ?");
        valueStatement = db.compileStatement("SELECT value FROM data WHERE rowid = ?");
        insertStatement = db.compileStatement("INSERT INTO data (timestamp, pid, value) VALUES (?, ?, ?)");

        connectionFragment = (ConnectionFragment) ContainerActivity.FRAGMENTS.get(0);
        handler = new Handler();

        nextCollectTime = System.currentTimeMillis();
        handler.post(collect);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(collect);
        db.close();
        wakelock.release();
    }

    private void collect() {
        for (int i = 1; i < 0x100; i += 1) {
            final String pid = String.format("%02x", i);
            if (connectionFragment.pidSupported(pid) && OBD.unit(pid) != null) {
                String cmd = String.format("%02x %02x %d", 1, pid, 1);
                connectionFragment.sendCommand(new BluetoothRunnable.Transaction(cmd) {
                    @Override
                    protected void success(String response) {
                        float newValue = OBD.convert(pid, response);
                        idStatement.bindString(1, pid);
                        long id = idStatement.simpleQueryForLong();
                        valueStatement.bindLong(1, id);
                        float dbValue = Float.parseFloat(valueStatement.simpleQueryForString());
                        if (dbValue != newValue) {
                            insertStatement.bindLong(1, System.currentTimeMillis());
                            insertStatement.bindString(2, pid);
                            insertStatement.bindDouble(3, newValue);
                            insertStatement.executeInsert();
                        }
                    }
                });
            }
        }
    }
}
