package com.marzig.shottimer;

import java.math.BigDecimal;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Message;


public class AudioEngine extends Thread{

	private static final int FREQUENCY = 8000;
	private static final int CHANNEL = AudioFormat.CHANNEL_CONFIGURATION_MONO;
	private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
	private static final int MY_MSG = 1;
	protected static final int MAXOVER_MSG = 2;
	private int BUFFSIZE = 320;
	private static final double P0 = 0.000002;
	public volatile boolean isRunning = false;
	private Handler handle;
   
	private static final int CALIB_DEFAULT = -80;
	private int caliberationValue = CALIB_DEFAULT;
   
	private double maxValue = 0.0;
	public boolean showMaxValue = false;
   
	//private String mode = "FAST";
   
	AudioRecord recordInstance = null;
   
	public AudioEngine(Handler h){
		this.handle = h;
		this.caliberationValue = readCalibValue();
		//String mode = "FAST";
		isRunning = false;
		maxValue = 0.0;
		showMaxValue = false;
	}
   
	
	public void reset(){
		caliberationValue = CALIB_DEFAULT;  
	}
   

   	public void start_engine(){
		this.isRunning = true;
		this.start();
	}
   

	public void stop_engine(){
		this.isRunning = false;
		recordInstance.stop();
	}
	

	public int getCalibValue(){
		return caliberationValue;
	}
   

	public void setCalibValue(int value){
		caliberationValue = value;
	}
   

   	public int readCalibValue(){   
   		return CALIB_DEFAULT;
   	}
    

   	public double getMaxValue(){
   		return maxValue;
   	}
   
   
   	public void setMaxValue(double max){
   		maxValue = max;
   	}
   

   	public void run(){
      
   		try{
         
   			android.os.Process
   				.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
   			recordInstance = new AudioRecord(
   									MediaRecorder.AudioSource.MIC,
   									FREQUENCY,
   									CHANNEL,
   									ENCODING,
   									8000);
         
   			recordInstance.startRecording();
   			short[] tempBuffer = new short[BUFFSIZE];
         
   			while (this.isRunning){
   				double splValue = 0.0;
   				double rmsValue = 0.0;
   			
   			for (int i = 0; i < BUFFSIZE - 1; i++){
            	tempBuffer[i] = 0;
            }
            
            recordInstance.read(tempBuffer, 0, BUFFSIZE);
            
            for (int i = 0; i < BUFFSIZE - 1; i++){
               rmsValue += tempBuffer[i] * tempBuffer[i];               
            }
            
            rmsValue = rmsValue / BUFFSIZE;
            rmsValue = Math.sqrt(rmsValue);
            
            splValue = 20 * Math.log10(rmsValue / P0);
            splValue = splValue + caliberationValue;
            splValue = round(splValue, 2);

            if (maxValue < splValue){
               maxValue = splValue;
            }
            
            if (!showMaxValue){
               Message msg = handle.obtainMessage(MY_MSG, splValue);
               handle.sendMessage(msg);
            }
            else{
            	
               Message msg = handle.obtainMessage(MY_MSG, maxValue);
               handle.sendMessage(msg);
               Thread.sleep(2000);
               msg = handle.obtainMessage(MAXOVER_MSG, maxValue);
               handle.sendMessage(msg);
               showMaxValue = false;
               
            }
            
         }
         
         recordInstance.stop();
      }
      	catch (Exception e){
      		//Message msg = handle.obtainMessage(MY_MSG, e);
      		//handle.sendMessage(msg);
      	}
   	}
   
 
   	public double round(double d, int decimalPlace){
   		// Javadoc:
   		// http://java.sun.com/j2se/1.5.0/docs/api/java/math/BigDecimal.html#BigDecimal(double)
   		BigDecimal bd = new BigDecimal(Double.toString(d));
   		bd = bd.setScale(decimalPlace, BigDecimal.ROUND_HALF_UP);
   		return bd.doubleValue();
   	}
   
}
