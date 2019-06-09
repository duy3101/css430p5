



public class FileSystem
{

    public static final String READ = "r";
    public static final String WRITE = "w";
    public static final String READWRITE = "w+";
    public static final String APPEND = "a";
    public static final String ROOT_NAME = "/";

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
        FileTableEntry dirEnt = open(ROOT_NAME, READ);
        int dirSize = fsizes(dirEnt);
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
        FileTableEntry dirEnt = open(ROOT_NAME, WRITE);
        byte[] dirData = directory.directory2bytes();
        this.write(dirEnt, dirData);
        this.close(dirEnt);
        this.superblock.sync();
    }

    public boolean format(int files)
    {
        if(this.filetable.fempty())
        {
            this.superblock.format(files);
            this.directory = new Directory(this.superblock.totalInodes);
            this.filetable = new FileTable(this.directory);
            return true;
        }
        return false;
    }

    public FileTableEntry open(String filename, String mode)
    {
       
        FileTableEntry anEntry = filetable.falloc(filename, mode);
        SysLib.cout("ftEnt.iNumber: " + anEntry.iNumber);
        // anEntry will be null if filename is not in there
        
        if (anEntry == null && mode.equals(READ))
        {
            return anEntry;
        }
            
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
    

    public boolean close(FileTableEntry ftEnt)
    {
        synchronized(ftEnt)
        {
            ftEnt.count--;
            if(ftEnt.count > 0)
            {
                return true;
            }
        }
        return this.filetable.ffree(ftEnt);
    }

    public int fsizes(FileTableEntry ftEnt)
    {
        synchronized(ftEnt)
        {
            return ftEnt.inode.length;
        }
    }

    public int read(FileTableEntry ftEnt, byte[] buffer)
    {
        if (ftEnt.inode.flag == Inode.FLAG_WRITE)
            return -1;
        
        // System.out.println("count : " + ftEnt.count);
        // System.out.println("fte inumber : " + ftEnt.iNumber);
        // System.out.println("fte seek : " + ftEnt.seekPtr);
        // System.out.println("fte direct[0] : " + ftEnt.inode.direct[0]);
        // System.out.println("fte inode length : " + ftEnt.inode.length);

        synchronized(ftEnt)
        {
            int seekPosition = ftEnt.seekPtr;
            int startPosition = seekPosition % 512;
            int remainingStartBlock = Disk.blockSize - startPosition;
            byte[] bytes = new byte[Disk.blockSize];
            

            int bytesRead = 0;
            boolean willReadFullFirstBlock = true;
            if (remainingStartBlock > buffer.length)
            {
                willReadFullFirstBlock = false;
                remainingStartBlock = buffer.length;
            }
            
            short block = ftEnt.inode.findTargetBlock(ftEnt.seekPtr);
            if (block != (short)-1)
            {
                SysLib.rawread(block, bytes);
                System.arraycopy(bytes, startPosition, buffer, 0, remainingStartBlock);
                bytesRead += remainingStartBlock;
                //ftEnt.seekPtr += remainingStartBlock;
            }
            


            int fileLeft = ftEnt.inode.length - (bytesRead + seekPosition);
            if (fileLeft > buffer.length)
            {
                fileLeft = buffer.length;
            }

            int fullBlocks = fileLeft / Disk.blockSize;
            int partialBlock = fileLeft % Disk.blockSize;
            //System.out.println("fullblocks = " + fullBlocks + " partialblocks = " + partialBlock);
           




            for (int i = fullBlocks; i > 0; i--)
            {
                block = ftEnt.inode.findTargetBlock(ftEnt.seekPtr);
                if (block == (short)-1)
                {
                    // cannot find block
                    break;
                }

                SysLib.rawread(block, bytes);
                System.arraycopy(bytes, 0, buffer, bytesRead, Disk.blockSize);


                //ftEnt.seekPtr += Disk.blockSize;
                bytesRead += Disk.blockSize;
            }


            if (partialBlock > 0 && willReadFullFirstBlock)
            {
                block = ftEnt.inode.findTargetBlock(ftEnt.seekPtr);
                SysLib.rawread(block, bytes);

                System.out.println(" partialblocks = " + partialBlock);
                System.out.println(" bytesread = " + bytesRead);
                System.arraycopy(bytes, 0, buffer, bytesRead, partialBlock);

                //ftEnt.seekPtr += Disk.blockSize;
                bytesRead += partialBlock;
            }

            seek(ftEnt, 0, SEEK_SET);
            return bytesRead;
            
        }
     
    }

    public int write(FileTableEntry ftEnt, byte[] buffer)
    {
        if (ftEnt.inode.flag == Inode.FLAG_READ)
            return -1;
        
        int bytesWritten = 0;

        // if (ftEnt.mode == WRITE || ftEnt.mode == READWRITE)
        // {
        //     deallocAllBlocks(ftEnt);
        // }

        if (ftEnt.mode != APPEND)  // if mode is WRITE or READWRITE
        {


            byte[] bytes = new byte[Disk.blockSize];

            synchronized(ftEnt)
            {   
                int seekPosition = ftEnt.seekPtr;
                int startPosition = seekPosition % 512;
                int remainingStartBlock = Disk.blockSize - startPosition;                

                boolean willWriteFullFirstBlock = true;
                if (remainingStartBlock > buffer.length)
                {
                    willWriteFullFirstBlock = false;
                    remainingStartBlock = buffer.length;
                }
                
                short block = -1;
                System.out.println("setBlock outside calling");
                block = setBlock(block, ftEnt);
                
                if (block != (short)-1)
                {
                    System.arraycopy(buffer, 0, bytes, startPosition, remainingStartBlock);
                    SysLib.rawwrite(block, bytes);
                    bytesWritten += remainingStartBlock;

                    ftEnt.seekPtr += bytesWritten;
                    ftEnt.inode.length += remainingStartBlock;
                }
                
    
    
                int fileLeft = buffer.length - ftEnt.inode.length;
   
                int fullBlocks = fileLeft / Disk.blockSize;
                int partialBlock = fileLeft % Disk.blockSize;

                System.out.println("fileleft: " + fileLeft);
                System.out.println("fullBs: " + fullBlocks);
                System.out.println("partial: " + partialBlock);

                System.out.println("seek before loop: " + ftEnt.seekPtr);


                for (int i = fullBlocks; i > 0; i--)
                {
                    System.out.println("setBlock inside calling");
                    block = setBlock(block, ftEnt);
                    System.out.println();

                    if (block < Disk.blockSize && block != (short)-1)  // block found and within disk block range
                    {
                        System.arraycopy(buffer, bytesWritten, bytes, 0, Disk.blockSize);
                        SysLib.rawwrite(block, bytes);

                        ftEnt.inode.length += Disk.blockSize;

                        ftEnt.seekPtr += Disk.blockSize;
                        bytesWritten += Disk.blockSize;
                    }
                    else
                    {
                        break;
                    }
                }
                if (partialBlock > 0 && willWriteFullFirstBlock)
                {
                    block = setBlock(block, ftEnt);

                    System.arraycopy(buffer, bytesWritten, bytes, 0, partialBlock);
                    SysLib.rawwrite(block, bytes);

                    ftEnt.inode.length += partialBlock;
                    bytesWritten += partialBlock;
                    ftEnt.seekPtr +=  bytesWritten;
                }

            }
            
            seek(ftEnt, 0, SEEK_SET);
            ftEnt.inode.toDisk(ftEnt.iNumber);
            return bytesWritten;
        }
        else  // if mode is APPEND
        {
            byte[] bytes = new byte[Disk.blockSize];
            
            // how much original file spilled into its last block
            int startBlockOffset = ftEnt.inode.length % Disk.blockSize;
            System.out.println("ftEnt.inode.length: " + ftEnt.inode.length);
            
            // how much space original file's last block still has
            int remainingStartBlock = Disk.blockSize - startBlockOffset;
            
            // if buffer is smaller than what is left in the block where original file ended 
            // (i.e. < disk.BlockSize), set remaining amount to buffer length
            boolean willFillFirstBlock = true;
            if (remainingStartBlock > buffer.length)
            {
                willFillFirstBlock = false;
                remainingStartBlock = buffer.length;
            }
            
            // (original file size + what we are appending) % (Disk.blocksize) gives partial size of last block
            int partialEndBlock = (buffer.length - remainingStartBlock) % Disk.blockSize;
            
            // "trims" beginning partial amount and ending partial amount, divides by blockSize to get full blocks
            int fullBlocks = (buffer.length - remainingStartBlock) / Disk.blockSize;
            

            synchronized(ftEnt)
            {
                short block = -1;
                block = setBlock(block, ftEnt);
                if (block < Disk.blockSize && block != (short)-1)  // block found and within disk block range
                {
                    // // keep the part of block where original file ended
                    SysLib.rawread(block, bytes);
                    
                    // arraycopy from the buffer, beginning at the offset where the original file ended
                    // the length of what is being copied is however much free space is left in the block
                    // UNLESS buffer is less than that remaining block space, in which case just copy all of buffer.
                    System.arraycopy(buffer, 0, bytes, startBlockOffset, remainingStartBlock);
                    System.out.println("RemainStartBlock: " + remainingStartBlock);
                    System.out.println("SBOFfset:" + startBlockOffset);
                    SysLib.rawwrite(block, bytes);

                    ftEnt.inode.length += remainingStartBlock;
                    ftEnt.seekPtr += remainingStartBlock;
                    bytesWritten += remainingStartBlock;
                }
                for (int i = fullBlocks; i > 0; i--)
                {
                    block = setBlock(block, ftEnt);
                    if (block < Disk.blockSize && block != (short)-1)
                    {
                        System.arraycopy(buffer, bytesWritten, bytes, 0, Disk.blockSize);
                        SysLib.rawwrite(block, bytes);

                        ftEnt.inode.length += Disk.blockSize;
                        ftEnt.seekPtr += Disk.blockSize;
                        bytesWritten += Disk.blockSize;
                    }
                    else
                    {
                        break;
                    }
                }
                  
                if (willFillFirstBlock && partialEndBlock > 0)
                {
                    SysLib.cout("End part");
                    block = setBlock(block, ftEnt);
                    if (block < Disk.blockSize && block != (short)-1)
                    {
                        System.arraycopy(buffer, bytesWritten, bytes, 0, buffer.length - bytesWritten);
                        SysLib.rawwrite(block, bytes);

                        ftEnt.inode.length += partialEndBlock;
                        bytesWritten += partialEndBlock;
                        ftEnt.seekPtr += bytesWritten;

                    }
                }
            }
        }
        ftEnt.inode.toDisk(ftEnt.iNumber);
        return bytesWritten;
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
                superblock.returnBlock(ftEnt.inode.direct[i]);
                ftEnt.inode.direct[i] = -1;
            }
        }
        
        ftEnt.inode.freeBlock();
        
        ftEnt.inode.length = 0;
        ftEnt.inode.count = 0;
        ftEnt.inode.flag = Inode.FLAG_UNUSED;
        ftEnt.inode.toDisk(ftEnt.iNumber);
        
        return true;
        
    }

    public boolean delete(String filename)
    {
        FileTableEntry ftEnt = this.open(filename, WRITE);
        return this.close(ftEnt) && this.directory.ifree(ftEnt.iNumber);
    }





    public final int SEEK_SET = 0;
    public final int SEEK_CUR = 1;
    public final int SEEK_END = 2;

    public int seek(FileTableEntry ftEnt, int offset, int whence)
    {
        synchronized(ftEnt)
        {
            int filelength = ftEnt.inode.length;
            
            switch(whence)
            {

                case SEEK_SET:
                    // the file's seek pointer is set to offset bytes from the beginning of the file
                    ftEnt.seekPtr = offset;
                    break;

                case SEEK_CUR:
                    // the file's seek pointer is set to its current value plus the offset. 
                    ftEnt.seekPtr += offset;

                    // bound check
                    if (ftEnt.seekPtr < 0)
                    {
                        ftEnt.seekPtr = 0;
                    }
                    else if (ftEnt.seekPtr > filelength)
                    {
                        ftEnt.seekPtr = filelength;
                    }
                    break;
                case SEEK_END:
                    // the file's seek pointer is set to the size of the file plus the offset. 
                    ftEnt.seekPtr = ftEnt.inode.length + offset;

                    // bound check
                    if (ftEnt.seekPtr < 0)
                    {
                        ftEnt.seekPtr = 0;
                    }
                    else if (ftEnt.seekPtr > filelength)
                    {
                        ftEnt.seekPtr = filelength;
                    }
                    break;


                default:
                    // shouldn't go in here
                    return -1;

                
            }
            return ftEnt.seekPtr;

        }
    }

    public short setBlock(short block, FileTableEntry ftEnt)
    {
        block = ftEnt.inode.findTargetBlock(ftEnt.seekPtr);
        if (block == -1)
        {

            short freeBlock = (short)superblock.getFreeBlock();
            ftEnt.inode.registerBlock(ftEnt.seekPtr, freeBlock);
            //System.out.println("offset " + ftEnt.seekPtr);
            return freeBlock;
        }
        else if (block == -2)
        {
            short indirectBlock = (short)superblock.getFreeBlock();
            ftEnt.inode.setIndirectBlock(indirectBlock);

            short freeBlock = (short)superblock.getFreeBlock();
            ftEnt.inode.registerBlock(ftEnt.seekPtr, freeBlock);
            System.out.println("offsetCHECK " + ftEnt.seekPtr);
            return freeBlock;
        }

        return block;
    }
}