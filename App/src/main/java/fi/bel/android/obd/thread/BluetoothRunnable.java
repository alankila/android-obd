package fi.bel.android.obd.thread;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.nio.channels.Selector;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

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
        DISCONNECTED, CONNECTING, CONNECTED, INITIALIZING, READY
    }

    public interface Callback {
        public void setPhase(Phase phase);
    }

    protected static final String TAG = BluetoothRunnable.class.getSimpleName();

    protected static final Charset ISO88591 = Charset.forName("ISO8859-1");

    /** Well-known serial SPP */
    protected static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final BluetoothDevice device;

    private final Handler handler;

    private final Callback callback;

    private final Selector selector;

    private final BlockingQueue<Transaction> queue = new ArrayBlockingQueue<>(10);

    private final byte[] data = new byte[1024];

    private final Set<String> supportedPid = new TreeSet<>();

    public BluetoothRunnable(BluetoothDevice device, Handler handler, Callback callback) {
        this.device = device;
        this.handler = handler;
        this.callback = callback;
        try {
            this.selector = Selector.open();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                callback.setPhase(Phase.CONNECTING);
            }
        });

        queue.clear();
        for (String command : new String[] { "ATSP0", "ATZ", "ATE0" }) {
            queue.add(new Transaction(command) {
                @Override
                protected void success(String response) {
                    if (getCommand().equals("ATSP0")) {
                        callback.setPhase(Phase.INITIALIZING);
                    }

                    if (getCommand().equals("ATE0")) {
                        callback.setPhase(Phase.READY);
                    }
                }
            });
        }

        supportedPid.add("00"); /* 00 is our entry point, this PID must always exist. */
        checkPid(0);

        connectAndRun();

        handler.post(new Runnable() {
            @Override
            public void run() {
                callback.setPhase(Phase.DISCONNECTED);
            }
        });
    }

    private void connectAndRun() {
        BluetoothSocket socket;
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
                callback.setPhase(Phase.CONNECTED);
            }
        });

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
                    Log.i(TAG, "fragment: " + piece);
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
        } catch (IOException e) {
            Log.e(TAG, "Error closing socket", e);
        }
    }

    public void addTransaction(Transaction transaction) {
        queue.add(transaction);
    }

    public boolean pidSupported(String pid) {
        return supportedPid.contains(pid);
    }

    /**
     * Discover all known PID values
     *
     *
     */
    private void checkPid(final int i) {
        queue.add(new Transaction(String.format("%02x %02x %d", 1, i, 1)) {
            @Override
            protected void success(String response) {
                int data = Integer.valueOf(response.substring(4), 16);
                for (int j = 0; j < 32; j ++) {
                    if ((data & (1 << (31 - j))) != 0) {
                        String s = String.format("%02x", i + j);
                        supportedPid.add(s);
                        Log.i(TAG, "PID: " + s);
                    }
                }

                if (i < 256 - 32 && supportedPid.contains(String.format("%02x", i + 32))) {
                    checkPid(i + 32);
                }
            }
        });
    }
}
