package fi.bel.android.obd.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import fi.bel.android.obd.ContainerActivity;
import fi.bel.android.obd.R;
import fi.bel.android.obd.thread.BluetoothRunnable;
import fi.bel.android.obd.util.PID;

/**
 * Background service that collects data when connection to BT device has been established.
 */
public class DataService extends Service {
    protected static final String TAG = DataService.class.getSimpleName();

    protected static final int COLLECT_INTERVAL_MS = 5000;

    public static final String NEW_DATA = "fi.bel.android.obd.NEW_DATA";

    public static SQLiteDatabase openDatabase(Context context) {
        context.getDatabasePath(".").getParentFile().mkdirs();
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("data2"), null);
        db.execSQL("CREATE TABLE IF NOT EXISTS data (timestamp long, pid int, value varchar(8))");
        db.execSQL("CREATE INDEX IF NOT EXISTS i_data_pid ON data (pid)");
        return db;
    }

    protected PowerManager.WakeLock wakelock;

    protected SQLiteDatabase db;

    protected SQLiteStatement insertStatement;

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
        wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DTC Data Collection Over BT");
        wakelock.acquire();

        db = openDatabase(this);
        db.execSQL("DELETE FROM data");
        insertStatement = db.compileStatement("INSERT INTO data (timestamp, code, value) VALUES (?, ?, ?)");

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

    protected void collect() {
        for (final PID pid : ContainerActivity.BLUETOOTH_RUNNABLE.pid()) {
            String cmd = String.format("%02x%02x %d", 1, pid.getCode(), 1);
            ContainerActivity.BLUETOOTH_RUNNABLE.addTransaction(new BluetoothRunnable.Transaction(cmd) {
                @Override
                protected void success(String response) {
                    long time = System.currentTimeMillis();
                    int code = pid.getCode();
                    response = response.substring(4);

                    Cursor cursor = db.rawQuery(
                            "SELECT value FROM data WHERE rowid = (SELECT max(rowid) FROM data WHERE pid = ?)",
                            new String[] { String.valueOf(code) }
                    );
                    if (cursor.moveToFirst()) {
                        String dbValue = cursor.getString(0);
                        if (! response.equals(dbValue)) {
                            insertStatement.bindLong(1, time);
                            insertStatement.bindLong(2, pid.getCode());
                            insertStatement.bindString(3, response);
                            insertStatement.executeInsert();
                        }
                    }
                    cursor.close();

                    Intent newData = new Intent(NEW_DATA);
                    newData.putExtra("timestamp", time);
                    newData.putExtra("pid", code);
                    newData.putExtra("value", response);
                    sendBroadcast(newData);
                }
            });
        }
    }
}
