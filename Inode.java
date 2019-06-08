



public class Inode
{
    public static final int INODE_SIZE = 32;
    public static final int DIRECT_SIZE = 11;
    public static final int FLAG_UNUSED = 0;
    public static final int FLAG_USED = 1;
    public static final int FLAG_READ = 2;
    public static final int FLAG_WRITE = 3;
    public static final int FLAG_DELETE = 4;
    public static final int ERROR = -1;
    public static final int SUCCESS = 0;
    private static final int READ = 0;
    private static final int WRITE = 1;
    private static final short NULL_POINTER = -1;
    private static final short UNREGISTERED  = -1;

    
    public int length;  // file size in bytes
    public short count; // # file-table entires pointing to this
    public short flag;  // 0 = unused, 1 = used
    public short direct[] = new short[DIRECT_SIZE];  // direct pointers
    public short indirect;


    /**
     * Default Inode Constructor.
     */
    public Inode()
    {
        this.direct = new short[DIRECT_SIZE];
        this.length = 0;
        this.count = 0;
        this.flag = FLAG_USED;
        for (int i = 0; i < DIRECT_SIZE; i++)
        {
            this.direct[i] = NULL_POINTER;
        }
        this.indirect = UNREGISTERED;
    }

    /**
     * Inode constructor. Creates an Inode from an existing block on the disk.
     * @param iNumber - The inode to read from the disk.
     */
    public Inode(short iNumber)
    {
        this.toFromDisk(iNumber, READ);
    }


    /**
     * Writes the inode to the disk at the position indicated by the iNumber.
     * @param iNumber - The position on the disk to write the Inode to.
     */
    void toDisk(short iNumber)
    {
        this.toFromDisk(iNumber, WRITE); 
    }


    /**
     * Gets the index block number of this Inode.
     * @return - The index block number of this Inode.
     */
    short getIndirectBlockNumber()
    {
        return this.indirect;
    }


    /**
     * Sets this Inode's index block number to the given value, and writes it
     * the the disk in that position.
     * @param indirectBlockNumber - The value to set this Inode's index block
     *                           number to.
     * @return True if the indirectBlockNumber was set successfully. Returns false
     * if any of the block's inodes are unregistered or if the block itself is
     * unregistered.
     */
    boolean setIndirectBlock(short indirectBlockNumber)
    {
        for (int i = 0; i < DIRECT_SIZE; i++)
        {
            if (this.direct[i] == NULL_POINTER)
            {
                return false;
            }
        }
        if (this.indirect != UNREGISTERED)
        {
            return false;
        }
        this.indirect = indirectBlockNumber;

        byte[] block = new byte[Disk.blockSize];
        for (int i = 0; i < Disk.blockSize / 2; i++)
        {
            SysLib.short2bytes((short)(-1), block, i * 2);
        }

        SysLib.rawwrite((int)indirect, block);
        return true;
    }


    /**
     * Finds the block at the given offset and returns its block number.
     * @param offset - The offset of the position of the block on the disk.
     * @return The block number at the position on the disk of the given offset. 
     */
    short findTargetBlock(int offset)
    {
        int blockNumber = offset / Disk.blockSize;
        if (blockNumber < DIRECT_SIZE)
        {
            return this.direct[blockNumber];
        }
        if (this.indirect == UNREGISTERED)
        {
            return -1;
        }
        byte[] block = new byte[Disk.blockSize];
        SysLib.rawread((int)this.indirect, block);
        return SysLib.bytes2short(block, (blockNumber - DIRECT_SIZE) * 2);
    }


    /**
     * Sets the data in the block at the given blockNumber to the inode found at
     * the given offset.
     * @param offset - The Inode number to set the block data to.
     * @param blockNumber - The block to set the Inode of.
     * @return {@value #SUCCESS} if the block is registered successfully,
     *         returns {@value #ERROR} otherwise.
     */
    int registerBlock(int offset, short blockNumber)
    {
        int blockPosition = offset / Disk.blockSize;
        if (blockPosition < DIRECT_SIZE)
        {
            if (this.direct[blockPosition] != NULL_POINTER)
            {
                return ERROR;
            }
            if (blockPosition > 0 && this.direct[blockPosition - 1] == NULL_POINTER)
            {
                return ERROR;
            }

            this.direct[blockPosition] = blockNumber;
            return SUCCESS;
        }
        else
        {
            if (this.indirect == UNREGISTERED)
            {
                this.indirect = blockNumber;
            }

            byte[] block = new byte[Disk.blockSize];
            SysLib.rawread((int)this.indirect, block);

            // block's location in the indirect block
            int blockLocation = (blockPosition - DIRECT_SIZE) * 2;
            if (SysLib.bytes2short(block, blockLocation) < 0)  // > 0?
            {
                return ERROR;
            }

            SysLib.short2bytes(blockNumber, block, blockLocation);
            SysLib.rawwrite((int)this.indirect, block);
            return SUCCESS;
        }
    }


    /**
     * Frees a block, returning the data within the block and unregistering the
     * block.
     * @return The byte array of data from the block. Returns null if the block
     *         is unregistered.
     */
    public byte[] freeBlock()
    {
        if (this.indirect != UNREGISTERED)
        {
            byte[] block = new byte[Disk.blockSize];
            SysLib.rawread((int)this.indirect, block);
            this.indirect = UNREGISTERED;
            return block;
        }
        return null;
    }


    /**
     * A helper method to either set this Inode's data using data read from
     * existing Inode data on the disk, or to write this Inode's data to the
     * Inode at the given iNumber on the disk.
     * @param iNumber The Inode position on the disk to interact with.
     * @param diskInteraction The type of interaction with the disk. 
     *                        {@link #READ} if reading, {@link #WRITE} if
     *                        writing.
     */
    private void toFromDisk(int iNumber, int diskInteraction)
    {
        byte[] block = new byte[Disk.blockSize];
        if(diskInteraction == Inode.READ)
        {
            this.direct = new short[DIRECT_SIZE];
            int blockNumber = 1 + iNumber / 16;
            SysLib.rawread(blockNumber, block);
        }
        int offset = iNumber % 16 * INODE_SIZE;
        this.length = SysLib.bytes2int(block, offset);
        offset += 4;
        this.count = SysLib.bytes2short(block, offset);
        offset += 2;
        this.flag = SysLib.bytes2short(block, offset);
        offset += 2;
        for (int i = 0; i < DIRECT_SIZE; i++)
        {
            this.direct[i] = SysLib.bytes2short(block, offset);
            offset += 2;
        }
        this.indirect = SysLib.bytes2short(block, offset);
        offset += 2;
        if(diskInteraction == Inode.WRITE)
        {
            offset = 1 + iNumber / 16;
            final byte[] newBlock = new byte[Disk.blockSize];
            SysLib.rawread(offset, newBlock);
            System.arraycopy(block, 0, newBlock, iNumber % 16 * INODE_SIZE, 
                INODE_SIZE);
            SysLib.rawwrite(offset, newBlock);
        }
        
    }




}