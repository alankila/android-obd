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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import fi.bel.android.obd.service.DataService;
import fi.bel.android.obd.util.OxygenSensor;
import fi.bel.android.obd.util.PID;

/**
 * This class implements a simple command-response protocol over the bluetooth
 * serial link.
 */
public class BluetoothRunnable implements Runnable {
    protected static final String TAG = BluetoothRunnable.class.getSimpleName();

    protected static final Charset ISO88591 = Charset.forName("ISO8859-1");

    /**
     * Perform an DTC/ELM transaction, if connection exists.
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

    /** Current phase/state of the BT connection. */
    public enum Phase {
        DISCONNECTED, CONNECTING, INITIALIZING, READY
    }

    /** Intent used to notify listeners about phase change */
    public static final String ACTION_PHASE = "fi.bel.android.obd.PHASE";

    /** Extra that holds the serialized Phase. */
    public static final String EXTRA_PHASE = "fi.bel.android.obd.PHASE";

    /** Well-known serial SPP */
    protected static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    /** Current BT device */
    private final AtomicReference<BluetoothDevice> device = new AtomicReference<>();

    /** Last used socket. */
    private final AtomicReference<BluetoothSocket> socket = new AtomicReference<>();

    /** Operations queue */
    private final BlockingQueue<Transaction> queue = new LinkedBlockingQueue<>();

    private final Handler handler;

    private final Context context;

    /* These are accessed only from the main thread. */
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
                pid.clear();
                pid13Supported = false;
                pid1dSupported = false;
            }
        });

        queue.clear();
        for (String command : new String[] { "ATSP0", "ATZ", "ATE0" }) {
            queue.add(new Transaction(command));
        }

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
            socket.set(device.get().createRfcommSocketToServiceRecord(SPP));
            socket.get().connect();
        }
        catch (IOException ioe) {
            return;
        }

        if (! socket.get().isConnected()) {
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
                socket.get().getOutputStream().write(cmd);

                final StringBuilder response = new StringBuilder();
                while (response.length() == 0 || response.charAt(response.length() - 1) != '>') {
                    int length = socket.get().getInputStream().read(data);
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
     * <p>
     * Next in chain: checkPid13.
     *
     * @param i pid to scan onwards from
     */
    private void checkPid(final int i) {
        queue.add(new Transaction(String.format("%02x%02x %d", 1, i, 1)) {
            @Override
            protected void success(String response) {
                int data = (int) Long.parseLong(response.substring(4, 12), 16);
                for (int j = 0; j < 31; j++) {
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
     * <p>
     * Next in chain: checkPid1d.
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
     * different (4 banks, 2 sensors).
     * <p>
     * This is the final check.
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
        return device.get();
    }

    /**
     * Set the device being worked with. This method should be called before starting
     * the thread.
     *
     * @param device BT device
     */
    public void setDevice(BluetoothDevice device) {
        this.device.set(device);
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
     * <p>
     * This method is synchronized because it may be called from UI thread or from this Runnable's
     * thread itself.
     */
    public synchronized void terminate() {
        if (socket.get() != null) {
            try {
                socket.get().close();
                socket.set(null);
            } catch (IOException e) {
            }
        }
    }
}
