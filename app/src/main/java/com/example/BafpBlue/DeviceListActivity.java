package com.example.BafpBlue;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;

public class DeviceListActivity extends AppCompatActivity {

    private ArrayList<BluetoothDevice> deviceList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_list);

        // Retrieve the device list passed from MainActivity
        deviceList = getIntent().getParcelableArrayListExtra("deviceList");

        // Display the device list in a ListView
        ListView listView = findViewById(R.id.device_list_view);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);

        // Check Bluetooth permission if Android version >= 12
        for (BluetoothDevice device : deviceList) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    // Request the BLUETOOTH_CONNECT permission for Android 12 and above
                    ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.BLUETOOTH_CONNECT}, 1);
                    return;
                }
            }

            // Add device name and address to the list adapter
            adapter.add(device.getName() + "\n" + device.getAddress());
        }

        listView.setAdapter(adapter);

        // Handle device selection
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                BluetoothDevice selectedDevice = deviceList.get(position);

                // Return the selected device to MainActivity and close this activity
                setResult(Activity.RESULT_OK, getIntent().putExtra("selectedDevice", selectedDevice));
                finish();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, re-create the activity to populate the device list
                recreate();
            } else {
                // Permission denied, close the activity
                finish();
            }
        }
    }
}
