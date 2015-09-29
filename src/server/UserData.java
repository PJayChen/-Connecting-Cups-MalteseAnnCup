package server;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class UserData {
	private String ID = null;
	private BlockingQueue<String> from1to2Queue = null;
	private BlockingQueue<String> from2to1Queue = null;
	private boolean isfrom1to2QueueInUse = false;
	private boolean isfrom2to1QueueInUse = false;
	
	public synchronized boolean isIsfrom1to2QueueInUse() {
		return isfrom1to2QueueInUse;
	}

	public synchronized void setIsfrom1to2QueueInUse(boolean isfrom1to2QueueInUse) {
		this.isfrom1to2QueueInUse = isfrom1to2QueueInUse;
	}

	public synchronized boolean isIsfrom2to1QueueInUse() {
		return isfrom2to1QueueInUse;
	}

	public synchronized void setIsfrom2to1QueueInUse(boolean isfrom2to1QueueInUse) {
		this.isfrom2to1QueueInUse = isfrom2to1QueueInUse;
	}

	public UserData(String iD) {
		super();
		ID = iD;
		from1to2Queue = new ArrayBlockingQueue<String>(10);
		from2to1Queue = new ArrayBlockingQueue<String>(10);
	}

	public synchronized String getID() {
		return ID;
	}

	public synchronized BlockingQueue<String> getfrom1to2Queue() {
		return from1to2Queue;
	}

	public synchronized void setfrom1to2Queue(BlockingQueue<String> from1to2Queue) {
		this.from1to2Queue = from1to2Queue;
	}

	public synchronized BlockingQueue<String> getfrom2to1Queue() {
		return from2to1Queue;
	}

	public synchronized void setfrom2to1Queue(BlockingQueue<String> from2to1Queue) {
		this.from2to1Queue = from2to1Queue;
	}
}
