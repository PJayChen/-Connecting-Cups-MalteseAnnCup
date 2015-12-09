package server;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

public class MotionRecognition extends Thread {

	private static final boolean DEBUG_PARSING = false;

	private static final boolean DEBUG_MOTION_DETECTION = true;

	private static final boolean TRAINING_PHASE = true;

	private BlockingQueue<String> rawDataStreamQueue;

	private List<FeatureVector> featureVectorList;
	private int frameIndex = 0;

	// members for plot chart
	private List<FeatureVector> featureVectorForPlot;
	private List<Integer> axis_x;

	private static final int MOTION_DETECTION = 1;
	private static final int FIND_END_FRAME = 2;
	private static final int ANALYSIS_FRAMES = 3;
	private int state = MOTION_DETECTION;
	
	private static final int THRESHOLD_MOTION = 50;

	private List<List<FeatureVector>> motionFramesList;

	public MotionRecognition(BlockingQueue<String> rawDataStreamQueue) {
		super();
		this.rawDataStreamQueue = rawDataStreamQueue;

		this.featureVectorForPlot = new LinkedList<FeatureVector>();
		this.axis_x = new LinkedList<Integer>();
		this.axis_x.add(0);

		this.featureVectorList = new LinkedList<FeatureVector>();
	}

	private void parsingRawDataStream(String stream) {
		int size = featureVectorForPlot.size(); // store the last size of accel data
		if (DEBUG_PARSING)
			System.out.print(stream);
		String[] splitedStream = stream.split(";");
		if (splitedStream[0].equals("N,2,")) {
			if (DEBUG_PARSING)
				System.out.printf("[M] There are %s data\n", splitedStream.length - 1);
			for (int i = 1; i < splitedStream.length; i++) {
				String[] accelStr = splitedStream[i].split(",");
				if (accelStr.length == 3) {
					try {
						int z = Integer.valueOf(accelStr[2]);
						int y = Integer.valueOf(accelStr[1]);
						int x = Integer.valueOf(accelStr[0]);
						int magnitude = (int) (Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2) + Math.pow(z, 2)) - 64);
						int norm = (int) (Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2) + Math.pow(z, 2)));
						
						// store parsed data into a List for plot chart
						featureVectorForPlot.add(new FeatureVector(x,y,z,magnitude));

						// store parsed data into processing List
						featureVectorList.add(new FeatureVector(x, y, z, magnitude));

					} catch (NumberFormatException e) {
						if (DEBUG_PARSING)
							System.out.print("[M] Number format error. \n");
					}
				}
			}
		}

		if (DEBUG_PARSING) {
			System.out.printf("Stored data:\n");
			for (int i = size; i < featureVectorForPlot.size(); i++) {
				// System.out.printf("%d %d %d, ", accel_x.get(i),
				// accel_y.get(i), accel_z.get(i));
				System.out.printf("%d %d %d, magnitude: %d; ", featureVectorList.get(i).getAcceleration_x(),
						featureVectorList.get(i).getAcceleration_y(), featureVectorList.get(i).getAcceleration_z(),
						featureVectorList.get(i).getAcceleration_magnitude());
			}
			System.out.printf("\n");
		}

	}
    
	//Power = 1/N * Sum(f(n))^2
	private int getPowerOfFrame(List<FeatureVector> featureVectorList) {
		int power = 0;

		for (int i = 0; i < featureVectorList.size(); i++) {
			power += Math.pow(featureVectorList.get(i).getAcceleration_magnitude(), 2);
		}

		power /= featureVectorList.size();

		return power;
	}

	//Framing sample sequence.
	//64 samples per frame, 50% overlap
	private List<FeatureVector> getFrame(List<FeatureVector> featureVectorList) {
		List<FeatureVector> frameFV = new LinkedList<FeatureVector>();

		// Framing the samples, 64 samples per frame, 50% overlap
		for (int i = 0; i < 32; i++) {
			frameFV.add(featureVectorList.remove(0));
		}
		for (int i = 0; i < 32; i++) {
			frameFV.add(featureVectorList.get(i));
		}
		frameIndex++;
		return frameFV;
	}

	//Detect whether active motion or not 
	private boolean motionDetection(List<FeatureVector> featureVectorList) {

		System.out.printf("Frame[%d]: frame size is %d, ", frameIndex, featureVectorList.size());

		int power = getPowerOfFrame(featureVectorList);

		if (DEBUG_MOTION_DETECTION) {
			System.out.printf("Power is %d \n", power);
		}

		if (power > THRESHOLD_MOTION)
			return true;
		else
			return false;
	}

	@Override
	public void run() {
		// ----- Setup chart -----
		final RealtimeChart realtimeChart = new RealtimeChart();

		while (true) {

			try {
				// ----- take data from the queue and parse it. -----
				parsingRawDataStream(rawDataStreamQueue.take());

				// ----- Plot data -----				
				// Increase time axis
				while (axis_x.size() < featureVectorForPlot.size())
					axis_x.add(axis_x.get(axis_x.size() - 1) + 10);
				// Limit the total number of points
				while (axis_x.size() > 200  && featureVectorForPlot.size() > 200) {
					axis_x.remove(0);
					featureVectorForPlot.remove(0);
				}
					
				//update chart
				realtimeChart.updateChart(axis_x, featureVectorForPlot);
				// ----------------------

//				if (DEBUG_MOTION_DETECTION) {
//					System.out.printf("State [%d] \n\n", state);
//				}
				// ----- processing data -----
				switch (state) {
				case MOTION_DETECTION:
					if (featureVectorList.size() >= 64) {
                        //get a frame from input sequence of samples
						List<FeatureVector> frameFV = getFrame(featureVectorList);

						if (DEBUG_MOTION_DETECTION) {
							System.out.printf("Number of samples in featureVectorList is %d \n", featureVectorList.size());
						}
						// Determine whether a motion or not
						if (true == motionDetection(frameFV)) {
							if (DEBUG_MOTION_DETECTION) {
								System.out.printf("*****Motion detected! \n");
							}
							
							//create a list to store frames which has activity motion.
							motionFramesList = new LinkedList<List<FeatureVector>>();
							motionFramesList.add(frameFV);
							
							state = FIND_END_FRAME;
						} else {
							state = MOTION_DETECTION;
						}
					}
					break;
				case FIND_END_FRAME:
					if (featureVectorList.size() >= 64) {
						//get a frame from input sequence of samples
						List<FeatureVector> frameFV = getFrame(featureVectorList);

						if (DEBUG_MOTION_DETECTION) {
							System.out.printf("Number of samples in featureVectorList is %d \n", featureVectorList.size());
						}

						// Determine whether the end-point(frame) of motion or not
						if (true == motionDetection(frameFV)) {
							if (DEBUG_MOTION_DETECTION) {
								System.out.printf("*****Motion detected! \n");
							}
							motionFramesList.add(frameFV);
							state = FIND_END_FRAME;
						} else {
							//the end-point(frame) of motion found.
							if (DEBUG_MOTION_DETECTION) {
								System.out.printf("*****Motion Terminate! \n");
							}
							motionFramesList.add(frameFV);
							state = ANALYSIS_FRAMES;
						}
					}
					break;

				case ANALYSIS_FRAMES:
					//print all samples of the activity motion frames
					if (DEBUG_MOTION_DETECTION) {
						System.out.printf("Number of frame is %d \n", motionFramesList.size());
						for (List<FeatureVector> fv : motionFramesList) {
							for (int i = 0; i < fv.size(); i++) {
								System.out.printf("[%d, %d, %d, %d], ", fv.get(i).getAcceleration_x(),
										fv.get(i).getAcceleration_y(), fv.get(i).getAcceleration_z(),
										fv.get(i).getAcceleration_magnitude());
							}
							System.out.println("\n");
						}
					}

					//Step for training phase, which write the activity motion frames into a file, it is used as motion-template.
					if (TRAINING_PHASE) {
						//Write activity motion frames into a file.
						FileOutputStream fos = new FileOutputStream("./dataset/motion_" + String.valueOf(frameIndex));
						ObjectOutputStream oos = new ObjectOutputStream(fos);
						oos.writeObject(motionFramesList);
						oos.close();
						fos.close();
						
						if (DEBUG_MOTION_DETECTION) {
							//Read activity motion frames from the file to check whether store correctly.
							FileInputStream fis = new FileInputStream("./dataset/motion_" + String.valueOf(frameIndex));
							ObjectInputStream ois = new ObjectInputStream(fis);
							List<List<FeatureVector>> mList = (List<List<FeatureVector>>) ois.readObject();
							ois.close();
							fis.close();
							System.out.printf("[Read from file] \n");						
							System.out.printf("Number of frame is %d \n", mList.size());
							for (List<FeatureVector> fv : mList) {
								for (int i = 0; i < fv.size(); i++) {
									System.out.printf("[%d, %d, %d, %d], ", fv.get(i).getAcceleration_x(),
											fv.get(i).getAcceleration_y(), fv.get(i).getAcceleration_z(),
											fv.get(i).getAcceleration_magnitude());
								}
								System.out.println("\n");
							}
						}

					}
					state = MOTION_DETECTION;
					break;
				}
				// ----------------------

			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}

		}
	}

}
