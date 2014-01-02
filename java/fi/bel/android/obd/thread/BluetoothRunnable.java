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
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import fi.bel.android.obd.service.DataService;

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

    private final Set<String> supportedPid = new TreeSet<>();

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

        supportedPid.add("00"); /* 00 is our entry point, this PID must always exist. */
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

        try {
            socket.close();
            socket = null;
        } catch (IOException e) {
            Log.e(TAG, "Error closing socket", e);
        }
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
    public Set<String> pid() {
        return supportedPid;
    }

    /**
     * Discover all known PID values
     *
     * @param i pid to scan onwards from
     */
    private void checkPid(final int i) {
        queue.add(new Transaction(String.format("%02x %02x %d", 1, i, 1)) {
            @Override
            protected void success(String response) {
                int data = (int) Long.parseLong(response.substring(4), 16);
                for (int j = 0; j < 32; j ++) {
                    if ((data & (1 << (31 - j))) != 0) {
                        int pid = i + j + 1;
                        String s = String.format("%02x", pid);
                        supportedPid.add(s);
                    }
                }

                if (i != 0xe0 && supportedPid.contains(String.format("%02x", i + 32))) {
                    checkPid(i + 32);
                } else {
                    setPhase(Phase.READY);
                }
            }
        });
    }

    public BluetoothDevice getDevice() {
        return device;
    }

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
     */
    public void terminate() {
        if (socket != null) {
            try {
                socket.close();
                socket = null;
            } catch (IOException e) {
            }
        }
    }
}
