



public class FileSystem
{

    public static final String READ = "r";
    public static final String WRITE = "w";
    public static final String READWRITE = "w+";
    public static final String APPEND = "a";
    public static final String ROOTNAME = "/";

    private Superblock superblock;
    private Directory directory;
    private FileTable filetable;

    public FileSystem(int diskBlocks)
    {
        // create superblock, and format disk with 64 inodes in default
        superblock = new Superblock(diskBlocks);

        // create directory, and register "/" in the directory entry 0
        directory = new Directory(superblock.totalInodes);

        // create file table, and store directory in the file table
        filetable = new FileTable(directory);

        // directory reconstruction
        FileTableEntry dirEnt = open("/", READ);
        if (dirSize > 0)
        {
            byte[] dirData = new byte[dirSize];
            read(dirEnt, dirData);
            directory.bytes2directory(dirData);
        }
        close(dirEnt);
    }


    public void sync()
    {
        FileTableEntry dirEnt = open("/", WRITE);
        byte[] dirData = directory.directory2bytes();
        write(dirEnt, dirData);
        superblock.sync();
        close(ftEnt);
    }

    public boolean format(int files)
    {
        return false;
    }

    public FileTableEntry open(String filename, String mode)
    {
        if (!mode.equals(READ) || !mode.equals(WRITE) || !mode.equals(READWRITE) || !mode.equals(APPEND))
        {
            return null;
        }
        else
        {
            FileTableEntry anEntry = filetable.falloc(filename, mode);
            // anEntry will be null if filename is not in there
            
            if (anEntry == null && mode.equals(READ))
                return anEntry;
            
            synchronized(anEntry)
            {

                if (mode.equals(APPEND))
                {
                    seek(anEntry, 0, SEEK_END);
                }

                else if (mode.equals(READ) || mode.equals(READWRITE))
                {
                    seek(anEntry, 0, SEEK_SET);
                }

                else
                {
                    seek(anEntry, 0, SEEK_SET);
                    deallocAllBlocks(anEntry);
                }
            
                return anEntry;

            }
        }
    }
    

    public boolean close(FileTableEntry ftEnt)
    {
        return false;
    }

    public int fsizes(FileTableEntry ftEnt)
    {
        return -1;
    }

    public int read(FileTableEntry ftEnt, byte[] buffer)
    {
        if (ftEnt.inode.flag != Inode.FLAG_READ)
            return -1;

        
        int iterator = 0;

        synchronized(ftEnt)
        {
            byte[] bytes = new byte[Disk.blockSize];
            

            int bufferlength = buffer.length;
            int bytesRead = 0;
            
            int fileLeft = ftEnt.inode.length - ftEnt.seekPtr;
            
            if (fileLeft > buffer.length)
            {
                fileLeft = buffer.length;
            }

            int fullBlocks = fileLeft / Disk.blockSize;
            int partialBlock = fileLeft % Disk.blockSize;
            short block = -1;

            for (int i = fullBlocks; i > 0; i--)
            {
                block = ftEnt.inode.findTargetBlock(ftEnt.seekPtr);
                if (block == (short)-1)
                {
                    // cannot find block
                    break;
                }

                SysLib.rawread(block, bytes);
                System.arraycopy(bytes, 0, buffer, ftEnt.seekPtr, Disk.blockSize);


                ftEnt.seekPtr += Disk.blockSize;
                bytesRead += Disk.blockSize;
            }
            if (block != (short)-1)
            {
                SysLib.rawread(block, bytes);
                System.arraycopy(bytes, 0, buffer, ftEnt.seekPtr, partialBlock);
                bytesRead += partialBlock;
            }
            seek(ftEnt, 0, SEEK_SET);
            return bytesRead;
            
        }
     
    }

    public int write(FileTableEntry ftEnt, byte[] buffer)
    {
        if (ftEnt.inode.flag != Inode.FLAG_WRITE)
            return -1;

        synchronized(ftEnt)
        {
            int bytesWritten = 0;
            block = ftEnt.inode.findTargetBlock(ftEnt.seekPtr);

        }
    }

    private boolean deallocAllBlocks(FileTableEntry ftEnt)
    {
        if (ftEnt == null)
            return false;

        if (ftEnt.inode.count > 1)
            return false;

        for (int i = 0; i < Inode.DIRECT_SIZE; i++)
        {
            if (ftEnt.inode.direct[i] != -1)
            {
                superblock.returnBlock(i);
                ftEnt.inode.direct[i] = -1;
            }
        }

        ftEnt.inode.freeBlock();
        ftEnt.inode.count = 0;
        ftEnt.inode.toDisk(ftEnt.iNumber);
        return true;
        
    }

    public boolean delete(String filename)
    {
        return false;
    }


    public final int SEEK_SET = 0;
    public final int SEEK_CUR = 1;
    public final int SEEK_END = 2;

    public int seek(FileTableEntry ftEnt, int offset, int whence)
    {
        return -1;
    }



}