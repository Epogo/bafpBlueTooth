package com.example.BafpBlue;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
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
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String LAST_CONNECTED_DEVICE = "lastConnectedDevice";
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;
    private static final int CHECK_PERIOD = 15000; // Check every 15 seconds

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice lastConnectedDevice;
    private boolean deviceInVicinity = false;
    private Handler checkVicinityHandler = new Handler();
    private Runnable checkVicinityRunnable;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Check if Bluetooth is supported
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth is not supported on this device.");
            finish();
            return;
        }

        // Request Bluetooth permissions
        requestBluetoothPermissions();

        // Get the last connected device's address from shared preferences
        String lastDeviceAddress = getSharedPreferences("BluetoothPrefs", MODE_PRIVATE)
                .getString(LAST_CONNECTED_DEVICE, null);

        if (lastDeviceAddress != null) {
            lastConnectedDevice = bluetoothAdapter.getRemoteDevice(lastDeviceAddress);
            startCheckingDeviceVicinity();
        }

        // Set up the rescan button
        Button scanButton = findViewById(R.id.scan_button);
        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startDeviceDiscovery(); // Start scanning for devices
            }
        });
    }

    private void requestBluetoothPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
            }, REQUEST_BLUETOOTH_PERMISSIONS);
        }
    }

    private void startDeviceDiscovery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.BLUETOOTH_SCAN
                }, REQUEST_BLUETOOTH_PERMISSIONS);
                return;
            }
        } else {
            // For Android versions below 12, check for Bluetooth permissions
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.BLUETOOTH
                }, REQUEST_BLUETOOTH_PERMISSIONS);
                return;
            }
        }

        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery(); // Stop any ongoing discovery
        }

        // Start Bluetooth discovery
        bluetoothAdapter.startDiscovery();
        registerReceiver(discoveryReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
        registerReceiver(discoveryReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));
    }


    private BroadcastReceiver discoveryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                // Check for Bluetooth_CONNECT permission based on API level
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        // Request permission if not granted
                        ActivityCompat.requestPermissions(MainActivity.this, new String[]{
                                Manifest.permission.BLUETOOTH_CONNECT
                        }, REQUEST_BLUETOOTH_PERMISSIONS);
                        return;
                    }
                } else {
                    // For Android versions below 12, check for Bluetooth permission
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(MainActivity.this, new String[]{
                                Manifest.permission.BLUETOOTH
                        }, REQUEST_BLUETOOTH_PERMISSIONS);
                        return;
                    }
                }

                Log.d(TAG, "Found device: " + device.getName() + " - " + device.getAddress());
                // Here, you can add code to display found devices to the user for selection
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Log.d(TAG, "Discovery finished");
            }
        }
    };


    private void startCheckingDeviceVicinity() {
        checkVicinityRunnable = new Runnable() {
            @Override
            public void run() {
                checkPairedDeviceVicinity();
                checkVicinityHandler.postDelayed(this, CHECK_PERIOD);
            }
        };
        checkVicinityHandler.post(checkVicinityRunnable);
    }

    private void checkPairedDeviceVicinity() {
        if (lastConnectedDevice == null) {
            Log.w(TAG, "No previously connected device found");
            return;
        }

        // Check for Bluetooth_SCAN permission based on API level
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                // Request permission if not granted
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{
                        Manifest.permission.BLUETOOTH_SCAN
                }, REQUEST_BLUETOOTH_PERMISSIONS);
                return;
            }
        } else {
            // For Android versions below 12, check for Bluetooth permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{
                        Manifest.permission.BLUETOOTH
                }, REQUEST_BLUETOOTH_PERMISSIONS);
                return;
            }
        }

        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }

        bluetoothAdapter.startDiscovery();
        deviceInVicinity = false; // Reset for this check

        // Register receiver to check if the device is found
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        BroadcastReceiver discoveryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null && device.getAddress().equals(lastConnectedDevice.getAddress())) {
                        deviceInVicinity = true;
                        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            // Request permission if not granted
                            ActivityCompat.requestPermissions(MainActivity.this, new String[]{
                                    Manifest.permission.BLUETOOTH_CONNECT
                            }, REQUEST_BLUETOOTH_PERMISSIONS);
                            return;
                        }
                        Log.d(TAG, "Device is in vicinity: " + device.getName());
                    }
                }
            }
        };

        registerReceiver(discoveryReceiver, filter);

        // Check if the device was in the vicinity after a delay
        Handler handler = new Handler();
        handler.postDelayed(() -> {
            if (!deviceInVicinity) {
                triggerDeviceDisappearedNotification();
                Log.d(TAG, "Device has disappeared from vicinity.");
            }
            // Unregister the receiver to avoid memory leaks
            unregisterReceiver(discoveryReceiver);
        }, 15000); // Delay to allow time for discovery
    }




    private void triggerDeviceDisappearedNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Create notification channel for Android 8.0+ (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("DEVICE_DISAPPEARED_CHANNEL",
                    "Device Disappeared",
                    NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        // Check for Bluetooth_CONNECT permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // Request permission if not granted
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{
                        Manifest.permission.BLUETOOTH_CONNECT
                }, REQUEST_BLUETOOTH_PERMISSIONS);
                return;
            }
        } else {
            // For Android versions below 12, check for Bluetooth permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{
                        Manifest.permission.BLUETOOTH
                }, REQUEST_BLUETOOTH_PERMISSIONS);
                return;
            }
        }

        // Build and display the notification
        Notification notification = new NotificationCompat.Builder(this, "DEVICE_DISAPPEARED_CHANNEL")
                .setContentTitle("Bluetooth Device Disconnected")
                .setContentText("The Bluetooth device " + lastConnectedDevice.getName() + " is no longer in range.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build();

        notificationManager.notify(1001, notification);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(discoveryReceiver); // Clean up the receiver
        checkVicinityHandler.removeCallbacks(checkVicinityRunnable); // Remove the handler
    }
}
