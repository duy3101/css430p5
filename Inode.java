



public class Inode
{
    public final static int iNodeSize = 12;     // fix to 32 bytes
    public final static int directSize = 11;    // # direct pointers
    
    public int length;  // file size in bytes
    public short count; // # file-table entires pointing to this
    public short flag;  // 0 = unused, 1 = used
    public short direct[] = new short[directSize];  // direct pointers
    public short indirect;

    public Inode()
    {
        this.length = 0;
        this.count = 0;
        this.flag = 1;
        for (int i = 0; i < directSize; i++)
        {
            this.direct[i] = -1;
        }
        this.indirect = -1;
    }

    public Inode(short iNumber)
    {
        // retrieving inode from disk
    }

    int toDisk(short iNumber)
    {
        // save to disk as the i-th inode
        return -1;
    }

    short getIndexBlockNumber()
    {
        return -1;
    }

    boolean setIndexBlock(short indexBlockNumber)
    {
        return false;
    }

    short findTargetBlock(int offset)
    {
        return -1;
    }
}