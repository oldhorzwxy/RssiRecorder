package cn.navior.tool.rssirec;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import android.os.Bundle;
import android.os.Environment;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

/**
 * Android activity, searching for all nearby Bluetooth devices.
 * @author wangxiayang
 *
 */
public class SearchingActivity extends Activity {
	
	/* constants defined by wangxiayang */
	private static final int REQUEST_ENABLE_BT = 36287;
	
	/* private fields */
	private int searchId = 0;
	private HashMap< String, RecordItem > tempRecords = new HashMap< String, RecordItem >();	// temporary record storage, available between two searches
	private boolean continueSearching = true;	// mark, showing whether the searching thread should continue running
	private BluetoothAdapter mBluetoothAdapter;
	private Activity thisActivity = this;	// reserve a reference to this activity to let the QUIT button work
	//private Thread searchingThread;	// searching thread
	private PrintWriter searchRecordWriter;
	private PrintWriter searchInfoWriter;
	private ArrayAdapter< String > devicesArrayAdapter;
	private TextView searchingStatus;
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
	    public void onReceive(Context context, Intent intent) {
	        String action = intent.getAction();
	        
	        // When discovery finds a device
	        if (BluetoothDevice.ACTION_FOUND.equals(action)) {
	            // Get the BluetoothDevice object from the Intent
	            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
	            short rssi = intent.getShortExtra( BluetoothDevice.EXTRA_RSSI, (short)0 );
	            
	            // Add the name and address to an array adapter to show in a ListView
	            devicesArrayAdapter.add(device.getName() + "  " + device.getAddress() + "  " + rssi );
	            devicesArrayAdapter.notifyDataSetChanged();
	            
	            // add the record into the hashmap
	            // update it if a device is discovered more than once in one discovery
	            RecordItem item = new RecordItem( device.getAddress() );
            	item.setRssi( rssi + 0 );	// replace the short value into an integer
            	item.setSearchId( searchId );
            	item.setName( device.getName() );
            	SimpleDateFormat tempDate = new SimpleDateFormat( "kk-mm-ss-SS", Locale.ENGLISH );
        		String datetime = tempDate.format(new java.util.Date());
            	item.setDatetime( datetime );
            	tempRecords.put( device.getAddress(), item );
	        }
	        // When the discovery ends
	        else if( BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals( action ) ){
	        	// write record
	        	saveRecords();
	        	// write stop info
	        	SimpleDateFormat tempDate = new SimpleDateFormat( "kk-mm-ss-SS", Locale.ENGLISH );
        		String datetime = tempDate.format(new java.util.Date());
        		searchInfoWriter.write( "stop," + searchId + "," + datetime + "\n" );
	        	// whether to continue
	        	if( continueSearching ){
	        		mBluetoothAdapter.startDiscovery();
	        	}
	        	else{
		        	searchingStatus.setText( "Searching has finished." );
	        	}
	        }
	        // When the discovery starts
	        else if( BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals( action ) ){
	        	devicesArrayAdapter.clear();	// clear the list
				devicesArrayAdapter.notifyDataSetChanged();
	        	searchId++;
	        	searchingStatus.setText( "Searching has been on for " + searchId + "." );
	        	// write start info
	        	SimpleDateFormat tempDate = new SimpleDateFormat( "kk-mm-ss-SS", Locale.ENGLISH );
        		String datetime = tempDate.format(new java.util.Date());
	        	searchInfoWriter.write( "start," + searchId + "," + datetime + "\n" );
	        }
	    }
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_searching);
		
		/* initialize the fields */
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();	// get the Bluetooth adapter for this device
		devicesArrayAdapter = new ArrayAdapter< String >( this, android.R.layout.simple_expandable_list_item_1 );
		searchingStatus = ( TextView )findViewById( R.id.searching_status );
		
		/* bind the action listeners */
		final Button stopButton = ( Button )findViewById( R.id.searching_stop );
		final Button startButton = ( Button ) findViewById( R.id.searching_start );
		final Button quitButton = ( Button )findViewById( R.id.searching_quit );
		// stop-searching button
		stopButton.setOnClickListener( new OnClickListener(){
			public void onClick( View v ){
				continueSearching = false;
				mBluetoothAdapter.cancelDiscovery();	// manually stop searching
				// write record
	        	saveRecords();
	        	// write stop info
	        	SimpleDateFormat tempDate = new SimpleDateFormat( "kk-mm-ss-SS", Locale.ENGLISH );
        		String datetime = tempDate.format(new java.util.Date());
        		searchInfoWriter.write( "stop," + searchId + "," + datetime + "\n" );
				searchRecordWriter.close();
				searchInfoWriter.close();
				stopButton.setEnabled( false );
				startButton.setEnabled( true );
			}
		});
		stopButton.setEnabled( false );	// disable the stop button until discovery starts
		// start-searching button
		startButton.setOnClickListener( new OnClickListener(){
			public void onClick( View v ){
				createPrintWriter();
				continueSearching = true;
				mBluetoothAdapter.startDiscovery();
				stopButton.setEnabled( true );
				startButton.setEnabled( false );
			}

			private void createPrintWriter() {
				// create directory
				File directory = new File( Environment.getExternalStoragePublicDirectory( Environment.DIRECTORY_DOWNLOADS ), "rssirec" );
                if( !directory.exists() ) {
                	directory.mkdir();
                }
                
                // create the time string
                SimpleDateFormat tempDate = new SimpleDateFormat( "yyyy-MM-dd-kk-mm-ss", Locale.ENGLISH );
        		String datetime = tempDate.format(new java.util.Date());
                
        		// create the file
                File recordFile = new File( directory.getAbsolutePath() + "/" + datetime + ".txt" );
                if( recordFile.exists() ) {
                	recordFile.delete();
                }
                File infoFile = new File( directory.getAbsolutePath() + "/info" + datetime + ".txt" );
                if( infoFile.exists() ) {
                	infoFile.delete();
                }
                
                // open writer
                try {
                	searchRecordWriter = new PrintWriter( recordFile );
                	searchRecordWriter.write( "Device name," + mBluetoothAdapter.getName() + "\n" );
                	searchRecordWriter.write( "Start time," + datetime + "\n" );
                	searchRecordWriter.write( "name,mac,rssi,time\n" );
                	searchInfoWriter = new PrintWriter( infoFile );
                	searchInfoWriter.write( "Device name," + mBluetoothAdapter.getName() + "\n" );
                	searchInfoWriter.write( "Start time," + datetime + "\n" );
                	searchInfoWriter.write( "status,searchId,time\n" );
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				}
			}
		});
		// quit-searching button
		quitButton.setOnClickListener( new OnClickListener(){
			public void onClick( View v ){
				thisActivity.finish();
			}
		});
		
		/* set list adapter */
		ListView lv = ( ListView )findViewById( R.id.searching_device_list );
		lv.setAdapter( devicesArrayAdapter );
		
		/* register the broadcast receiver */
		IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		registerReceiver(mReceiver, filter);
		filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		registerReceiver(mReceiver, filter);
		filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
		registerReceiver(mReceiver, filter);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		/* check Bluetooth status, notify user to turn it on if it's not */
		// request for Bluetooth if it's not on.
		// repeat requesting if user refused to open Bluetooth
		while (!mBluetoothAdapter.isEnabled()) {
		    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
		    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		}
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		// unregister the receiver
		unregisterReceiver( mReceiver );
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.searching, menu);
		return true;
	}
	
	/**
	 * Not handle NULLPOINTER exception for that writer is null
	 */
	private void saveRecords() {
		ArrayList< RecordItem > list = new ArrayList< RecordItem >( tempRecords.values() );
		for( int i = 0; i < list.size(); i++ ) {
			RecordItem item = list.get( i );
			searchRecordWriter.write( item.getSearchId() + "," + item.getName() + "," + item.getMac() + "," + item.getRssi() + "," + item.getDatetime() + "\n" );
		}
		tempRecords.clear();
	}
}
