



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

        seek(ftEnt, 0, SEEK_SET);
        
        System.out.println("count : " + ftEnt.count);
        System.out.println("fte inumber : " + ftEnt.iNumber);
        System.out.println("fte seek : " + ftEnt.seekPtr);
        System.out.println("fte direct[0] : " + ftEnt.inode.direct[0]);
        System.out.println("fte inode length : " + ftEnt.inode.length);

        synchronized(ftEnt)
        {
            byte[] bytes = new byte[Disk.blockSize];
            

            int bytesRead = 0;
            
            int fileLeft = ftEnt.inode.length - ftEnt.seekPtr;
            if (fileLeft > buffer.length)
            {
                fileLeft = buffer.length;
            }

            int fullBlocks = fileLeft / Disk.blockSize;
            int partialBlock = fileLeft % Disk.blockSize;
            //System.out.println("fullblocks = " + fullBlocks + " partialblocks = " + partialBlock);
            short block = ftEnt.inode.findTargetBlock(ftEnt.seekPtr);

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
        if (ftEnt.inode.flag == Inode.FLAG_READ)
            return -1;
        
        int bytesWritten = 0;

        if (ftEnt.mode != APPEND)  // if mode is WRITE or READWRITE
        {
            seek(ftEnt, 0, SEEK_SET);  // start at the beginning of the file
            deallocAllBlocks(ftEnt);   // overwrite all data in file

            byte[] bytes = new byte[Disk.blockSize];

            int fullBlocks = buffer.length / Disk.blockSize;
            int partialBlock = buffer.length % Disk.blockSize;

            synchronized(ftEnt)
            {   
                short block = ftEnt.inode.findTargetBlock(ftEnt.seekPtr);
                for (int i = fullBlocks; i > 0; i--)
                {
                    block = ftEnt.inode.findTargetBlock(ftEnt.seekPtr);
                    if (block < Disk.blockSize && block != (short)-1)  // block found and within disk block range
                    {
                        System.arraycopy(buffer, ftEnt.seekPtr, bytes, 0, Disk.blockSize);
                        SysLib.rawwrite(block, bytes);

                        System.out.println("CHECKBE " + ftEnt.inode.length);
                        ftEnt.inode.length += Disk.blockSize;
                        System.out.println("CHECKAF " + ftEnt.inode.length);

                        ftEnt.seekPtr += Disk.blockSize;
                        bytesWritten += Disk.blockSize;
                
                        ftEnt.inode.registerBlock(ftEnt.seekPtr, (short)superblock.getFreeBlock());
                    }
                    else
                    {
                        break;
                    }
                }
                if (block < Disk.blockSize)
                {
                    System.arraycopy(buffer, ftEnt.seekPtr, bytes, 0, partialBlock);
                    SysLib.rawwrite(block, bytes);

                    System.out.println("CHECKBE2 " + ftEnt.inode.length);
                    ftEnt.inode.length += partialBlock;
                    System.out.println("CHECKAF2 " + ftEnt.inode.length);

                    bytesWritten += partialBlock;

                    ftEnt.inode.registerBlock(ftEnt.seekPtr, (short)superblock.getFreeBlock());
                }
            }
            
            seek(ftEnt, 0, SEEK_SET);
            ftEnt.inode.toDisk(ftEnt.iNumber);
            return bytesWritten;
        }
        else  // if mode is APPEND
        {
            seek(ftEnt, 0, SEEK_END);  // go to the end of the file

            byte[] bytes = new byte[Disk.blockSize];
            
            // how much original file spilled into its last block
            int startBlockOffset = ftEnt.inode.length % Disk.blockSize;
            
            // how much space original file's last block still has
            int remainingStartBlock = Disk.blockSize - startBlockOffset;
            
            // if buffer is smaller than what is left in the block where original file ended 
            // (i.e. < disk.BlockSize), set remaining amount to buffer length
            if (remainingStartBlock > buffer.length)
            {
                remainingStartBlock = buffer.length;
            }
            
            // (original file size + what we are appending) % (Disk.blocksize) gives partial size of last block
            int partialEndBlock = (buffer.length + ftEnt.inode.length) % Disk.blockSize;
            
            // "trims" beginning partial amount and ending partial amount, divides by blockSize to get full blocks
            int fullBlocks = (buffer.length - (remainingStartBlock + partialEndBlock)) / Disk.blockSize;
            

            synchronized(ftEnt)
            {
                short block = ftEnt.inode.findTargetBlock(ftEnt.seekPtr);
                if (block < Disk.blockSize && block != (short)-1)  // block found and within disk block range
                {
                    // keep the part of block where original file ended
                    SysLib.rawread(block, bytes);
                    
                    // arraycopy from the buffer, beginning at the offset where the original file ended
                    // the length of what is being copied is however much free space is left in the block
                    // UNLESS buffer is less than that remaining block space, in which case just copy all of buffer.
                    System.arraycopy(buffer, 0, bytes, startBlockOffset, remainingStartBlock);
                    SysLib.rawwrite(block, bytes);

                    ftEnt.inode.length += remainingStartBlock;
                    ftEnt.seekPtr += remainingStartBlock;
                    bytesWritten += remainingStartBlock;
                }
                for (int i = fullBlocks; i > 0; i--)
                {
                    block = ftEnt.inode.findTargetBlock(ftEnt.seekPtr);
                    if (block < Disk.blockSize && block != (short)-1)
                    {
                        System.arraycopy(buffer, ftEnt.seekPtr, bytes, 0, Disk.blockSize);
                        SysLib.rawwrite(block, bytes);

                        ftEnt.inode.length += Disk.blockSize;
                        ftEnt.seekPtr += Disk.blockSize;
                        bytesWritten += Disk.blockSize;

                        ftEnt.inode.registerBlock(ftEnt.seekPtr, (short)superblock.getFreeBlock());
                    }
                    else
                    {
                        break;
                    }
                }
                if (block < Disk.blockSize && block != (short)-1)
                {
                    System.arraycopy(buffer, ftEnt.seekPtr, bytes, 0, partialEndBlock);
                    SysLib.rawwrite(block, bytes);

                    ftEnt.inode.length += partialEndBlock;
                    bytesWritten += partialEndBlock;

                    ftEnt.inode.registerBlock(ftEnt.seekPtr, (short)superblock.getFreeBlock());
                }
            }
        }
        
        seek(ftEnt, 0, SEEK_SET);
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
        ftEnt.inode.count = 0;
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



}