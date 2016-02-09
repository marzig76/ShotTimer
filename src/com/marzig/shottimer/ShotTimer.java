package com.marzig.shottimer;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Date;
import java.util.Random;

import android.app.Activity;
import android.content.Context;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.Vibrator;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class ShotTimer extends Activity {
	Chronometer mChronometer;
	LinearLayout layout;
	TextView tv;
	String shotDisplay = "";
	Boolean allowShot = false;
	MediaPlayer mp;
	AudioRecord mRecord;
	
	boolean displayAll = false;
	boolean logging = false;
	
	// roughly, normal room db is about 85 and a shot is about 122..
	// i am doubling the db i receive from the shot and comparing it to this threshold, 
	// that's why its set to 244.  Everywhere i record db levels, I multiply it by 2..
	// if you want to change it back, put the DEFAULT_THRESHOLD to 122 and the threshold to 110, 
	// then find each instance of * 2 and remove it so it's not multiplying the db level by 2 anymore
	private static final double DEFAULT_THRESHOLD = 244.00;
	private static final int threshInt = 1;
	double threshold = DEFAULT_THRESHOLD;	//set to something lower when testing with a clap (like 220.00)
	
	private FileWriter shotLog = null;
	
	private static String LOGPATH = "ShotTimer/";
	private static String configFile = "threshold.txt";

	double lastTime = 0.00;
	double lastShot = 0.00;
	
	boolean isCalibrating = false;
	int calibrateCount = 0;
	double maxCalThresh = 0.00;
	
	boolean mode = false;
	boolean calib = false;
	boolean log = false;
	boolean max = false;
	
	boolean delaySleep = false;
	int delaySleepTime = 0;

	static final int MY_MSG = 1;

	protected AudioEngine myEngine;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    
	    //construct the layout / set orientation
	    layout = new LinearLayout(this);
	    layout.setOrientation(LinearLayout.VERTICAL);
	    
	    //create dir if needed
	    //File sddir = new File("/sdcard/"+LOGPATH);
	    //sddir.mkdirs();
	    
	    //display banner
	    //TextView banner = new TextView(this);
	    //banner.setText("Shot Clock");
	    //layout.addView(banner);
	    
	    
	    //load the user-saved threshold
	    loadThresh();
	    
	    //create text view to display shot times
	    //format the text and enable scrolling
	    tv = new TextView(this);
	    tv.setGravity(Gravity.CENTER_HORIZONTAL);
	    tv.setMovementMethod(new ScrollingMovementMethod());

	    //construct the chronometer
	    mChronometer = new Chronometer(this);
	    
	    // right now i'm hiding the chronometer from view.
	    //layout.addView(mChronometer);

	    //define the buttons and the handlers that will listen the their button pushes.
	    Button startButton = new Button(this);
	    startButton.setText("Start");
	    startButton.setOnClickListener(mStartListener);
	    //startButton.getBackground().setColorFilter(0xFF0099CC, Mode.MULTIPLY);
	    layout.addView(startButton);

	    Button resetButton = new Button(this);
	    resetButton.setText("New Shooter");
	    resetButton.setOnClickListener(mResetListener);
	    layout.addView(resetButton);

	    //for testing with the android emulator,
	    //enable this button to simulate a shot being fired
	    /*
	    Button shotButton = new Button(this);
	    shotButton.setText("Simulate Shot");
	    shotButton.setOnClickListener(mShotListener);
	    layout.addView(shotButton); 
	    */

	    //display it all!
	    setContentView(layout);
	}

	//set up the listeners
	View.OnClickListener mStartListener = new OnClickListener() {
		public void onClick(View v) {
			// to avoid microphone conflict, we cannot be in calibration mode and allowShot mode at the same time.
			// since we are entering allowShot mode, we need to cleanup if we are in calibration mode.
			if (isCalibrating)
				cleanUp();
				
			if (!allowShot){
				//open log file
				if (logging){
					try{
						Date now = new Date();
						shotLog = new FileWriter(LOGPATH + now.getYear()+now.getMonth()+now.getDate()+ "txt", true);
					}catch (Exception e){}
				}
				
				//sleep for a random amount of time if needed
				if (delaySleep){
					try {
						Thread.sleep(delaySleepTime);
					}catch (Exception e) {}
				}
				
				//create and play sound file
			    mp = MediaPlayer.create(getApplicationContext(), R.raw.buzzer1);
				mp.start();

			    //initialize and start the audio engine
		        start_meter();
		
				//allow shot time to be recorded
				allowShot = true;

				int stoppedMilliseconds = 0;

				String chronoText = mChronometer.getText().toString();
				String array[] = chronoText.split(":");
				if (array.length == 2) {
					stoppedMilliseconds = Integer.parseInt(array[0]) * 60 * 1000
					+ Integer.parseInt(array[1]) * 1000;
				} else if (array.length == 3) {
					stoppedMilliseconds = Integer.parseInt(array[0]) * 60 * 60 * 1000 
					+ Integer.parseInt(array[1]) * 60 * 1000
					+ Integer.parseInt(array[2]) * 1000;
				}

				mChronometer.setBase(SystemClock.elapsedRealtime() - stoppedMilliseconds + 300);
				mChronometer.start();
				
				// Vibrate to alert user
				Vibrator goodVibration = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
				goodVibration.vibrate(300);
			}
	    }
	};
	
	View.OnClickListener mResetListener = new OnClickListener() {
	    public void onClick(View v) {
	    	cleanUp();
	    }
	};
	
	View.OnClickListener mShotListener = new OnClickListener() {
	    public void onClick(View v) {
	    	shootIt();
	    }
	};
	
	//sound handler
    public Handler mhandle = new Handler(){
    	
    	@Override
    	public void handleMessage(Message msg){
    		
    		switch (msg.what){
    		
    		case MY_MSG:
    			String str = msg.obj.toString();
    			double d = Double.valueOf(str).doubleValue();
    			
    			// this is the handler that receives shot db values.

    			// if we are in calibration mode, we want to record a few sample db values
    			// (over 95db only, to avoid higher than usual ambient noise)
    			// then, we'll set the threshold at the maximum db (minus .5) of those sample readings
    			if (isCalibrating && (d > 95)){
    				if ((d * 2) > maxCalThresh)
    					maxCalThresh = (d * 2);
    				
    				if (calibrateCount == 6){
    					stop_meter();
    					saveThresh(maxCalThresh - .5);
    					isCalibrating = false;
    					calibrateCount = 0;
    					maxCalThresh = 0.00;
    				}
    				calibrateCount++;
    			}
    			// if we're not calibrating, we just call the shootIt() function to record the shot
    			else if ((d * 2) > threshold){
    					shootIt();
    			}
    			break;
    		default:
    			super.handleMessage(msg);
    			break;
    		}
    	}
    };

    public void start_meter(){
    	calib = false;
		max = false;
		log = false;
		mode = false;

		myEngine = new AudioEngine(mhandle);
		myEngine.start_engine();
	}

    public void stop_meter(){
    	myEngine.stop_engine();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
        	case R.id.increase_threshold:
        		threshold += threshInt;        			
        		Toast.makeText(getBaseContext(), "Threshold: " + (int) (threshold - DEFAULT_THRESHOLD),
        				Toast.LENGTH_SHORT).show();
        		return true;
        
        	case R.id.default_threshold:
        		threshold = DEFAULT_THRESHOLD;
        		Toast.makeText(getBaseContext(), "Threshold: " + (int) (threshold - DEFAULT_THRESHOLD) + " (Default)",
        				Toast.LENGTH_SHORT).show();
        		return true;    
        
        	case R.id.decrease_threshold:
        		threshold -= threshInt;
        		Toast.makeText(getBaseContext(), "Threshold: " + (int) (threshold - DEFAULT_THRESHOLD),
        				Toast.LENGTH_SHORT).show();
        		return true;
        
        	case R.id.show_all:
        		displayAll = !displayAll;
        		Toast.makeText(getBaseContext(), "Now displaying " + (displayAll ? "all shots" : "only your latest shot"),
        				Toast.LENGTH_SHORT).show();
        		return true;
        
        	case R.id.toggle_delay:
        		Random randGen = new Random(); 
                int randNum = randGen.nextInt(4);
                delaySleep = !delaySleep;
        		delaySleepTime = (randNum * 1000) + 2000;
        		Toast.makeText(getBaseContext(), "There will " + (!delaySleep ? "not " : "") + "be a delayed start",
        				Toast.LENGTH_SHORT).show();
        		return true;
        		
        	case R.id.calibrate:
        		calibrate();
        		return true;
        
        	case R.id.save_threshold:
        		saveThresh(threshold);
        		return true;
        
        	case R.id.load_threshold:
        		loadThresh();
        		return true;

        	/*
        	case R.id.logging:
        		logging = !logging;
        		Toast.makeText(getBaseContext(),
        				"Data is " + (logging ? "now" : "no longer") + " being logged" + (logging ? " to " + LOGPATH : ""),
        				Toast.LENGTH_SHORT).show();
        		return true;
        	 */

        	default:
        		return super.onOptionsItemSelected(item);
        }
    }

    //simply override the menu create function and create my damn menus!
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    //called when we want to record a shot
    public void shootIt(){
    	if (allowShot){
    		//display the time
    		layout.removeView(tv);

    		long base = SystemClock.elapsedRealtime() - mChronometer.getBase();
    		float secs = (float) base / 1000;

    		// we want to avoid 1 shot being counted multiple times
    		// we won't listen to anything that is within .055 seconds of the previously recorded sound
    		// or anything with .110 seconds of the previous shot
    		if ((secs > (lastTime + 0.055)) && (secs > (lastShot + 0.110))){
    			if (displayAll)
    				shotDisplay = "<br /><h1>" + secs + "</h1>" + shotDisplay.replace("<h1>", "").replace("</h1>", "");
    			else shotDisplay = "<br /><h1>" + secs + "</h1>";
    			
    			if (logging)
    				logValue(secs);

    			lastShot = secs;
    		}
    		
			tv.setText( Html.fromHtml(shotDisplay) );
			layout.addView(tv);
			
			lastTime = secs;
    	}
    }
    
    //when the home or back button is pushed, cleanup and finish
    //home button
    public void onUserLeaveHint(){
    	cleanUp();
    	finish();
    }
    
    //back button
    @Override
    public void onBackPressed() {
    	cleanUp();
    	finish();
    }
    
    //stops audio engines, resets sound and log files, resets default values and counters, etc..
    public void cleanUp(){
    	// if we are in calibration mode, just reset the audio engine
    	if (isCalibrating){
    		isCalibrating = false;
    		stop_meter();
    	}
    	
    	// if we are in allowShot mode, there is a lot more to clean up
    	if (allowShot){
    		allowShot = false;
    		
    		//reset last shot and time
    		lastTime = 0.00;
    		lastShot = 0.00;
    		
    		//close log file
    		try {
    			shotLog.close();
    		}catch (Exception e){}
    		
    		//stop the audio engine
    		stop_meter();
    	
    		//stop and reset the sound file
    		mp.stop();
    		mp.reset();

    		//stop time, clear display
    		mChronometer.stop();
    		mChronometer.setBase(SystemClock.elapsedRealtime());
    		layout.removeView(tv);
    		shotDisplay = "";
    		tv.setText(shotDisplay);
    		layout.addView(tv);
    	}
    }
    
    public void calibrate(){
    	// to avoid audio conflict, we cannot be in calibration mode and allowShot mode at the same time
		// since we are entering calibration mode, we need to cleanup if we are in allowShot mode.
    	if (allowShot)
    		cleanUp();
    		
    	isCalibrating = true;
    	start_meter();	//start recording sound
    	Toast.makeText(getBaseContext(), "Fire A Shot!", Toast.LENGTH_SHORT).show();
    }
    
    //simply saving the current threshold to the phone's file system and displaying a message
    public void saveThresh(double thresh){
    	try{
    		FileOutputStream fOut = null;
    		OutputStreamWriter osw = null;

    		File root = getFilesDir();
        	
    		if(root.canWrite()){
    			fOut = openFileOutput(configFile, MODE_PRIVATE);
    			osw = new OutputStreamWriter(fOut);
    			osw.write(Double.toString(thresh));
    			osw.flush();
    			osw.close();
    			fOut.close();
    			threshold = thresh;
    		    Toast.makeText(getBaseContext(), "Threshold saved", Toast.LENGTH_SHORT).show();    		    
    		    Toast.makeText(getBaseContext(), "Threshold: " + (int) (threshold - DEFAULT_THRESHOLD),
                		Toast.LENGTH_SHORT).show();
    		}else {
    			Toast.makeText(getBaseContext(), "Cannot Save Threshold", Toast.LENGTH_SHORT).show();
    		}
    		  }catch( Exception e ){
    			  Toast.makeText(getBaseContext(), "Cannot Save Threshold", Toast.LENGTH_SHORT).show();
    		  }

    }
    
    //simply loading the current threshold from the phone's file system and displaying a message
    public void loadThresh(){
    	FileInputStream fIn = null;
    	InputStreamReader isr = null;
    	char[] inputBuffer = new char[255];
    	String data = null;
    	try{
    		fIn = openFileInput(configFile);
    		isr = new InputStreamReader(fIn);
    		isr.read(inputBuffer);
    		data = new String(inputBuffer);
    		threshold = Double.parseDouble(data);
    		Toast.makeText(getBaseContext(), "Threshold Loaded", Toast.LENGTH_SHORT).show();
    		Toast.makeText(getBaseContext(), "Threshold: " + (int) (threshold - DEFAULT_THRESHOLD),
        			Toast.LENGTH_SHORT).show();
    		isr.close();
    		fIn.close();
    	}catch (Exception e) {
    		Toast.makeText(getBaseContext(), "No Saved Threshold. Please Calibrate.", Toast.LENGTH_SHORT).show();
    	}
    }
    
    public void logValue(float value){
    	try{
    		shotLog.append(value + "\n");
    		shotLog.close();
        }catch (Exception e){}
    }
}


