// File:  TCB.java
// Group: Marc Skaarup, Dewey Nguyen, Jake Stewart
// Class: CSS430
//
// Build for ThreadOS: javac *.java
//                     java Boot
//                     l Test5

public class TCB {
    private Thread thread = null;
    private int tid = 0;
    private int pid = 0;
    private boolean terminated = false;
	private int sleepTime = 0;
	

	// user file descripter table:
	// each entry pointing to a file (structure) table entry
    public FileTableEntry[] ftEnt = null; // added for the file system

    public TCB( Thread newThread, int myTid, int parentTid ) {
	thread = newThread;
	tid = myTid;
	pid = parentTid;
	terminated = false;

	ftEnt = new FileTableEntry[32];    // added for the file system
	for (int i = 0; i < 32; i++)	// added for the file system
		ftEnt[i] = null;

	System.err.println( "threadOS: a new thread (thread=" + thread + 
			    " tid=" + tid + 
			    " pid=" + pid + ")");
    }

    public synchronized Thread getThread( ) {
	return thread;
    }

    public synchronized int getTid( ) {
	return tid;
    }

    public synchronized int getPid( ) {
	return pid;
    }

    public synchronized boolean setTerminated( ) {
	terminated = true;
	return terminated;
    }

    public synchronized boolean getTerminated( ) {
	return terminated;
    }

 	// Implemented for P5
    public synchronized int getFd( FileTableEntry entry ) {
	if ( entry == null )
	    return -1;
	for ( int i = 3; i < 32; i++ ) {
	    if ( ftEnt[i] == null ) {
		ftEnt[i] = entry;
		return i;
	    }
	}
	return -1;
    }

    // Implemented for P5
    public synchronized FileTableEntry returnFd( int fd ) {
	if ( fd >= 3 && fd < 32 ) {
	    FileTableEntry oldEnt = ftEnt[fd];
	    ftEnt[fd] = null;
	    return oldEnt;
	}
	else
	    return null;
    }

    // Implemented for P5
    public synchronized FileTableEntry getFtEnt( int fd ) {
	if ( fd >= 3 && fd < 32 )
	    return ftEnt[fd];
	else
	    return null;
    }
}
