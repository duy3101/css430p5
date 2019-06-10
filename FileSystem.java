// File:  FileSystem.java
// Group: Marc Skaarup, Dewey Nguyen, Jake Stewart
// Class: CSS430
//
// Build for ThreadOS: javac *.java
//                     java Boot
//                     l Test5



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
        // open the directory entry and write the directory information
        // to the disk, then close the entry and sync the superblock
        FileTableEntry dirEnt = open(ROOT_NAME, WRITE);
        byte[] dirData = directory.directory2bytes();
        this.write(dirEnt, dirData);
        this.close(dirEnt);
        this.superblock.sync();
    }

    public boolean format(int files)
    {
        // format the disk
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
        // create a file table entry with the filename and given mode
        FileTableEntry anEntry = filetable.falloc(filename, mode);

        // anEntry will be null if filename is not in there
        
        // if trying to read a file that doesn't exist, don't create a file
        if (anEntry == null && mode.equals(READ))
        {
            return anEntry;
        }
            
        // set the seekPtr to the appropriate place in the file
        synchronized(anEntry)
        {
            // seekPtr to end of file for append
            if (mode.equals(APPEND))
            {
                seek(anEntry, 0, SEEK_END);
            }
            
            // seekPtr to beginning of file for read and readwrite
            else if (mode.equals(READ) || mode.equals(READWRITE))
            {
                seek(anEntry, 0, SEEK_SET);
            }
            
            // seekPtr to beginning of file for write and deallocate all blocks
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
        // reduces the count of the ftEnt count
        synchronized(ftEnt)
        {
            ftEnt.count--;
            if(ftEnt.count > 0)
            {
                return true;
            }
        }
        // if count == 0 (no more ftEnt of this type), free it from filetable
        return this.filetable.ffree(ftEnt);
    }

    public int fsizes(FileTableEntry ftEnt)
    {
        // returns the size of the file
        synchronized(ftEnt)
        {
            return ftEnt.inode.length;
        }
    }

    public int read(FileTableEntry ftEnt, byte[] buffer)
    {
        // fills the given buffer with the file data from ftEnt
        
        if (ftEnt.inode.flag == Inode.FLAG_WRITE)
            return -1;

        synchronized(ftEnt)
        {
            int seekPosition = ftEnt.seekPtr;  // where seekPtr starts at
            int startPosition = seekPosition % 512;  // start position in the block
            
            // how much space is left in the block, given the startPosition
            int remainingStartBlock = Disk.blockSize - startPosition;
            byte[] bytes = new byte[Disk.blockSize];
            

            int bytesRead = 0;
            boolean willReadFullFirstBlock = true;
            if (remainingStartBlock > buffer.length)
            {
                // keep arraycopy from copying too much data (maxes out at buffer.length)
                willReadFullFirstBlock = false;
                remainingStartBlock = buffer.length;
            }
            
            short block = ftEnt.inode.findTargetBlock(ftEnt.seekPtr);
            if (block != (short)-1)
            {
                // read from the first block
                SysLib.rawread(block, bytes);
                System.arraycopy(bytes, startPosition, buffer, 0, remainingStartBlock);
                bytesRead += remainingStartBlock;
                ftEnt.seekPtr += remainingStartBlock;
            }
            

            // find out how much of the file is left
            int fileLeft = ftEnt.inode.length - (bytesRead + seekPosition);
            if (fileLeft > buffer.length)
            {
                fileLeft = buffer.length;
            }

            int fullBlocks = fileLeft / Disk.blockSize;
            int partialBlock = fileLeft % Disk.blockSize;  // how much data the last block contains

            for (int i = fullBlocks; i > 0; i--)
            {
                // find next block to read data from
                block = ftEnt.inode.findTargetBlock(ftEnt.seekPtr);
                if (block == (short)-1)
                {
                    // cannot find block
                    break;
                }
                
                // read data from disk and copy into the buffer
                SysLib.rawread(block, bytes);
                System.arraycopy(bytes, 0, buffer, bytesRead, Disk.blockSize);


                ftEnt.seekPtr += Disk.blockSize;
                bytesRead += Disk.blockSize;
            }

            // if the first block was read and there is a partial block amount to read
            if (partialBlock > 0 && willReadFullFirstBlock)
            {
                block = ftEnt.inode.findTargetBlock(ftEnt.seekPtr);
                SysLib.rawread(block, bytes);

                System.arraycopy(bytes, 0, buffer, bytesRead, partialBlock);

                ftEnt.seekPtr += Disk.blockSize;
                bytesRead += partialBlock;
            }

            seek(ftEnt, 0, SEEK_SET);
            return bytesRead;
        }
    }

    public int write(FileTableEntry ftEnt, byte[] buffer)
    {
        // writes data from the buffer to the disk
        
        if (ftEnt.inode.flag == Inode.FLAG_READ)
            return -1;
        
        int bytesWritten = 0;

        if (ftEnt.mode != APPEND)  // if mode is WRITE or READWRITE
        {
            byte[] bytes = new byte[Disk.blockSize];

            synchronized(ftEnt)
            {   
                int seekPosition = ftEnt.seekPtr;
                int startPosition = seekPosition % 512;
                
                // how much space is left in the block, given the startPosition
                int remainingStartBlock = Disk.blockSize - startPosition;                

                boolean willWriteFullFirstBlock = true;
                if (remainingStartBlock > buffer.length)
                {
                    willWriteFullFirstBlock = false;
                    remainingStartBlock = buffer.length;
                }
                
                short block = -1;

                block = setBlock(block, ftEnt);
                
                if (block != (short)-1)
                {
                    // write to the first block
                    System.arraycopy(buffer, 0, bytes, startPosition, remainingStartBlock);
                    SysLib.rawwrite(block, bytes);
                    bytesWritten += remainingStartBlock;

                    ftEnt.seekPtr += bytesWritten;
                    
                    // update the inode's filelength
                    ftEnt.inode.length += remainingStartBlock;
                }
                
    
                // find out how much data is left in the buffer
                int fileLeft = buffer.length - ftEnt.inode.length;
   
                int fullBlocks = fileLeft / Disk.blockSize;
                int partialBlock = fileLeft % Disk.blockSize;


                for (int i = fullBlocks; i > 0; i--)
                {
                    // register a freeblock to the inode to be written to
                    block = setBlock(block, ftEnt);

                    if (block < Disk.blockSize && block != (short)-1)  // block found and within disk block range
                    {
                        // write blockSize amount from buffer to byte array to be written to disk
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
                    // if there is still data to fill part of a block, write the rest of the buffer
                    block = setBlock(block, ftEnt);

                    System.arraycopy(buffer, bytesWritten, bytes, 0, partialBlock);
                    SysLib.rawwrite(block, bytes);

                    ftEnt.inode.length += partialBlock;
                    bytesWritten += partialBlock;
                    ftEnt.seekPtr +=  bytesWritten;
                }

            }
            
            seek(ftEnt, 0, SEEK_SET);
            
            // write the updated inode to disk
            ftEnt.inode.toDisk(ftEnt.iNumber);
            return bytesWritten;
        }

        
        else  // if mode is APPEND
        {
            byte[] bytes = new byte[Disk.blockSize];
            
            // how much original file spilled into its last block
            int startBlockOffset = ftEnt.inode.length % Disk.blockSize;
            
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

                    SysLib.rawwrite(block, bytes);

                    ftEnt.inode.length += remainingStartBlock;
                    ftEnt.seekPtr += remainingStartBlock;
                    bytesWritten += remainingStartBlock;
                }
                for (int i = fullBlocks; i > 0; i--)
                {
                    // register a free block and write a full blockSize amount
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
                    // write the rest of the buffer to the last block
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
        // write the updated inode to the disk
        ftEnt.inode.toDisk(ftEnt.iNumber);
        return bytesWritten;
    }

    private boolean deallocAllBlocks(FileTableEntry ftEnt)
    {
        // deallocates all of the blocks in a fileTableEntry's file
        // and saves the updated inode to the disk
        
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
        // deletes a file
        FileTableEntry ftEnt = this.open(filename, WRITE);
        return this.close(ftEnt) && this.directory.ifree(ftEnt.iNumber);
    }

    public final int SEEK_SET = 0;
    public final int SEEK_CUR = 1;
    public final int SEEK_END = 2;

    public int seek(FileTableEntry ftEnt, int offset, int whence)
    {
        // moves the seekPtr to beginning, current position, or end
        // each option can contain an offset
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
        // search for the given block
        block = ftEnt.inode.findTargetBlock(ftEnt.seekPtr);
        
        // if the block has not been registered, register it to a free block
        if (block == -1)
        {
            // will register the block to ftEnt.inode's next available direct slot
            short freeBlock = (short)superblock.getFreeBlock();
            ftEnt.inode.registerBlock(ftEnt.seekPtr, freeBlock);
            return freeBlock;
        }
        else if (block == -2)
        {
            // once ftEnt.inode's direct fills up, initializes the indirect block
            // and registers the block to an indirect slot
            short indirectBlock = (short)superblock.getFreeBlock();
            ftEnt.inode.setIndirectBlock(indirectBlock);

            short freeBlock = (short)superblock.getFreeBlock();
            ftEnt.inode.registerBlock(ftEnt.seekPtr, freeBlock);
            return freeBlock;
        }

        return block;
    }
}