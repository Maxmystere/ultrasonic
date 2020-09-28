/*
 This file is part of Subsonic.

 Subsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Subsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Subsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2010 (C) Sindre Mehus
 */
package org.moire.ultrasonic.receiver;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.moire.ultrasonic.util.Constants;
import org.moire.ultrasonic.util.Util;

/**
 * Request media button focus when connected to Bluetooth A2DP.
 *
 * @author Sindre Mehus
 */
public class BluetoothIntentReceiver extends BroadcastReceiver
{
	private static final String TAG = BluetoothIntentReceiver.class.getSimpleName();

	@Override
	public void onReceive(Context context, Intent intent)
	{
		int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
		BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
		String action = intent.getAction();
		String name = device != null ? device.getName() : "Unknown";
		String address = device != null ? device.getAddress() : "Unknown";

		Log.d(TAG, String.format("A2DP State: %d; Action: %s; Device: %s; Address: %s", state, action, name, address));

		boolean actionBluetoothDeviceConnected = false;
		boolean actionBluetoothDeviceDisconnected = false;
		boolean actionA2dpConnected = false;
		boolean actionA2dpDisconnected = false;

		if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action))
		{
			actionBluetoothDeviceConnected = true;
		}
		else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action) || BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED.equals(action))
		{
			actionBluetoothDeviceDisconnected = true;
		}

		if (state == android.bluetooth.BluetoothA2dp.STATE_CONNECTED) actionA2dpConnected = true;
		else if (state == android.bluetooth.BluetoothA2dp.STATE_DISCONNECTED) actionA2dpDisconnected = true;

		boolean connected = actionA2dpConnected || actionBluetoothDeviceConnected;
		boolean resume = false;
		boolean pause = false;

		switch (Util.getResumeOnBluetoothDevice(context))
		{
			case Constants.PREFERENCE_VALUE_ALL: resume = actionA2dpConnected || actionBluetoothDeviceConnected;
				break;
			case Constants.PREFERENCE_VALUE_A2DP: resume = actionA2dpConnected;
				break;
		}

		switch (Util.getPauseOnBluetoothDevice(context))
		{
			case Constants.PREFERENCE_VALUE_ALL: pause = actionA2dpDisconnected || actionBluetoothDeviceDisconnected;
				break;
			case Constants.PREFERENCE_VALUE_A2DP: pause = actionA2dpDisconnected;
				break;
		}

		if (connected)
		{
			Log.i(TAG, String.format("Connected to Bluetooth device %s address %s, requesting media button focus.", name, address));
			Util.registerMediaButtonEventReceiver(context, false);
		}

		if (resume)
		{
			Log.i(TAG, String.format("Connected to Bluetooth device %s address %s, resuming playback.", name, address));
			context.sendBroadcast(new Intent(Constants.CMD_PLAY));
		}

		if (pause)
		{
			Log.i(TAG, String.format("Disconnected from Bluetooth device %s address %s, requesting pause.", name, address));
			context.sendBroadcast(new Intent(Constants.CMD_PAUSE));
		}
	}
}
