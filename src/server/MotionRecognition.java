package server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;

import net.sf.javaml.core.DenseInstance;
import net.sf.javaml.core.Instance;
import net.sf.javaml.distance.fastdtw.dtw.FastDTW;
import net.sf.javaml.distance.fastdtw.timeseries.TimeSeries;

public class MotionRecognition extends Thread {

	private static final boolean DEBUG_PARSING = false;
	private static final boolean DEBUG_MOTION_DETECTION = false;
	private static final boolean DEBUG_SIMILARITY = false;
	private static final boolean DEBUG_IDENTIFY_MOTION = true;

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
	private static final int MOTION_RECOGNITION = 4;
	private int state = MOTION_DETECTION;

	private static final int FRAME_SIZE = 64;
	private static final int THRESHOLD_MOTION = 100;

	private List<List<FeatureVector>> motionFramesList;

	public MotionRecognition(BlockingQueue<String> rawDataStreamQueue) {
		super();
		this.rawDataStreamQueue = rawDataStreamQueue;

		this.featureVectorForPlot = new LinkedList<FeatureVector>();
		this.axis_x = new LinkedList<Integer>();
		this.axis_x.add(0);

		this.featureVectorList = new LinkedList<FeatureVector>();
	}

	// Z-score normalization
	private FeatureVector normalization(FeatureVector fv, String type) {

		FeatureVector normalizedFV = null;

		if (type.equals("Z_SCORE")) {
			if (DEBUG_PARSING)
				System.out.println("Z_SCORE");
			double average = (fv.getAcceleration_x() + fv.getAcceleration_y() + fv.getAcceleration_z()) / 3;

			double standardDeviation = Math
					.sqrt((Math.pow(fv.getAcceleration_x() - average, 2) + Math.pow(fv.getAcceleration_y() - average, 2)
							+ Math.pow(fv.getAcceleration_z() - average, 2)) / (3 - 1));

			// The result is multiply 10 to be able to make it as a integer
			// number.
			int zScore_x = (int) ((fv.getAcceleration_x() - average) / standardDeviation * 10);
			int zScore_y = (int) ((fv.getAcceleration_y() - average) / standardDeviation * 10);
			int zScore_z = (int) ((fv.getAcceleration_z() - average) / standardDeviation * 10);

			// normalized data. magnitude just bypass to output.
			normalizedFV = new FeatureVector(zScore_x, zScore_y, zScore_z, fv.getAcceleration_magnitude());

		} else if (type.equals("NORM")) {
			if (DEBUG_PARSING)
				System.out.println("NORM");
			int norm = (int) (Math.sqrt(Math.pow(fv.getAcceleration_x(), 2) + Math.pow(fv.getAcceleration_y(), 2)
					+ Math.pow(fv.getAcceleration_z(), 2)));
			int norm_x = fv.getAcceleration_x() / norm * 10;
			int norm_y = fv.getAcceleration_y() / norm * 10;
			int norm_z = fv.getAcceleration_z() / norm * 10;

			normalizedFV = new FeatureVector(norm_x, norm_y, norm_z, norm);
		}
		if (DEBUG_PARSING)
			System.out.println("None");
		return normalizedFV;
	}

	private void parsingRawDataStream(String stream) {
		int size = featureVectorForPlot.size(); // store the last size of accel
												// data
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

						// store parsed data into a List for plot chart
						featureVectorForPlot.add(normalization(new FeatureVector(x, y, z, Math.abs(magnitude) ), "Z_SCORE"));

						// store parsed data into processing List
						featureVectorList.add(new FeatureVector(x, y, z, magnitude));

						if (DEBUG_PARSING)
							System.out.println("size: " + featureVectorForPlot.size() + ", " + axis_x.size());

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

	// Power = 1/N * Sum(f(n))^2
	private int getPowerOfFrame(List<FeatureVector> featureVectorList) {
		int power = 0;

		for (int i = 0; i < featureVectorList.size(); i++) {
			power += Math.pow(featureVectorList.get(i).getAcceleration_magnitude(), 2);
		}

		power /= featureVectorList.size();

		return power;
	}

	// Framing sample sequence.
	// 64 samples per frame, 50% overlap
	private List<FeatureVector> getFrame(List<FeatureVector> featureVectorList) {
		List<FeatureVector> frameFV = new LinkedList<FeatureVector>();

		// Framing the samples, 64 samples per frame, 50% overlap
		for (int i = 0; i < FRAME_SIZE/2; i++) {
			frameFV.add(featureVectorList.remove(0));
		}
		for (int i = 0; i < FRAME_SIZE/2; i++) {
			frameFV.add(featureVectorList.get(i));
		}
		frameIndex++;
		return frameFV;
	}

	// Detect whether active motion or not
	private boolean motionDetection(List<FeatureVector> featureVectorList) {

		if (DEBUG_MOTION_DETECTION)
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

	private double getSimilarity(List<List<FeatureVector>> templateFeatureFrameList,
			List<List<FeatureVector>> testingFeatureFrameList) {

		// ----- extract feature vectors from templateFeatureFrameList -----
		double[] d_x = new double[templateFeatureFrameList.size() * templateFeatureFrameList.get(0).size()],
				d_y = new double[templateFeatureFrameList.size() * templateFeatureFrameList.get(0).size()],
				d_z = new double[templateFeatureFrameList.size() * templateFeatureFrameList.get(0).size()],
				d_m = new double[templateFeatureFrameList.size() * templateFeatureFrameList.get(0).size()];

		// extract features frame by frame
		for (int j = 0; j < templateFeatureFrameList.size(); j++) {
			// extracting features from each frame
			for (int i = 0; i < templateFeatureFrameList.get(j).size(); i++) {

				// Normalized acceleration data
				FeatureVector fv = normalization(
						new FeatureVector(templateFeatureFrameList.get(j).get(i).getAcceleration_x(),
								templateFeatureFrameList.get(j).get(i).getAcceleration_y(),
								templateFeatureFrameList.get(j).get(i).getAcceleration_z(),
								templateFeatureFrameList.get(j).get(i).getAcceleration_magnitude()),
						"Z_SCORE");
//				d_x[j * templateFeatureFrameList.get(j).size() + i] = (double) fv.getAcceleration_x();
//				d_y[j * templateFeatureFrameList.get(j).size() + i] = (double) fv.getAcceleration_y();
//				d_z[j * templateFeatureFrameList.get(j).size() + i] = (double) fv.getAcceleration_z();
//				d_m[j * templateFeatureFrameList.get(j).size() + i] = (double) fv.getAcceleration_magnitude();
				d_x[j * templateFeatureFrameList.get(j).size() + i] = (double) Math.abs(fv.getAcceleration_x() - fv.getAcceleration_y());
				d_y[j * templateFeatureFrameList.get(j).size() + i] = (double) Math.abs(fv.getAcceleration_y() + fv.getAcceleration_x());
				d_z[j * templateFeatureFrameList.get(j).size() + i] = (double) fv.getAcceleration_z();
				d_m[j * templateFeatureFrameList.get(j).size() + i] = (double) fv.getAcceleration_magnitude();

				// Raw acceleration data
				// d_x[j * templateFeatureFrameList.get(j).size() + i] =
				// (double) templateFeatureFrameList.get(j).get(i)
				// .getAcceleration_x();
				// d_y[j * templateFeatureFrameList.get(j).size() + i] =
				// (double) templateFeatureFrameList.get(j).get(i)
				// .getAcceleration_y();
				// d_z[j * templateFeatureFrameList.get(j).size() + i] =
				// (double) templateFeatureFrameList.get(j).get(i)
				// .getAcceleration_z();
				// d_m[j * templateFeatureFrameList.get(j).size() + i] =
				// (double) templateFeatureFrameList.get(j).get(i)
				// .getAcceleration_magnitude();
			}
		}
		// if (DEBUG_SIMILARITY) {
		// for (int i=0;i<d_x.length;i++)
		// System.out.println("[" + d_x[i] + ", " + d_y[i] + ", " + d_z[i] + ",
		// " + d_m[i] + "], ");
		// System.out.println("\n");
		// }
		Instance templateInstance_x = new DenseInstance(d_x);
		Instance templateInstance_y = new DenseInstance(d_y);
		Instance templateInstance_z = new DenseInstance(d_z);
		Instance templateInstance_m = new DenseInstance(d_m);
		// -----------------------------------------------------------------

		// ----- extract feature vectors from testingFeatureFrameList -----
		d_x = new double[testingFeatureFrameList.size() * testingFeatureFrameList.get(0).size()];
		d_y = new double[testingFeatureFrameList.size() * testingFeatureFrameList.get(0).size()];
		d_z = new double[testingFeatureFrameList.size() * testingFeatureFrameList.get(0).size()];
		d_m = new double[testingFeatureFrameList.size() * testingFeatureFrameList.get(0).size()];

		// extract features frame by frame
		for (int j = 0; j < testingFeatureFrameList.size(); j++) {
			// extracting features from each frame
			for (int i = 0; i < testingFeatureFrameList.get(j).size(); i++) {

				// Normalized acceleration data
				FeatureVector fv = normalization(
						new FeatureVector(testingFeatureFrameList.get(j).get(i).getAcceleration_x(),
								testingFeatureFrameList.get(j).get(i).getAcceleration_y(),
								testingFeatureFrameList.get(j).get(i).getAcceleration_z(),
								testingFeatureFrameList.get(j).get(i).getAcceleration_magnitude()),
						"Z_SCORE");
//				d_x[j * testingFeatureFrameList.get(j).size() + i] = (double) fv.getAcceleration_x();
//				d_y[j * testingFeatureFrameList.get(j).size() + i] = (double) fv.getAcceleration_y();
//				d_z[j * testingFeatureFrameList.get(j).size() + i] = (double) fv.getAcceleration_z();
//				d_m[j * testingFeatureFrameList.get(j).size() + i] = (double) fv.getAcceleration_magnitude();
				d_x[j * testingFeatureFrameList.get(j).size() + i] = (double) Math.abs(fv.getAcceleration_x() - fv.getAcceleration_y());
				d_y[j * testingFeatureFrameList.get(j).size() + i] = (double) Math.abs(fv.getAcceleration_y() + fv.getAcceleration_x());
				d_z[j * testingFeatureFrameList.get(j).size() + i] = (double) fv.getAcceleration_z();
				d_m[j * testingFeatureFrameList.get(j).size() + i] = (double) fv.getAcceleration_magnitude();

				// Raw acceleration data
				// d_x[j * testingFeatureFrameList.get(j).size() + i] = (double)
				// testingFeatureFrameList.get(j).get(i)
				// .getAcceleration_x();
				// d_y[j * testingFeatureFrameList.get(j).size() + i] = (double)
				// testingFeatureFrameList.get(j).get(i)
				// .getAcceleration_y();
				// d_z[j * testingFeatureFrameList.get(j).size() + i] = (double)
				// testingFeatureFrameList.get(j).get(i)
				// .getAcceleration_z();
				// d_m[j * testingFeatureFrameList.get(j).size() + i] = (double)
				// testingFeatureFrameList.get(j).get(i)
				// .getAcceleration_magnitude();
			}
		}
		// if (DEBUG_SIMILARITY) {
		// for (int i=0;i<d_x.length;i++)
		// System.out.println("[" + d_x[i] + ", " + d_y[i] + ", " + d_z[i] + ",
		// " + d_m[i] + "], ");
		// System.out.println("\n");
		// }

		Instance testingInstance_x = new DenseInstance(d_x);
		Instance testingInstance_y = new DenseInstance(d_y);
		Instance testingInstance_z = new DenseInstance(d_z);
		Instance testingInstance_m = new DenseInstance(d_m);
		// -----------------------------------------------------------------

		// ----- calculate distance by fastDTW -----
//		double dist = (FastDTW.getWarpDistBetween(new TimeSeries(templateInstance_x), new TimeSeries(testingInstance_x),
//				30)
//				+ FastDTW.getWarpDistBetween(new TimeSeries(templateInstance_y), new TimeSeries(testingInstance_y), 30)
//				+ FastDTW.getWarpDistBetween(new TimeSeries(templateInstance_z), new TimeSeries(testingInstance_z), 30))/3;
		
		double dist = (
				FastDTW.getWarpDistBetween(new TimeSeries(templateInstance_x), new TimeSeries(testingInstance_x), 30) 
				+ FastDTW.getWarpDistBetween(new TimeSeries(templateInstance_y), new TimeSeries(testingInstance_y), 30)
				+ FastDTW.getWarpDistBetween(new TimeSeries(templateInstance_z), new TimeSeries(testingInstance_z), 30) * 3.5
				+ FastDTW.getWarpDistBetween(new TimeSeries(templateInstance_m), new TimeSeries(testingInstance_m), 30) * 1);

		if (DEBUG_SIMILARITY) {
			System.out.printf("Similarity: %d\n", (int) dist);
		}
		// -----------------------------------------

		return dist;
	}

	private void identifyMotion(String templateName, Queue<SimilarTemplate> similarityQueue) {

		try {
			// Read motion template from file.
			FileInputStream fis = new FileInputStream("./templates/" + templateName);
			ObjectInputStream ois;
			ois = new ObjectInputStream(fis);
			List<List<FeatureVector>> mList = (List<List<FeatureVector>>) ois.readObject();
			ois.close();
			fis.close();
			/*
			 * if (DEBUG_IDENTIFY_MOTION) { System.out.printf(
			 * "[Read from file] "); System.out.printf(
			 * "Number of frame is %d \n", mList.size()); for
			 * (List<FeatureVector> fv : mList) { for (int i = 0; i < fv.size();
			 * i++) { System.out.printf("[%d, %d, %d, %d], ",
			 * fv.get(i).getAcceleration_x(), fv.get(i).getAcceleration_y(),
			 * fv.get(i).getAcceleration_z(),
			 * fv.get(i).getAcceleration_magnitude()); }
			 * System.out.println("\n"); } }
			 */

			// compare run-time motion frames with template
			double dist = getSimilarity(mList, motionFramesList);
//			if (DEBUG_IDENTIFY_MOTION) {
//				System.out.printf("Motion %s, similarity: %d\n", templateName, (int) dist);
//			}

			similarityQueue.add(new SimilarTemplate(templateName, (int) dist));

		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}

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
				while (axis_x.size() > 200 && featureVectorForPlot.size() > 200) {
					axis_x.remove(0);
					featureVectorForPlot.remove(0);
				}

				// update chart
				if (axis_x.size() == featureVectorForPlot.size())
					realtimeChart.updateChart(axis_x, featureVectorForPlot);
				// ----------------------

				// if (DEBUG_MOTION_DETECTION) {
				// System.out.printf("State [%d] \n\n", state);
				// }

				// ----- processing data -----
				switch (state) {
				case MOTION_DETECTION:
					if (featureVectorList.size() >= FRAME_SIZE) {
						// get a frame from input sequence of samples
						List<FeatureVector> frameFV = getFrame(featureVectorList);

						if (DEBUG_MOTION_DETECTION) {
							System.out.printf("Number of samples in featureVectorList is %d \n",
									featureVectorList.size());
						}
						// Determine whether a motion or not
						if (true == motionDetection(frameFV)) {
							if (DEBUG_MOTION_DETECTION | DEBUG_IDENTIFY_MOTION) {
								System.out.printf("\n ><><><><><>< Motion detected! ><><><><><><\n");
							}

							// create a list to store frames which has activity
							// motion.
							motionFramesList = new LinkedList<List<FeatureVector>>();
							motionFramesList.add(frameFV);

							state = FIND_END_FRAME;
						} else {
							state = MOTION_DETECTION;
						}
					}
					break;
				case FIND_END_FRAME:
					if (featureVectorList.size() >= FRAME_SIZE) {
						// get a frame from input sequence of samples
						List<FeatureVector> frameFV = getFrame(featureVectorList);

						if (DEBUG_MOTION_DETECTION) {
							System.out.printf("Number of samples in featureVectorList is %d \n",
									featureVectorList.size());
						}

						// Determine whether the end-point(frame) of motion or
						// not
						if (true == motionDetection(frameFV)) {
							if (DEBUG_MOTION_DETECTION) {
								System.out.printf("*****Motion detected! \n");
							}
							motionFramesList.add(frameFV);
							state = FIND_END_FRAME;
						} else {
							// the end-point(frame) of motion found.
							if (DEBUG_MOTION_DETECTION) {
								System.out.printf("*****Motion Terminate! \n");
							}
							motionFramesList.add(frameFV);
							state = ANALYSIS_FRAMES;
						}
					}
					break;

				case ANALYSIS_FRAMES:
					// print all samples of the activity motion frames
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

					// Step for training phase, which write the activity motion
					// frames into a file, it is used as motion-template.
					if (TRAINING_PHASE) {
						// Write activity motion frames into a file.
						FileOutputStream fos = new FileOutputStream("./dataset/motion_" + String.valueOf(frameIndex));
						ObjectOutputStream oos = new ObjectOutputStream(fos);
						oos.writeObject(motionFramesList);
						oos.close();
						fos.close();

						if (DEBUG_MOTION_DETECTION) {
							// Read activity motion frames from the file to
							// check whether store correctly.
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
					state = MOTION_RECOGNITION;
					break;
				case MOTION_RECOGNITION:
					long time0 = System.currentTimeMillis();
					
					if (DEBUG_IDENTIFY_MOTION) {
						System.out.println("\n========================");
					}
					// a min-Heap contain similarity of all templates.
					// top of the Heap is the most similar one of templates.
					PriorityQueue<SimilarTemplate> similarityQueue = new PriorityQueue<SimilarTemplate>();

					// Calculate similarity between testing frames and templates
//					File[] templateFileList = new File(new String("./templates/")).listFiles();
//					for (int i = 0; i < templateFileList.length; i++) {
//						identifyMotion(templateFileList[i].getName(), similarityQueue);
//					}										
					File[] templateFolderList = new File(new String("./templates/")).listFiles();												
					for (int k = 0; k < templateFolderList.length; k++) {
						File[] templateFileList = new File(new String("./templates/"+templateFolderList[k].getName())).listFiles();										
						for (int i = 0; i < templateFileList.length; i++) {								
							identifyMotion(templateFolderList[k].getName()+"/"+templateFileList[i].getName(), similarityQueue);
						}
						
					}
					

					if (DEBUG_IDENTIFY_MOTION) {
						SimilarTemplate mostSimilarOne = similarityQueue.remove();
						SimilarTemplate similarOne = similarityQueue.remove();
						
						String[] motionOfmostSimilarOne = mostSimilarOne.getTemplateName().split("_");
						String[] motionOfSimilarOne = similarOne.getTemplateName().split("_");

						System.out.println( similarOne.getTemplateName() + " " + similarOne.getSimilarity() + " - "
								+ mostSimilarOne.getTemplateName() + " " + mostSimilarOne.getSimilarity() + " = "
								+ String.valueOf(similarOne.getSimilarity() - mostSimilarOne.getSimilarity()) );
						
						System.out.println("========================");
						
//						while (!similarityQueue.isEmpty()) {
//							SimilarTemplate st = similarityQueue.remove();
//							System.out.printf("%s\t\t similarity: %d \n", st.getTemplateName(), st.getSimilarity());
//						}
												
						System.out.println("========================");												

						System.out.println("[DTW] Detect: " + motionOfmostSimilarOne[1]);
						
						
						long time1 = System.currentTimeMillis();
						System.out.println( (time1 - time0) +"ms\n");
						System.out.println("\n----------------------");
						
						SVMRecognizer.identifyMotionJ(motionFramesList);												
						long time2 = System.currentTimeMillis();
						System.out.println( (time2 - time1) +"ms\n");
						
						System.out.println("\n----------------------");
						SVMRecognizer.identifyMotionB(motionFramesList);
						long time3 = System.currentTimeMillis();
						System.out.println( (time3 - time2) +"ms\n");
					}
					state = MOTION_DETECTION;
					break;
				}
				// ----------------------

			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (FileNotFoundException e) {
				System.out.println("[Read file fail] ");
			} catch (IOException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}

		}
	}

}
