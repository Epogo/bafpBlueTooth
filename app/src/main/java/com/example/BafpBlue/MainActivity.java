package com.example.BafpBlue;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MenuInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 2;
    private static final int REQUEST_ENABLE_BT = 1;

    private static final String LAST_CONNECTED_DEVICE = "last_connected_device";
    private BluetoothAdapter bluetoothAdapter;
    private ArrayList<BluetoothDevice> deviceList = new ArrayList<>();
    private BluetoothSocket bluetoothSocket;
    private BluetoothDevice connectedDevice = null;
    private ArrayAdapter<String> deviceAdapter;
    private ListView deviceListView;

    private Button scanAgainButton;
    private TextView connectionStatusTextView;

    private BroadcastReceiver bluetoothReceiver;
    private boolean isReceiverRegistered = false;

    // UUID for Bluetooth SPP (Serial Port Profile)
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // Handler and Runnable for connection check
    private Handler connectionCheckHandler;
    private Runnable connectionCheckRunnable;

    private Toolbar toolbar;

    private boolean isConnecting = false; // Track connection attempts
    private boolean alertShown = false; // Flag to control alert display
    private BroadcastReceiver backgroundToggleReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean isRunningInBackground = intent.getBooleanExtra("isRunningInBackground", false);
            if (isRunningInBackground) {
                // Start the BluetoothService
                Intent serviceIntent = new Intent(MainActivity.this, BluetoothService.class);
                ContextCompat.startForegroundService(MainActivity.this, serviceIntent);
            } else {
                // Stop the BluetoothService
                Intent serviceIntent = new Intent(MainActivity.this, BluetoothService.class);
                stopService(serviceIntent);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Remove the default title in the action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        // Set up your custom TextView title
        TextView toolbarTitle = findViewById(R.id.toolbar_title);
        toolbarTitle.setText("BafpBlue");

        toolbar.setOnClickListener(this::showPopupMenu);

        // Check if the app should run in the background
        SharedPreferences sharedPreferences = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean isRunningInBackground = sharedPreferences.getBoolean("RUNNING_IN_BACKGROUND", false);

        initializeViews();

        // Initialize Handler
        connectionCheckHandler = new Handler();

        // Define Runnable for checking connection status
        connectionCheckRunnable = new Runnable() {
            @Override
            public void run() {
                checkConnectionStatus();
                Log.d(TAG, "Checking connection status");
                connectionCheckHandler.postDelayed(this, 20000); // Check every 20 seconds
            }
        };

        checkBluetoothPermissionsAndInitialize();
        if (isRunningInBackground) {
            // Start the BluetoothService
            Intent serviceIntent = new Intent(this, BluetoothService.class);
            ContextCompat.startForegroundService(this, serviceIntent);
        }

        // Create Notification Channel for Android 8.0 and above
        createNotificationChannel();

        // Register the BroadcastReceiver
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        bluetoothReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Your receiver logic
            }
        };
        registerReceiver(bluetoothReceiver, filter);
        isReceiverRegistered = true; // Set flag to indicate receiver is registered

        // Register the receiver
        LocalBroadcastManager.getInstance(this).registerReceiver(backgroundToggleReceiver,
                new IntentFilter("BackgroundServiceToggle"));
    }

    private void showPopupMenu(View view) {
        PopupMenu popup = new PopupMenu(this, view);
        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.popup_menu, popup.getMenu());

        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.settings) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                return true;
            }
            // Add other cases for more menu items if needed
            return false;
        });

        popup.show();
    }


    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = "DISCONNECT_CHANNEL_ID";
            CharSequence channelName = "Bluetooth Disconnection Alerts";
            String description = "Alerts when a Bluetooth device is disconnected";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(channelId, channelName, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }


    private void initializeViews() {
        connectionStatusTextView = findViewById(R.id.connection_status);
        deviceListView = findViewById(R.id.device_list_view);
        deviceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        deviceListView.setAdapter(deviceAdapter);

        // Set "N/A" as default connection status
        connectionStatusTextView.setText("Connected Device: N/A");

        // Handle device selection
        deviceListView.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> {
            BluetoothDevice selectedDevice = deviceList.get(position);
            connectToDevice(selectedDevice);
        });

        // Initialize the "Scan Again" button
        scanAgainButton = findViewById(R.id.scan_again_button);
        scanAgainButton.setOnClickListener(v -> scanForDevices());
    }

    private void checkBluetoothPermissionsAndInitialize() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                }, REQUEST_BLUETOOTH_PERMISSIONS);
            } else {
                initializeBluetooth();
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.BLUETOOTH,
                        Manifest.permission.BLUETOOTH_ADMIN
                }, REQUEST_BLUETOOTH_PERMISSIONS);
            } else {
                initializeBluetooth();
            }
        }
    }

    private void initializeBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            Log.e(TAG, "Device doesn't support Bluetooth");
            Toast.makeText(this, "Bluetooth is not available on this device", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Check if Bluetooth is enabled
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return;
        }

        // Request necessary permissions based on SDK version
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.BLUETOOTH,
                        Manifest.permission.BLUETOOTH_ADMIN
                }, REQUEST_BLUETOOTH_PERMISSIONS);
                return;
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                }, REQUEST_BLUETOOTH_PERMISSIONS);
                return;
            }
        }

        // Retrieve the last connected device address
        String lastDeviceAddress = getSharedPreferences("BluetoothPrefs", MODE_PRIVATE)
                .getString(LAST_CONNECTED_DEVICE, null);

        if (lastDeviceAddress != null) {
            BluetoothDevice lastDevice = bluetoothAdapter.getRemoteDevice(lastDeviceAddress);

            // Check if the device is still paired and available
            if (lastDevice != null && lastDevice.getBondState() == BluetoothDevice.BOND_BONDED) {
                connectToPairedDevice(lastDevice);
                return;
            }
        }

        // Proceed with scanning for devices if no last device found or if connection fails
        scanForDevices();

        // Ensure Handler is not null before posting Runnable
        if (connectionCheckHandler != null) {
            connectionCheckHandler.post(connectionCheckRunnable);
        }
    }

    private void scanForDevices() {
        deviceList.clear();
        deviceAdapter.clear();

        // Register a receiver for found devices and discovery finished
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(receiver, filter);

        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(receiver, filter);

        // Start discovery if permission is granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        bluetoothAdapter.startDiscovery();
        Toast.makeText(this, "Scanning for devices...", Toast.LENGTH_SHORT).show();
    }

    // BroadcastReceiver for handling discovered devices and discovery completion
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    // Add the device to the list and update the adapter
                    addDeviceToList(device);
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                // Discovery has finished, inform the user
                Toast.makeText(MainActivity.this, "Device scan finished.", Toast.LENGTH_SHORT).show();
            }
        }
    };

    private void addDeviceToList(BluetoothDevice device) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "BLUETOOTH_CONNECT permission is not granted. Device details cannot be accessed.");
                return;
            }
        }

        deviceList.add(device);
        deviceAdapter.add(device.getName() + "\n" + device.getAddress());
        deviceAdapter.notifyDataSetChanged();
    }

    private void connectToDevice(BluetoothDevice device) {
        // Register the bond state receiver to listen for pairing events
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(bondStateReceiver, filter);

        // Disconnect the currently connected device if there is one
        if (connectedDevice != null && connectedDevice != device) {
            disconnectDevice();
        }

        // Check if we have the required Bluetooth permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                }, REQUEST_BLUETOOTH_PERMISSIONS);
                return;
            }
        }

        // Check if the device is already paired
        if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
            device.createBond();
            Toast.makeText(this, "Pairing with " + device.getName() + ". Please confirm on the other device.", Toast.LENGTH_LONG).show();
            return;
        } else {
            connectToPairedDevice(device);
        }
    }

    private final BroadcastReceiver bondStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                // Check for Bluetooth permissions based on Android version
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        // Handle the missing permission case, possibly request permission
                        return;
                    }
                } else {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                        // Handle the missing permission case, possibly request permission
                        return;
                    }
                }

                if (device != null && device.getBondState() == BluetoothDevice.BOND_BONDED) {
                    // Device is paired, proceed with connection
                    connectToPairedDevice(device);
                }
            }
        }
    };

    private void connectToPairedDevice(BluetoothDevice device) {
        new Thread(() -> {
            try {
                // Check for Bluetooth permissions based on Android version
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        Log.w(TAG, "BLUETOOTH_CONNECT permission is not granted. Cannot connect to device.");
                        return;
                    }
                } else {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                        Log.w(TAG, "BLUETOOTH or BLUETOOTH_ADMIN permission is not granted. Cannot connect to device.");
                        return;
                    }
                }

                // Create and connect the Bluetooth socket
                bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
                bluetoothSocket.connect();
                connectedDevice = device;
                runOnUiThread(() -> {
                    String connectedDeviceName = connectedDevice.getName();
                    connectionStatusTextView.setText("Connected Device: " + connectedDeviceName);
                    alertShown = false;
                    // Save the last connected device's address
                    getSharedPreferences("BluetoothPrefs", MODE_PRIVATE)
                            .edit()
                            .putString(LAST_CONNECTED_DEVICE, connectedDevice.getAddress())
                            .apply();

                    // Reset the connection attempt flag
                    isConnecting = false;
                });

            } catch (IOException e) {
                Log.e(TAG, "Error connecting to device", e);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to connect to device", Toast.LENGTH_SHORT).show());
                disconnectDevice();

                // Reset the connection attempt flag
                isConnecting = false;
            }
        }).start();
    }


    private void checkConnectionStatus() {
        if (connectedDevice == null || bluetoothSocket == null) {
            runOnUiThread(() -> connectionStatusTextView.setText("Connected Device: N/A"));
            reconnectToLastConnectedDevice();
            return;
        }

        // Check for Bluetooth permissions based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // Permission not granted; handle this case
                Log.w(TAG, "BLUETOOTH_CONNECT permission is not granted. Cannot check connection status.");
                return;
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                // Permission not granted; handle this case
                Log.w(TAG, "BLUETOOTH or BLUETOOTH_ADMIN permission is not granted. Cannot check connection status.");
                return;
            }
        }

        try {
            // Send a small data to check if the connection is still active
            bluetoothSocket.getOutputStream().write(0); // Example command
            runOnUiThread(() -> connectionStatusTextView.setText("Connected Device: " + connectedDevice.getName()));
        } catch (IOException e) {
            runOnUiThread(() -> connectionStatusTextView.setText("Connected Device: N/A"));
            Log.e(TAG, "Connection check failed", e);
            disconnectDevice();
        }
    }

    private void disconnectDevice() {
        if (bluetoothSocket != null) {
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing socket", e);
            }
            bluetoothSocket = null;
            connectedDevice = null;

            runOnUiThread(() -> {
                connectionStatusTextView.setText("Connected Device: N/A");
                // Show alert only if it hasn't been shown yet
                if (!alertShown) {
                    AlertManager alertManager = new AlertManager(MainActivity.this);
                    alertManager.showAlert("The Bluetooth device has been disconnected.");
                    alertShown = true; // Set the flag to true to prevent future alerts
                }

                // Check if the last connected device is in the vicinity
                checkLastConnectedDeviceInVicinity();
            });
        }
    }

    private void checkLastConnectedDeviceInVicinity() {
        String lastDeviceAddress = getSharedPreferences("BluetoothPrefs", MODE_PRIVATE)
                .getString(LAST_CONNECTED_DEVICE, null);

        if (lastDeviceAddress != null) {
            BluetoothDevice lastDevice = bluetoothAdapter.getRemoteDevice(lastDeviceAddress);

            // Check for Bluetooth permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    // Request the permission if not granted
                    ActivityCompat.requestPermissions(this, new String[]{
                            Manifest.permission.BLUETOOTH_CONNECT
                    }, REQUEST_BLUETOOTH_PERMISSIONS);
                    return; // Exit the method until permission is granted
                }
            }

            // Check if the device is bonded
            if (lastDevice != null && lastDevice.getBondState() == BluetoothDevice.BOND_BONDED) {
                // Check if a connection attempt is already in progress
                if (!isConnecting) {
                    isConnecting = true; // Set flag to indicate connection attempt

                    // Attempt to connect to the last device
                    connectToPairedDevice(lastDevice);
                }
            }
        }
    }


    private void reconnectToLastConnectedDevice() {
        // Retrieve the last connected device address
        String lastDeviceAddress = getSharedPreferences("BluetoothPrefs", MODE_PRIVATE)
                .getString(LAST_CONNECTED_DEVICE, null);

        if (lastDeviceAddress != null) {
            BluetoothDevice lastDevice = bluetoothAdapter.getRemoteDevice(lastDeviceAddress);

            // Check if we have the required Bluetooth permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    // Handle permission request for Android 12 and above
                    ActivityCompat.requestPermissions(this, new String[]{
                            Manifest.permission.BLUETOOTH_CONNECT
                    }, REQUEST_BLUETOOTH_PERMISSIONS);
                    return;
                }
            } else {
                // For Android 11 and below, check for BLUETOOTH and BLUETOOTH_ADMIN permissions
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED ||
                        ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                    // Handle permission request for Android 11 and below
                    ActivityCompat.requestPermissions(this, new String[]{
                            Manifest.permission.BLUETOOTH,
                            Manifest.permission.BLUETOOTH_ADMIN
                    }, REQUEST_BLUETOOTH_PERMISSIONS);
                    return;
                }
            }

            if (lastDevice != null && lastDevice.getBondState() == BluetoothDevice.BOND_BONDED) {
                connectToPairedDevice(lastDevice);
            }
        }
    }



    @Override
    protected void onResume() {
        super.onResume();
        // Check connection status when activity resumes
        if (connectionCheckHandler != null) {
            connectionCheckHandler.post(connectionCheckRunnable);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Remove callbacks when activity is paused
        if (connectionCheckHandler != null) {
            connectionCheckHandler.removeCallbacks(connectionCheckRunnable);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Register your receiver
        if (!isReceiverRegistered) {
            IntentFilter filter = new IntentFilter(); // Set your intent filter
            registerReceiver(bluetoothReceiver, filter);
            isReceiverRegistered = true;
        }
    }
    @Override
    protected void onStop() {
        super.onStop();
        // Unregister your receiver
        if (isReceiverRegistered) {
            unregisterReceiver(bluetoothReceiver);
            isReceiverRegistered = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Unregister bluetoothReceiver only if it was registered
        if (isReceiverRegistered) {
            unregisterReceiver(bluetoothReceiver);
            isReceiverRegistered = false;
        }

        // Unregister receiver and bondStateReceiver only if they were registered
        try {
            unregisterReceiver(receiver);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Receiver not registered: " + e.getMessage());
        }

        try {
            unregisterReceiver(bondStateReceiver);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Bond state receiver not registered: " + e.getMessage());
        }

        disconnectDevice();

        // Optionally clear the last connected device
        getSharedPreferences("BluetoothPrefs", MODE_PRIVATE)
                .edit()
                .remove(LAST_CONNECTED_DEVICE)
                .apply();

        if (connectionCheckHandler != null) {
            connectionCheckHandler.removeCallbacks(connectionCheckRunnable);
        }

        // Unregister the receiver
        LocalBroadcastManager.getInstance(this).unregisterReceiver(backgroundToggleReceiver);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeBluetooth();
            } else {
                Toast.makeText(this, "Bluetooth permissions are required for this app to function.", Toast.LENGTH_LONG).show();
            }
        }
    }
}
