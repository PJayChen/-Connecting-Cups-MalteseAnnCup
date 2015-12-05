package server;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

public class MotionRecognition extends Thread{
    
	private static final boolean DEBUG = true;
	
	private BlockingQueue<String> rawDataStreamQueue;
	private List<Integer> accel_x, accel_y, accel_z, accel_average;
	private List<Integer> axis_x;
	
	
	public MotionRecognition(BlockingQueue<String> rawDataStreamQueue) {
		super();
		this.rawDataStreamQueue = rawDataStreamQueue;
		this.accel_x = new LinkedList<Integer>();
		this.accel_y = new LinkedList<Integer>();
		this.accel_z = new LinkedList<Integer>();
		
		this.accel_average = new LinkedList<Integer>();
		
		this.axis_x = new LinkedList<Integer>();
		this.axis_x.add(0);
	}


	private void parsingRawDataStream(String stream) {
		int size = accel_x.size(); //store the last size of accel data
		if(DEBUG) System.out.print(stream);
		String[] splitedStream = stream.split(";");
		if (splitedStream[0].equals("N,2,")) {
			if(DEBUG) System.out.printf("[M] There are %s data\n", splitedStream.length - 1);	
			for (int i = 1; i < splitedStream.length;i++) {
				String[] accelStr = splitedStream[i].split(",");
				if (accelStr.length == 3) {					
					try {
						int z = Integer.valueOf(accelStr[2]);
						int y = Integer.valueOf(accelStr[1]);
						int x = Integer.valueOf(accelStr[0]);
						//store parsed data into Lists
						accel_z.add(Integer.valueOf(accelStr[2]));
						accel_y.add(Integer.valueOf(accelStr[1]));
						accel_x.add(Integer.valueOf(accelStr[0]));
						accel_average.add( (int) (Math.sqrt( Math.pow(x,2) + Math.pow(y, 2) + Math.pow(z, 2) ) - 64) );
					} catch (NumberFormatException e) {
						if(DEBUG) System.out.print("[M] Number format error. \n");
					}
				}					
			}
		}
		
		if(DEBUG) {
			System.out.printf("Stored data");
			for (int i = size; i < accel_x.size();i++) {
				System.out.printf("%d %d %d, ", accel_x.get(i), accel_y.get(i), accel_z.get(i));
			}
			System.out.printf("\n");
		}
		
	}
	
	@Override
	public void run() {
		//Setup chart		
	    final RealtimeChart realtimeChart = new RealtimeChart();
		
        while(true) {
        	      	
        	try {
        		//take data from the queue and parse it.
				parsingRawDataStream(rawDataStreamQueue.take());
				
				//Increase time axis
				while (axis_x.size() < accel_x.size())
				    axis_x.add(axis_x.get(axis_x.size() - 1) + 10);
				
				// Limit the total number of points
			    while (accel_x.size() > 200) {
			    	accel_x.remove(0);
			    }
			    while (accel_y.size() > 200) {
			    	accel_y.remove(0);
			    }
			    while (accel_z.size() > 200) {
			    	accel_z.remove(0);
			    }
			    while (axis_x.size() > 200) {
			    	axis_x.remove(0);
			    }
			    while (accel_average.size() > 200) {
			    	accel_average.remove(0);
			    }
			    realtimeChart.updatChart(accel_average, accel_x, accel_y, accel_z, axis_x);
				
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
        	
        }
	}
	
}
