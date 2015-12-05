package server;

import java.util.LinkedList;
import java.util.List;

import javax.swing.JFrame;

import org.knowm.xchart.Chart;
import org.knowm.xchart.XChartPanel;

public class RealtimeChart {
	private XChartPanel chartPanel;
	private Chart chart;
	public static final String SERIES_NAME_1 = "x";
	public static final String SERIES_NAME_2 = "y";
	public static final String SERIES_NAME_3 = "z";
	public static final String SERIES_NAME_4 = "average";
	private List<Integer> xData;
	private List<Integer> yData;
	
	public RealtimeChart() {
		buildPanel();
	}

	public void buildPanel() {	
		// Setup the panel
		chartPanel = new XChartPanel(getChart());
		
		// Schedule a job for the event-dispatching thread:
	    // creating and showing this application's GUI.
	    javax.swing.SwingUtilities.invokeLater(new Runnable() {
	      @Override
	      public void run() {
	        // Create and set up the window.
	        JFrame frame = new JFrame("XChart");
	        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	        frame.add(chartPanel);
	        // Display the window.
	        frame.pack();
	        frame.setVisible(true);
	      }
	    });
	}

	public Chart getChart() {
        
		//initial plot value
		xData = new LinkedList<Integer>();
	    yData = new LinkedList<Integer>();
	    xData.add(0);
	    yData.add(0);
		
	    // Create Chart
		chart = new Chart(500, 400);
		chart.setChartTitle("Acceleration Chart");
		chart.setXAxisTitle("Time(ms)");
		chart.setYAxisTitle("Acceleration Raw Data");
	    chart.addSeries(SERIES_NAME_1, xData, yData);
	    chart.addSeries(SERIES_NAME_2, xData, yData);
	    chart.addSeries(SERIES_NAME_3, xData, yData);
	    chart.addSeries(SERIES_NAME_4, xData, yData);
	    return chart;
	}
	
	public void updatChart(List<Integer> accel_avarge, List<Integer> accel_x, List<Integer> accel_y, List<Integer> accel_z, List<Integer> axis_x) {
		chartPanel.updateSeries(RealtimeChart.SERIES_NAME_1, axis_x, accel_x, null);
		chartPanel.updateSeries(RealtimeChart.SERIES_NAME_2, axis_x, accel_y, null);
		chartPanel.updateSeries(RealtimeChart.SERIES_NAME_3, axis_x, accel_z, null);
		chartPanel.updateSeries(RealtimeChart.SERIES_NAME_4, axis_x, accel_avarge, null);
	}

}

