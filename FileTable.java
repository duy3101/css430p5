import java.util.Vector;

public class FileTable
{
    private Vector table;   // the actual entity of this file table
    private Directory dir;  // the root directory

    public FileTable(Directory directory)
    {
        table = new Vector();   // instantiate a file table
        dir = directory;        // receive a reference to the Directory from the filesystem
    }

    public synchronized FileTableEntry falloc(String filename, String mode)
    {
        // allocate a new file table entry for this filename
        // allocate/retrieve and register the corresponding inode using dir
        // increment this inodes's count
        // immediately write back this inode to the disk
        // return a reference to this file table entry
        return FileTableEntry;
    }

    public synchronized boolean ffree(FileTableEntry e)
    {
        // recieve a file table entry reference
        // save the corresponding inode to the disk
        // free this file table entry
        // return true if this file table found in my table
        return false;
    }

    public synchronized boolean fempty()
    {
        return table.isEmpty();
        // return if table is empty
        // should be called before starting a format
    }
}