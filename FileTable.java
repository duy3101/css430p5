import java.net.http.WebSocket;
import java.util.Vector;

import javax.lang.model.util.ElementScanner6;

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
    
        short iNumber = -1;
        Inode inode = null;

        // allocate/retrieve and register the corresponding inode using dir
        while (true)
        {
            if (filename.equals("/"))
            {
                iNumber = 0;
            }
            else
            {
                iNumber = dir.namei(filename);
            }
            

            if (iNumber >= 0)
            {
                
                inode = new Inode(iNumber);
                
                if (mode.compareTo(FileSystem.READ))
                {

                    if (inode.flag == Inode.FLAG_USED || inode.flag == Inode.FLAG_UNUSED)
                    {
                        inode.flag = Inode.FLAG_READ;
                        break;
                    }

                    else if (inode.flag == Inode.FLAG_READ)
                    {
                        break;
                    }

                    else if (inode.flag == Inode.FLAG_WRITE)
                    {
                        try 
                        {
                            wait();
                            inode.flag = Inode.FLAG_READ;
                        }
                        catch(InterruptedException e) {}

                        break;
                    }

                    else if (inode.flag == Inode.FLAG_DELETE)
                    {
                        iNumber = -1;
                        return null;
                    }
                }

                else
                {

                    if (inode.flag == Inode.FLAG_USED || inode.flag == Inode.FLAG_UNUSED)
                    {
                        inode.flag = Inode.FLAG_WRITE;
                        break;
                    }

                    else if (inode.flag == Inode.FLAG_WRITE || inode.flag == Inode.FLAG_READ)
                    {
                        try 
                        {
                            wait();
                            inode.flag = Inode.FLAG_WRITE;
                        }
                        catch(InterruptedException e) {}

                        break;
                    }
                    
                    else if (inode.flag == Inode.FLAG_DELETE)
                    {
                        iNumber = -1;
                        return null;
                    }
                }
            }
            else
            {
                //iNumber is somethingelse
                return null;
            }
        }
        // increment this inodes's count
        // immediately write back this inode to the disk
        // return a reference to this file table entry
        inode.count++;
        inode.toDisk(iNumber);
        FileTableEntry anEntry = new FileTableEntry(inode, iNumber, mode);
        table.addElement(anEntry);
        return anEntry;   
    }





    public synchronized boolean ffree(FileTableEntry anEntry)
    {
        // recieve a file table entry reference
        // save the corresponding inode to the disk
        // free this file table entry
        // return true if this file table found in my table

        if (table.removeElement(anEntry))
        {
            Inode inode = anEntry.inode;

            if (inode.count > 0)
            {
                inode.count--;
            }

            if (inode.count == 0)
            {
                inode.flag = Inode.FLAG_UNUSED;
            }

            notify(); // ?

            inode.toDisk(anEntry.iNumber);
            return true;
        }

        return false;
    
    }

    public synchronized boolean fempty()
    {
        return table.isEmpty();
        // return if table is empty
        // should be called before starting a format
    }
}