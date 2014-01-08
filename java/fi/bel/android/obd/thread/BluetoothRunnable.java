package fi.bel.android.obd.thread;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentSkipListSet;

import fi.bel.android.obd.service.DataService;
import fi.bel.android.obd.util.OxygenSensor;
import fi.bel.android.obd.util.PID;

/**
 * This class implements a simple command-response protocol over the bluetooth
 * serial link.
 */
public class BluetoothRunnable implements Runnable {
    /**
     * Perform an OBD/ELM transaction, if connection exists.
     * <p>
     * Terminates in either success() or failed() call.
     */
    public static class Transaction {
        private final String command;

        public Transaction(String command) {
            this.command = command;
        }

        public String getCommand() {
            return command;
        }

        /** Runs on UI thread */
        protected void success(String response) {
        }

        /** Runs on UI thread */
        protected void failed() {
        }
    }

    public enum Phase {
        DISCONNECTED, CONNECTING, INITIALIZING, READY
    }

    public static final String ACTION_PHASE = "fi.bel.android.obd.PHASE";

    public static final String EXTRA_PHASE = "fi.bel.android.obd.PHASE";

    protected static final String TAG = BluetoothRunnable.class.getSimpleName();

    protected static final Charset ISO88591 = Charset.forName("ISO8859-1");

    /** Well-known serial SPP */
    protected static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private BluetoothDevice device;

    private BluetoothSocket socket;

    private final Handler handler;

    private final Context context;

    private final BlockingQueue<Transaction> queue = new ArrayBlockingQueue<>(100);

    private final Set<PID> pid = new ConcurrentSkipListSet<>();

    private boolean pid13Supported;

    private boolean pid1dSupported;

    protected Phase phase = null;

    public BluetoothRunnable(Handler handler, Context context) {
        this.handler = handler;
        this.context = context;
        setPhase(Phase.DISCONNECTED);
    }

    @Override
    public void run() {
        handler.post(new Runnable() {
            @Override
            public void run() {
               setPhase(Phase.CONNECTING);
            }
        });

        queue.clear();
        for (String command : new String[] { "ATSP0", "ATZ", "ATE0" }) {
            queue.add(new Transaction(command));
        }

        pid.clear();
        pid13Supported = false;
        pid1dSupported = false;

        checkPid(0);

        connectAndRun();
        if (phase == Phase.READY) {
            context.stopService(new Intent(context, DataService.class));
        }

        handler.post(new Runnable() {
            @Override
            public void run() {
                setPhase(Phase.DISCONNECTED);
            }
        });
    }

    private void connectAndRun() {
        try {
            socket = device.createRfcommSocketToServiceRecord(SPP);
            socket.connect();
        }
        catch (IOException ioe) {
            return;
        }

        if (! socket.isConnected()) {
            return;
        }

        handler.post(new Runnable() {
            @Override
            public void run() {
                setPhase(Phase.INITIALIZING);
            }
        });

        byte[] data = new byte[256];
        while (! Thread.interrupted()) {
            final Transaction transaction;
            try {
                transaction = queue.take();
            }
            catch (InterruptedException ie) {
                Log.e(TAG, "Caught InterruptException, going away");
                break;
            }

            try {
                String command = transaction.getCommand();
                Log.i(TAG, "-> " + command);
                byte[] cmd = (command + "\r\n").getBytes(ISO88591);
                socket.getOutputStream().write(cmd);

                final StringBuilder response = new StringBuilder();
                while (response.length() == 0 || response.charAt(response.length() - 1) != '>') {
                    int length = socket.getInputStream().read(data);
                    String piece = new String(data, 0, length, ISO88591);
                    piece = piece.replaceAll("\\s", "");
                    response.append(piece);
                }
                response.deleteCharAt(response.length() - 1);

                Log.i(TAG, "<- " + response);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        transaction.success(response.toString());
                    }
                });
            }
            catch (IOException ioe) {
                Log.e(TAG, "Error during read/write", ioe);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        transaction.failed();
                    }
                });
                break;
            }
        }

        terminate();
    }

    /**
     * Add a transaction on the transaction queue
     *
     * @param transaction
     */
    public void addTransaction(Transaction transaction) {
        queue.add(transaction);
    }

    /**
     * Returns the set of supported PIDs
     *
     * @return true if supported
     */
    public Set<PID> pid() {
        return pid;
    }

    /**
     * Discover all known PID values of the car.
     *
     * @param i pid to scan onwards from
     */
    private void checkPid(final int i) {
        boolean add = queue.add(new Transaction(String.format("%02x%02x %d", 1, i, 1)) {
            @Override
            protected void success(String response) {
                int data = (int) Long.parseLong(response.substring(4, 12), 16);
                for (int j = 0; j < 32; j++) {
                    if ((data & (1 << (31 - j))) != 0) {
                        int code = i + j + 1;
                        PID obj = PID.make(code);
                        if (obj != null) {
                            pid.add(obj);
                        }
                        if (code == 0x13) {
                            pid13Supported = true;
                        }
                        if (code == 0x1d) {
                            pid1dSupported = true;
                        }
                    }
                }

                if (i != 0xe0 && (data & 1) != 0) {
                    checkPid(i + 32);
                } else {
                    checkPid13();
                }
            }
        });
    }

    /**
     * Discover pid 14-1b support from pid 13. (4 sensors, 2 banks).
     * We will then try 1d afterwards.
     */
    private void checkPid13() {
        if (! pid13Supported) {
            checkPid1d();
            return;
        }
        queue.add(new Transaction(String.format("%02x%02x %d", 1, 0x13, 1)) {
            @Override
            protected void success(String response) {
                int data = Integer.parseInt(response.substring(4, 6), 16);
                for (int i = 0; i < 8; i += 1) {
                    if ((data & (1 << i)) != 0) {
                        pid.add(new OxygenSensor(0x14 + i, i >> 2, i & 3));
                    }
                }

                checkPid1d();
            }
        });
    }

    /**
     * Check pid 1d. This also reports support for 14-1b, but the meanings are
     * different (2 sensors, 4 banks). We currently have no way to encode this
     * information in pid. We might be best off ignoring 1d altogether. :-/
     */
    private void checkPid1d() {
        if (! pid1dSupported) {
            setPhase(Phase.READY);
            return;
        }
        queue.add(new Transaction(String.format("%02x%02x %d", 1, 0x1d, 1)) {
            @Override
            protected void success(String response) {
                int data = Integer.parseInt(response.substring(4, 6), 16);
                for (int i = 0; i < 8; i += 1) {
                    if ((data & (1 << i)) != 0) {
                        pid.add(new OxygenSensor(0x14 + i, i >> 1, i & 3));
                    }
                }

                setPhase(Phase.READY);
            }
        });
    }

    /**
     * Return the device this runnable is interacting with
     *
     * @return BT device
     */
    public BluetoothDevice getDevice() {
        return device;
    }

    /**
     * Set the device being worked with. This method should be called before starting
     * the thread.
     *
     * @param device BT device
     */
    public void setDevice(BluetoothDevice device) {
        this.device = device;
    }

    /**
     * Get the current phase
     */
    public Phase getPhase() {
        return phase;
    }

    /**
     * Change phase. We detect when we enter READY state and leave it, and control the DataService
     * while at it.
     *
     * @param phase
     */
    protected void setPhase(BluetoothRunnable.Phase phase) {
        if (Thread.currentThread() != handler.getLooper().getThread()) {
            throw new RuntimeException("Invoked in wrong thread");
        }

        /* Start/Stop dataservice depending on connection phase. */
        if (this.phase != BluetoothRunnable.Phase.READY && phase == BluetoothRunnable.Phase.READY) {
            context.startService(new Intent(context, DataService.class));
        }
        if (this.phase == BluetoothRunnable.Phase.READY && phase != BluetoothRunnable.Phase.READY) {
            context.stopService(new Intent(context, DataService.class));
        }
        context.sendBroadcast(new Intent(ACTION_PHASE).putExtra(EXTRA_PHASE, phase));
        this.phase = phase;
    }

    /**
     * Terminates any ongoing transaction by closing the handles.
     *
     * This method is synchronized because it may be called from UI thread or from this Runnable's
     * thread itself.
     */
    public synchronized void terminate() {
        if (socket != null) {
            try {
                socket.close();
                socket = null;
            } catch (IOException e) {
            }
        }
    }
}
