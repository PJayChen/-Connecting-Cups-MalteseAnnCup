package server;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class Main {

	public static void main(String[] args) {
		
		BlockingQueue<String> rawDataStreamQueue = new ArrayBlockingQueue<String>(50);
		
		ThreadPooledServer server = new ThreadPooledServer(5508, rawDataStreamQueue);
		new Thread(server).start();

		MotionRecognition mr = new MotionRecognition(rawDataStreamQueue);
		mr.start();
//		try {
//		    Thread.sleep(20 * 1000);
//		} catch (InterruptedException e) {
//		    e.printStackTrace();
//		}
//		System.out.println("Stopping Server");
//		server.stop();
	}

}
