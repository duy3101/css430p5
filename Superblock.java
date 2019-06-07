import sun.security.x509.DistributionPoint;

public class Superblock
{
    private final int defaultInodeBlocks = 64;
    public int totalBlocks; // the number of disk blocks
    public int totalInodes; // the number of inodes
    public int freeList; // the block number of the freeList's head


    public Superblock(int diskSize)
    {
        byte[] bytes = new byte[Disk.blockSize];

        SysLib.rawread(0, bytes);

        totalBlocks = SysLib.bytes2int(bytes, 0);    
        totalInodes = SysLib.bytes2int(bytes, 4);    
        freeList = SysLib.bytes2int(bytes, 8);

        if (bytes == diskSize && totalInodes > 0 && freeList >=2)
        {
            // disk contents are valid
            return; 
        }
        else
        {
            // need to format disk
            totalBlocks = diskSize;
            SysLiB.format(defaultInodeBlocks);
        } 
    }

    public void sync()
    {
        // write back totalBlocks, inodeBlocks, and freeList
        byte[] bytes = new byte[Disk.blockSize];
        SysLib.rawread(0, bytes);

        SysLib.int2bytes(totalBlocks, bytes, 0);
        SysLib.int2bytes(totalInodes, bytes, 4);
        SysLib.int2bytes(freeList, bytes, 8);


        SysLib.rawwrite(0, bytes);

        SysLib.sync();

    }

    public int getFreeBlock()
    {
        byte[] bytes = new byte[Disk.blockSize];
        SysLib.rawread(freeList, bytes);
        //freeBlock = SysLib.bytes2int(bytes, offset)
        
        return -1;
    }

    public int returnBlock(int blockNumber)
    {
        // enqueue a given block to the end of the freeList
    }

}