



public class FileSystem
{
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
        FileTableEntry dirEnt = open("/", "r");
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

    }

    public boolean format(int files)
    {
        return false;
    }

    public FileTableEntry open(String filename, String mode)
    {
        return FileTableEntry;
    }

    public boolean close(FileTableEntry ftEnt)
    {
        return false;
    }

    public int fsize(FileTableEntry ftEnt)
    {
        return -1;
    }

    public int read(FileTableEntry ftEnt, byte[] buffer)
    {
        return -1;
    }

    public int write(FileTableEntry ftEnt, byte[] buffer)
    {
        return -1;
    }

    private boolean deallocAllBlocks(FileTableEntry ftEnt)
    {
        return false;
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