package server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;


public class WorkerRunnable implements Runnable{
	
	private static final int GETID = 0;
	private static final int GRANT = 1;
	private static final int DATABYPASS = 2;
	private int cur_state = 0;
	
	//Socket related members
    protected Socket clientSocket = null;
    private DataInputStream input = null;
    private DataOutputStream output = null;

    //a list contain all connecting user's data(such as, ID, queue) 
    private static List<UserData> userList = new ArrayList<UserData>();
    //userData used by current thread 
    private UserData userData = null;
    //queues used by current thread
    private BlockingQueue<String> writeQueue = null;
    private BlockingQueue<String> readQueue = null;
    
    private String userID = "PJay";
    private boolean userExisted = false;
    
    private String inMsg;
    String[] msgArray = new String[3];
    
    public WorkerRunnable(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    public void run() {
    	long start_time = System.currentTimeMillis();
    	if (ThreadPooledServer.DEBUG) System.out.println(clientSocket.getRemoteSocketAddress() + " connected.");
    	
    	try {
    		//get in/output stream
    		input = new DataInputStream( this.clientSocket.getInputStream() );
            output = new DataOutputStream( this.clientSocket.getOutputStream() );
            
            
            //Super loop
            while(true) {
	            switch (cur_state) {
	            	case GETID:
	            		inMsg = input.readUTF();
	                	if (ThreadPooledServer.DEBUG) System.out.println(": " + inMsg);
	                    msgArray = inMsg.split(", ");
	                    if (msgArray != null && msgArray[0].equals("N") && msgArray[1].equals("0")) {
	                    	userID = new String(msgArray[2].toString());
	                    	if (ThreadPooledServer.DEBUG) System.out.println("User ID: " + userID);
	                    	cur_state = GRANT;
	                    }
	            		break;
	            	case GRANT:
	            		output.writeUTF("N, 1, ok");
	            		output.flush();
	            		
	            		//traversal the user list to check the connected userID is existed or not.    	
	        	    	for (UserData ud : userList) {
	        	    		if (ud.getID().equals(userID)) {
	        	    			if (ThreadPooledServer.DEBUG) System.out.println(userID + " existed");
	        	    			userExisted = true;
	        	    			userData = ud;
	        	    			break; 
	        	    		}
	        	    	}
	        	    	
	        	    	//if the incoming user is never seen before, create new userData
	        	    	if (userExisted == false) {
	        	    		if (ThreadPooledServer.DEBUG) System.out.println("Create new userData for " + userID);
	        	    		userData = new UserData(userID);
	        	    		userList.add(userData);
	        	    	}
	            		
	        	    	//determine which queue should be use
	        	    	if (userData.isIsfrom1to2QueueInUse() != true) {
	        	    		userData.setIsfrom1to2QueueInUse(true);
	        	    		writeQueue = userData.getfrom1to2Queue();
	        	    		readQueue = userData.getfrom2to1Queue();
	        	    	} else if (userData.isIsfrom2to1QueueInUse() != true){
	        	    		userData.setIsfrom2to1QueueInUse(true);
	        	    		writeQueue = userData.getfrom2to1Queue();
	        	    		readQueue = userData.getfrom1to2Queue();
	        	    	} else {
	        	    		//all queue of specific userID are in use.
	        	    		output.writeUTF("two user connected.");
	                		output.flush();
	        	    		writeQueue = null;
	        	    		readQueue = null;
	        	    		if (output != null) output.close();
	        				if (input != null) input.close();
	        				
	        				output.writeUTF("trigger exception to terminate this conncetion and thread");
	                		output.flush();
	        	    	}
	            		
	        	    	cur_state = DATABYPASS;
	            		break;
	            	case DATABYPASS:
	            		//------------------- do works --------------------            	
	                	
	                	//pass user_x's data to user_y
	            		if (input.available() > 0) {
	            			inMsg = input.readUTF();
	            			if (writeQueue.offer(inMsg) == true) {
	            				if (ThreadPooledServer.DEBUG) System.out.printf("%s )Received: \"%s\" from %s\n",userID, inMsg, clientSocket.getRemoteSocketAddress());
	            			} else {
	        					if (ThreadPooledServer.DEBUG) System.out.printf("%s )writeQueue is full\n", userID);
	            			}
	            		} else {
	            			//No more data received from client,
	            			//test whether the client is disconnected or not
	            			output.writeUTF("W, 0, Are you alive?");
	                		output.flush();
	                		Thread.sleep(1000);
	            		}
	            		
	            		//take data from user_y's then send to user_x         		
	            		if ( !readQueue.isEmpty()) {
	            			String msgFromClient2 = readQueue.poll();
	            			output.writeUTF(msgFromClient2);
	                		output.flush();
	                		if (ThreadPooledServer.DEBUG) System.out.printf("%s )Take data \"%s\" from readQueue, send to  %s\n", userID, msgFromClient2, clientSocket.getRemoteSocketAddress());
	            		}            	
	                    Thread.sleep(250);
	            		
	            		break;
	            		
	            	default:;
	            }
            }//End Of SuperLoop
            
            // -----------------------           
        } catch (IOException e) {
            //report exception somewhere.
            e.printStackTrace();
        } catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			//free the using token of writeQueue						
			if (writeQueue.equals(userData.getfrom1to2Queue())) {
				userData.setIsfrom1to2QueueInUse(false);
			} else if (writeQueue.equals(userData.getfrom2to1Queue())) {
				userData.setIsfrom2to1QueueInUse(false);
			}
			//If all queue(2) not be use, it means the userData of these queue can be deleted.
			if (!userData.isIsfrom1to2QueueInUse() & !userData.isIsfrom2to1QueueInUse()) {
				if (ThreadPooledServer.DEBUG) System.out.println("Remove " + userData.getID() + " from user list");
				userList.remove(userData);
			}
			
        	long end_time = System.currentTimeMillis();
            System.out.println(clientSocket.getRemoteSocketAddress() + " connected time: " + String.valueOf(end_time - start_time) + " ms");
        	
        	try {
				if (output != null) output.close();
				if (input != null) input.close();
				if (clientSocket != null) clientSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}            
        }    	
    }
}
