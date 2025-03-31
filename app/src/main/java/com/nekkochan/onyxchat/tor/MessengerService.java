package com.nekkochan.onyxchat.tor;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.nekkochan.onyxchat.crypto.PQCProvider;
import com.nekkochan.onyxchat.data.Message;
import com.nekkochan.onyxchat.data.Repository;
import com.nekkochan.onyxchat.data.User;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service for handling secure message exchange over Tor network
 */
public class MessengerService extends Service implements TorManager.TorConnectionListener {
    private static final String TAG = "MessengerService";
    private static final int MESSAGE_SERVER_PORT = 9030;
    private static final int CONNECTION_TIMEOUT = 60000; // 60 seconds

    private final IBinder binder = new MessengerBinder();
    private TorManager torManager;
    private Repository repository;
    private ExecutorService executorService;
    private ServerSocket serverSocket;
    private boolean isRunning = false;
    private Handler mainHandler;
    private Map<String, MessageListener> messageListeners = new HashMap<>();
    
    // Connection pool for reusing connections to contacts
    private final Map<String, Socket> connectionPool = new HashMap<>();

    @Override
    public void onCreate() {
        super.onCreate();
        torManager = TorManager.getInstance(this);
        repository = new Repository(getApplication());
        executorService = Executors.newCachedThreadPool();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startService();
        return START_STICKY;
    }

    /**
     * Start the messenger service and connect to Tor
     */
    public void startService() {
        if (isRunning) {
            return;
        }
        
        // Start Tor and register for callbacks
        torManager.startTor(this);
    }

    /**
     * Stop the messenger service
     */
    public void stopService() {
        isRunning = false;
        
        // Close all connections
        synchronized (connectionPool) {
            for (Socket socket : connectionPool.values()) {
                try {
                    socket.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing socket: " + e.getMessage());
                }
            }
            connectionPool.clear();
        }
        
        // Stop the server
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing server socket: " + e.getMessage());
            }
        }
        
        // Stop the executor service
        executorService.shutdown();
        
        // Stop Tor
        torManager.stopTor();
    }

    @Override
    public void onDestroy() {
        stopService();
        super.onDestroy();
    }

    @Override
    public void onTorConnected(String onionAddress) {
        Log.d(TAG, "Tor connected with address: " + onionAddress);
        isRunning = true;
        
        // Start the message server
        startMessageServer();
    }

    @Override
    public void onTorDisconnected() {
        Log.d(TAG, "Tor disconnected");
        isRunning = false;
    }

    @Override
    public void onTorError(String errorMessage) {
        Log.e(TAG, "Tor error: " + errorMessage);
        isRunning = false;
    }

    /**
     * Start the server to listen for incoming messages
     */
    private void startMessageServer() {
        executorService.execute(() -> {
            try {
                serverSocket = new ServerSocket(MESSAGE_SERVER_PORT);
                Log.d(TAG, "Message server started on port " + MESSAGE_SERVER_PORT);
                
                while (isRunning && !serverSocket.isClosed()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        executorService.execute(() -> handleIncomingConnection(clientSocket));
                    } catch (IOException e) {
                        if (isRunning) {
                            Log.e(TAG, "Error accepting connection: " + e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Error starting message server: " + e.getMessage());
            }
        });
    }

    /**
     * Handle an incoming connection
     */
    private void handleIncomingConnection(Socket socket) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String senderAddress = reader.readLine();
            String encapsulation = reader.readLine();
            String encryptedIv = reader.readLine();
            String encryptedContent = reader.readLine();
            
            Log.d(TAG, "Received message from: " + senderAddress);
            
            // Find the sender in our database
            User sender = repository.userDao().getUserByAddress(senderAddress);
            
            if (sender == null) {
                Log.e(TAG, "Message received from unknown sender: " + senderAddress);
                socket.close();
                return;
            }
            
            // Get our private key
            User currentUser = repository.userDao().getCurrentUser();
            PrivateKey privateKey = PQCProvider.decodePrivateKey(currentUser.getPublicKey());
            
            // Decrypt the message
            byte[] secretKey = PQCProvider.decapsulateKey(privateKey, encapsulation);
            PQCProvider.EncryptedData encryptedData = new PQCProvider.EncryptedData(encryptedIv, encryptedContent);
            String plaintext = PQCProvider.decryptWithAES(encryptedData, secretKey);
            
            // Create and save the message
            Message message = new Message(
                    repository.generateUniqueId(),
                    senderAddress,
                    currentUser.getOnionAddress(),
                    encryptedContent
            );
            
            repository.insertMessage(message);
            
            // Notify any listeners
            notifyMessageReceived(senderAddress, message);
            
            socket.close();
        } catch (Exception e) {
            Log.e(TAG, "Error handling incoming connection: " + e.getMessage());
            try {
                socket.close();
            } catch (IOException ex) {
                Log.e(TAG, "Error closing socket: " + ex.getMessage());
            }
        }
    }

    /**
     * Send a message to a contact
     * @param recipientAddress The onion address of the recipient
     * @param content The message content
     * @param expirationTime Time when the message should expire (0 for never)
     * @return True if the message was sent successfully
     */
    public boolean sendMessage(String recipientAddress, String content, long expirationTime) {
        if (!isRunning) {
            Log.e(TAG, "Cannot send message, service not running");
            return false;
        }
        
        try {
            // Get the current user
            User currentUser = repository.userDao().getCurrentUser();
            if (currentUser == null) {
                Log.e(TAG, "Cannot send message, current user not found");
                return false;
            }
            
            // Get the recipient
            User recipient = repository.userDao().getUserByAddress(recipientAddress);
            if (recipient == null) {
                Log.e(TAG, "Cannot send message, recipient not found: " + recipientAddress);
                return false;
            }
            
            // Get recipient's public key
            PublicKey recipientPublicKey = PQCProvider.decodePublicKey(recipient.getPublicKey());
            
            // Encrypt the message
            PQCProvider.EncapsulatedKey encapsulatedKey = PQCProvider.encapsulateKey(recipientPublicKey);
            PQCProvider.EncryptedData encryptedData = PQCProvider.encryptWithAES(content, encapsulatedKey.getSecretKey());
            
            // Save the message locally
            Message message = repository.sendMessage(
                    currentUser.getOnionAddress(),
                    recipientAddress,
                    encryptedData.getCiphertext(),
                    expirationTime
            );
            
            // Send the message over Tor
            executorService.execute(() -> {
                try {
                    // Get or create a connection to the recipient
                    Socket socket = getConnectionToHost(recipientAddress, MESSAGE_SERVER_PORT);
                    
                    // Send the message
                    PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                    writer.println(currentUser.getOnionAddress());
                    writer.println(encapsulatedKey.getEncapsulation());
                    writer.println(encryptedData.getIv());
                    writer.println(encryptedData.getCiphertext());
                    
                    // Close the connection
                    socket.close();
                    
                    // Update the message as sent
                    message.setSent(true);
                    repository.updateMessage(message);
                    
                    Log.d(TAG, "Message sent to: " + recipientAddress);
                } catch (Exception e) {
                    Log.e(TAG, "Error sending message: " + e.getMessage());
                    
                    // Mark the message as not sent
                    message.setSent(false);
                    repository.updateMessage(message);
                }
            });
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error preparing message: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get a connection to a host through Tor
     */
    private Socket getConnectionToHost(String onionAddress, int port) throws IOException {
        synchronized (connectionPool) {
            // Check if we have an existing connection
            Socket existingSocket = connectionPool.get(onionAddress);
            if (existingSocket != null && !existingSocket.isClosed()) {
                return existingSocket;
            }
            
            // Create a new connection
            Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("localhost", TorManager.TOR_SOCKS_PORT));
            Socket socket = new Socket(proxy);
            socket.connect(new InetSocketAddress(onionAddress + ".onion", port), CONNECTION_TIMEOUT);
            
            // Add to the pool
            connectionPool.put(onionAddress, socket);
            return socket;
        }
    }

    /**
     * Register a listener for messages from a specific contact
     */
    public void registerMessageListener(String contactAddress, MessageListener listener) {
        synchronized (messageListeners) {
            messageListeners.put(contactAddress, listener);
        }
    }

    /**
     * Unregister a message listener
     */
    public void unregisterMessageListener(String contactAddress) {
        synchronized (messageListeners) {
            messageListeners.remove(contactAddress);
        }
    }

    /**
     * Notify listeners of a received message
     */
    private void notifyMessageReceived(String senderAddress, Message message) {
        synchronized (messageListeners) {
            MessageListener listener = messageListeners.get(senderAddress);
            if (listener != null) {
                mainHandler.post(() -> listener.onMessageReceived(message));
            }
        }
    }

    /**
     * Binder class for clients to get the service
     */
    public class MessengerBinder extends Binder {
        public MessengerService getService() {
            return MessengerService.this;
        }
    }

    /**
     * Interface for receiving messages
     */
    public interface MessageListener {
        void onMessageReceived(Message message);
    }
} 