package server;

import java.io.Serializable;

public class FeatureVector implements Serializable{

	private int acceleration_x;
	private int acceleration_y;
	private int acceleration_z;
	private int acceleration_magnitude;
		
	public FeatureVector(int acceleration_x, int acceleration_y, int acceleration_z, int acceleration_magnitude) {
		super();
		this.acceleration_x = acceleration_x;
		this.acceleration_y = acceleration_y;
		this.acceleration_z = acceleration_z;
		this.acceleration_magnitude = acceleration_magnitude;
	}
	public int getAcceleration_x() {
		return acceleration_x;
	}
	public void setAcceleration_x(int acceleration_x) {
		this.acceleration_x = acceleration_x;
	}
	public int getAcceleration_y() {
		return acceleration_y;
	}
	public void setAcceleration_y(int acceleration_y) {
		this.acceleration_y = acceleration_y;
	}
	public int getAcceleration_z() {
		return acceleration_z;
	}
	public void setAcceleration_z(int acceleration_z) {
		this.acceleration_z = acceleration_z;
	}
	public int getAcceleration_magnitude() {
		return acceleration_magnitude;
	}
	public void setAcceleration_magnitude(int acceleration_magnitude) {
		this.acceleration_magnitude = acceleration_magnitude;
	}
	
}
