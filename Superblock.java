/**
 * A class to keep track of the total blocks and Inodes that are used by the
 * FileSystem. It also keeps a pointer to a list of free blocks, and offers a
 * few useful functions for interacting with the inode blocks, such as 
 * {@link #getFreeBlock() getFreeBlock} and 
 * {@link #returnBlock(int) returnBlock}.
 */

public class Superblock
{
    public int totalBlocks; // the number of disk blocks
    public int totalInodes; // the number of inodes
    public int freeList; // the block number of the freeList's head

    private static final int DEFAULT_INODE_BLOCK_COUNT = 64;
    private static final int BLOCK_COUNT_POSITION = 0;
    private static final int INODE_COUNT_POSITION = 4;
    private static final int FREE_LIST_POSITION = 8;
 


    /**
     * SuperBlock Constructor. Initializes the SuperBlock using the given
     * diskSize.
     * @param diskSize - The number of Inodes to create on the disk.
     */
    public Superblock(int diskSize)
    {
        byte[] bytes = new byte[Disk.blockSize];

        SysLib.rawread(0, bytes);

        this.totalBlocks = SysLib.bytes2int(bytes, BLOCK_COUNT_POSITION);    
        this.totalInodes = SysLib.bytes2int(bytes, INODE_COUNT_POSITION);    
        this.freeList = SysLib.bytes2int(bytes, FREE_LIST_POSITION);

        if (totalBlocks == diskSize && totalInodes > 0 && freeList >=2)
        {
            return; 
        }
        else
        {
            totalBlocks = diskSize;
            format(DEFAULT_INODE_BLOCK_COUNT);
        } 
    }


    /**
     * Writes the data stored in this SuperBlock to the disk.
     */
    public void sync()
    {
        byte[] block = new byte[Disk.blockSize];
        SysLib.int2bytes(this.totalBlocks, block, BLOCK_COUNT_POSITION);
        SysLib.int2bytes(this.totalInodes, block, INODE_COUNT_POSITION);
        SysLib.int2bytes(this.freeList, block, FREE_LIST_POSITION);
        SysLib.rawwrite(0, block);
    }


    /**
     * Gets the next free block.
     * @return The block number of the next available free block. -1 
     *         if there are no free blocks available.
     */
    public int getFreeBlock()
    {
        int freeBlock = this.freeList;
        if (freeBlock != -1) {
            byte[] newBlock = new byte[Disk.blockSize];
            SysLib.rawread(freeBlock, newBlock);
            this.freeList = SysLib.bytes2int(newBlock, 0);
            // SysLib.int2bytes(0, newBlock, 0);
            // SysLib.rawwrite(freeBlock, newBlock);
        }
        return freeBlock;
    }


    /**
     * Returns the block with the given block number to the free list.
     * @param blockNumber - The block number of the block to return to the 
     *                      free list.
     * @return True if the return was successful, false otherwise.
     */
    public boolean returnBlock(int blockNumber)
    {
        if (this.freeList >= 0) 
        {
            byte[] block = new byte[Disk.blockSize];
            for (int i = 0; i < Disk.blockSize; i++)
            {
                block[i] = 0;
            }
            SysLib.int2bytes(this.freeList, block, 0);
            SysLib.rawwrite(freeList, block);
            this.freeList = blockNumber;
            return true;
        }
        return false;
    }


    public void format()
    {
        this.format(DEFAULT_INODE_BLOCK_COUNT);
    }


    public void format(int totalInodes)
    {
        for(int i = 0; i < totalInodes; i++)
        {
            Inode inode = new Inode();
            inode.flag = Inode.FLAG_UNUSED;
            inode.toDisk((short)i);
        }

        this.totalInodes = totalInodes;
        freeList = this.totalInodes * (32/Disk.blockSize) + 2;

        for(int i = freeList; i < this.totalBlocks; i++)
        {
            byte[] block = new byte[Disk.blockSize];

            SysLib.int2bytes(i + 1, block, 0);
            SysLib.rawwrite(i, block);
        }
        this.sync();
    }
}