/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.bluetoothlegatt;

import android.Manifest;
import android.app.Activity;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Activity for scanning and displaying available Bluetooth LE devices.
 */
public class DeviceScanActivity extends ListActivity
implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "BLE";
    private LeDeviceListAdapter mLeDeviceListAdapter;
    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private Handler mHandler;

    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private static final int REQUEST_ENABLE_BT = 1;
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 5000;
    private BluetoothLeScanner mBLEScanner;
    private ArrayList<LeScanRecord> mLeDevices;

    public String major_number;
    public String minor_number;
    public String rssi_value;
    public boolean rssi_boolean = false;
    public boolean flag_major = false;
    public boolean flag_minor = false;
    public boolean loop_flag = false;
    int i;
    public String option;

    static final char[] hexArray = "0123456789ABCDEF".toCharArray();

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActionBar().setTitle(R.string.title_devices);
        mHandler = new Handler();

        if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
        }
        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        mBLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
        // Checks if Bluetooth LE Scanner is available.
        if (mBLEScanner == null) {
            Toast.makeText(this, "Can not find BLE Scanner", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        setupSharePreferences();

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "coarse location permission granted");
                } else {
                    // TODO Show message to the user indicating the app will not be able to monitor beacons
                }
                break;
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();

//        getMenuInflater().inflate(R.menu.main, menu);
        inflater.inflate(R.menu.main, menu);

        //menu.findItem(R.id.action_settings).setVisible(true);

        if (!mScanning) {
            menu.findItem(R.id.menu_stop).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(true);
            menu.findItem(R.id.menu_refresh).setActionView(null);
        } else {
            menu.findItem(R.id.menu_stop).setVisible(true);
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(
                    R.layout.actionbar_indeterminate_progress);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_scan) {
            mLeDeviceListAdapter.clear();
            scanLeDevice(true);
            return true;
        }
        if (id == R.id.menu_stop) {
            scanLeDevice(false);
            return true;
        }
        if (id == R.id.action_settings) {
            Intent startSettingActivity = new Intent(this, SettingActivity.class);
            startActivity(startSettingActivity);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }

        // Initializes list view adapter.
        mLeDeviceListAdapter = new LeDeviceListAdapter();
        setListAdapter(mLeDeviceListAdapter);
        scanLeDevice(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onPause() {
        super.onPause();
        scanLeDevice(false);
        mLeDeviceListAdapter.clear();
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        final LeScanRecord device = mLeDeviceListAdapter.getDevice(position);
        if (device == null) return;

/* 170410_dialogfragment_usingonClick
        final Intent intent = new Intent(this, DeviceControlActivity.class);
        intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME, device.device.getName());
        intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, device.device.getAddress());

        if (mScanning) {
           //mBluetoothAdapter.stopLeScan(mLeScanCallback);
            mBLEScanner.startScan(mScanCallback);
            mScanning = false;
        }
        startActivity(intent);
*/

        if (mScanning) {
            mBLEScanner.startScan(mScanCallback);
            mScanning = false;
        }
    }
    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBLEScanner.stopScan(mScanCallback);
                    invalidateOptionsMenu();
                }
            }, SCAN_PERIOD);

            mScanning = true;
            loop_flag = false;
            mBLEScanner.startScan(mScanCallback);
        } else {
            mScanning = false;
            mBLEScanner.stopScan(mScanCallback);
        }
        invalidateOptionsMenu();
    }

    // Adapter for holding devices found through scanning.
    //private class LeDeviceListAdapter extends BaseAdapter {
    private class LeDeviceListAdapter extends ArrayAdapter<LeScanRecord> {

        private LayoutInflater mInflator;

        public LeDeviceListAdapter() {
            super(DeviceScanActivity.this, R.layout.listitem_device);
            mLeDevices = new ArrayList<LeScanRecord>();
            mInflator = DeviceScanActivity.this.getLayoutInflater();
        }

        public void add(LeScanRecord object) {
            if (mLeDeviceListAdapter.getCount() > 0) {
                for (int i = 0; i < mLeDeviceListAdapter.getCount(); i++) {
                    if (mLeDevices.get(i).device.equals(object.device)) {
                        return; // do not add duplicates
                    }
                }
            }
            //super.add(object);
            mLeDevices.add(object);

//Sorting***
            Collections.sort(mLeDevices);
        }

        public LeScanRecord getDevice(int position) {
            return mLeDevices.get(position);
        }

        public void clear() {
            mLeDevices.clear();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Nullable
        @Override
        public LeScanRecord getItem(int position) {
            return super.getItem(position);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }


        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.listitem_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = (TextView) view.findViewById(R.id.device_address);
                viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);

                viewHolder.deviceRssi = (TextView) view.findViewById(R.id.device_rssi);

                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }
            //BluetoothDevice device = mLeDevices.get(i);
            LeScanRecord device = mLeDevices.get(i);
            final String deviceName = device.device.getName();

            if (deviceName != null && deviceName.length() > 0) {
                viewHolder.deviceName.setText(deviceName);
                viewHolder.deviceAddress.setText(device.device.getAddress());
                viewHolder.deviceRssi.setText("UUID : " + device.uuid);
                viewHolder.deviceRssi.append(" Rssi :" + Integer.toString(device.rssi));
                viewHolder.deviceAddress.append("   major :" + device.major_Num + "  minor :  " + device.minor_Num);
            }
            return view;
        }
    }

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            processResult(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) {
                processResult(result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
        }

        private void processResult(final ScanResult result) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    byte[] by;
                    int major = -1;
                    int minor = -1;
                    String uuid = null;
                    by = result.getScanRecord().getBytes();
                    int startByte = 2;
                    boolean patternFound = false;
                    while (startByte <= 5) {
                        if (((int) by[startByte + 2] & 0xff) == 0x02 && //Identifies an iBeacon
                                ((int) by[startByte + 3] & 0xff) == 0x15) { //Identifies correct data length
                            patternFound = true;
                            break;
                        }
                        startByte++;
                    }

                    if (patternFound) {
                        //Convert to hex String
                        byte[] uuidBytes = new byte[16];
                        System.arraycopy(by, startByte+4, uuidBytes, 0, 16);
                        String hexString = bytesToHex(uuidBytes);

                        //Here is your UUID
                        uuid =  hexString.substring(0,8) + "-" +
                                hexString.substring(8,12) + "-" +
                                hexString.substring(12,16) + "-" +
                                hexString.substring(16,20) + "-" +
                                hexString.substring(20,32);

                        major = (by[startByte + 20] & 0xff) * 0x100 + (by[startByte + 21] & 0xff);
                        minor = (by[startByte + 22] & 0xff) * 0x100 + (by[startByte + 23] & 0xff);
                        //Log.i(TAG,"major :" + major + "minor : " +minor);
                    }
//170411
/*
                    if (major == Integer.parseInt(major_number) && minor == Integer.parseInt(minor_number)) {
                        flag = true;
                        mLeDeviceListAdapter.add(new LeScanRecord(result.getDevice(),result.getRssi(),uuid, major, minor));
                        result.getScanRecord().getServiceData();
                        mLeDeviceListAdapter.notifyDataSetChanged();
                    } //else {
                    if (major_number.equals("60000")) {
                        flag = false;
                        mLeDeviceListAdapter.add(new LeScanRecord(result.getDevice(), result.getRssi(),uuid, major, minor));
                        result.getScanRecord().getServiceData();
                        mLeDeviceListAdapter.notifyDataSetChanged();
                    }
*/
                    mLeDeviceListAdapter.add(new LeScanRecord(result.getDevice(), result.getRssi(),uuid, major, minor));
                    result.getScanRecord().getServiceData();
                    mLeDeviceListAdapter.notifyDataSetChanged();

                    int count = mLeDeviceListAdapter.getCount();

                    if(!loop_flag && rssi_boolean){
                        for(i = 0;i<count;i++){
                            if(mLeDevices.get(i).major_Num == Integer.parseInt(major_number)
                                    && mLeDevices.get(i).minor_Num == Integer.parseInt(minor_number)) {
                                loop_flag = true;
                                DialogSimple(mLeDevices.get(i).major_Num , mLeDevices.get(i).minor_Num, mLeDevices.get(i).rssi);
                                break;
                            }
                        }
                    }
/*
                    if(loop_flag && rssi_boolean){
                        if(mLeDevices.get(i).major_Num == Integer.parseInt(major_number)
                                && mLeDevices.get(i).minor_Num == Integer.parseInt(minor_number)) {
                            Log.i(TAG, "######" + mLeDevices.get(i).major_Num  + "" + Integer.parseInt(major_number) +
                                    " && " +  mLeDevices.get(i).minor_Num + "" +  Integer.parseInt(minor_number));
                            rssi_boolean = false;
                            DialogSimple(mLeDevices.get(i).rssi);

                        }

                    }
*/



                }
            });
        }
    };

    private class LeScanRecord
            implements Comparable<LeScanRecord> {
        public final BluetoothDevice device;
        public final int rssi;
        public final String uuid;
        public final int major_Num;
        public final int minor_Num;

        public LeScanRecord(BluetoothDevice device, int rssi, String uuid, int major_Num, int minor_Num) {
            this.device = device;
            this.rssi = rssi;
            this.major_Num = major_Num;
            this.minor_Num = minor_Num;
            this.uuid = uuid;
        }
        @Override
        public int compareTo(@NonNull LeScanRecord o) {
            if(option.equals("1")){
                return this.major_Num - ((LeScanRecord) o).major_Num;
            }else if (option.equals("2")){
                return this.minor_Num - ((LeScanRecord) o).minor_Num;
            } else {
                return ((LeScanRecord) o).rssi - this.rssi;
            }
        }
    }

    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
        TextView deviceRssi;
    }

    private void setupSharePreferences() {
        SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(this);

        major_number = sharedPreferences.getString(getString(R.string.pref_show_major_key), getString(R.string.pref_major_default));
//        Log.i(TAG, "Major 11 : " + major_number);
        minor_number = sharedPreferences.getString(getString(R.string.pref_show_minor_key), getString(R.string.pref_minor_default));
//        Log.i(TAG, "minor 11 : " + minor_number);
        rssi_value = sharedPreferences.getString(getString(R.string.pref_rssi_key), getString(R.string.pref_rssi_default));
//        Log.i(TAG, "rssi 11 : " + rssi_value);
        rssi_boolean = sharedPreferences.getBoolean(getString(R.string.pref_popup_key), getResources().getBoolean((R.bool.pref_rssi_default)));
     // Log.i(TAG, "rssi_boolean  : " + rssi_boolean + "  shard : " + sharedPreferences.getBoolean(getString(R.string.pref_rssi_check_key), getResources().getBoolean((R.bool.pref_rssi_default))));


        sortingOptionFromPreferences(sharedPreferences);

        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    private void sortingOptionFromPreferences(SharedPreferences sharedPreferences){
        option = sharedPreferences.getString(getString(R.string.pref_check_key), getString(R.string.pref_rssi_check_value));
        Log.i(TAG,"option selected "+ option);
    }
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getString(R.string.pref_show_major_key))) {
            major_number = sharedPreferences.getString(key, getResources().getString(R.string.pref_major_default));
            //Log.i(TAG, " Test2 : " + major_number);
        }
        else if (key.equals(getString(R.string.pref_popup_key))) {
            rssi_boolean = sharedPreferences.getBoolean(key, getResources().getBoolean((R.bool.pref_rssi_default)));
            //Log.i(TAG, " rssi_booleannnnnnn: " + rssi_boolean + "  shard : " + sharedPreferences.getBoolean(getString(R.string.pref_rssi_check_key), getResources().getBoolean((R.bool.pref_rssi_default))));
        }
        else if (key.equals(getString(R.string.pref_show_minor_key))) {
            minor_number = sharedPreferences.getString(key, getResources().getString((R.string.pref_minor_default)));
        }
        else if (key.equals(getString(R.string.pref_rssi_key))) {
            rssi_value = sharedPreferences.getString(key, getResources().getString((R.string.pref_rssi_default)));
        }
        else if(key.equals(getString(R.string.pref_check_key))){
            sortingOptionFromPreferences(sharedPreferences);
        }
    }
    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);

    }

    private void DialogSimple(int Major,int Minor,int rssi){


        //Log.i(TAG,"Dialog fuction " +         mLeDevices.get(0).device);


        AlertDialog.Builder alt_bld = new AlertDialog.Builder(this);
        alt_bld.setMessage(" Major : " +  Major +" Minor : " + Minor +"\n" + " Rssi : " + rssi).setCancelable(
                false).setPositiveButton("Yes",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // Action for 'Yes' Button
                    }
                }).setNegativeButton("No",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // Action for 'NO' Button
                        dialog.cancel();
                    }
                });
        AlertDialog alert = alt_bld.create();
        // Title for AlertDialog
        alert.setTitle("Title");
        // Icon for AlertDialog
        alert.setIcon(R.drawable.ic_launcher);
        alert.show();
    }
}