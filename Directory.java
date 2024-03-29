// File:  Directory.java
// Group: Marc Skaarup, Dewey Nguyen, Jake Stewart
// Class: CSS430
//
// Build for ThreadOS: javac *.java
//                     java Boot
//                     l Test5


/**
 * A Directory class manage the name of all files
 * and allocate/deallocate file in the Directory
 */
public class Directory
{
    private static int maxChars = 30;   // max characters of each filename
    private int fsizes[];               // each element stores a different file size
    private char fnames[][];            // each element storess a different file name


    /**
     * A Directory constructor
     * @param maxInumber the number of totalInodes
     * @return none
     */
    public Directory(int maxInumber)
    {
        fsizes = new int[maxInumber];
        for (int i = 0; i < maxInumber; i++)
        {
            fsizes[i] = 0;
        }
        fnames = new char[maxInumber][maxChars];
        String root = "/";
        fsizes[0] = root.length();
        root.getChars(0, fsizes[0], fnames[0], 0);
    }

    /**
     * assumes data[] recieved directory information from disk
     * initializes the Directory instance with this data[]
     * @param data byte[]
     * @return none
     */
    public void bytes2directory(byte data[])
    {
        // assumes data[] recieved directory information from disk
        // initializes the Directory instance with this data[]
        int offset = 0;
        for (int i = 0; i < fsizes.length; i++, offset+=4)
        {
            fsizes[i] = SysLib.bytes2int(data, offset);
        }

        for (int i = 0; i < fnames.length; i++, offset+= maxChars * 2)
        {
            String fname = new String(data, offset, maxChars * 2);
            fname.getChars(0, fsizes[i], fnames[i], 0);
        }
    }

    /**
     * converts and return Directory information into a plain byte array
     * this byte array will be written back to Disk
     * note only meaning directory information should be converted to byte
     * @return byte[]
     */
    public byte[] directory2bytes()
    {
        byte[] newBlock = new byte[(fsizes.length * 4 + fnames.length * maxChars * 2) + 1];
        int offset = 0;
        for (int i = 0; i < fsizes.length; i++, offset+=4)
        {
            SysLib.int2bytes(i, newBlock, offset);
        }
        for (int i = 0; i < fnames.length; i++, offset+= maxChars * 2)
        {
            for (int j = 0; j < fnames[i].length; j++)
            {
                newBlock[offset + j] = (byte)fnames[i][j];
            }
        }
        return newBlock;
    }

    /**
     * finds the next open spot on the directory
     * adds the given filename into the directory
     * @param filename String
     * @return -1 if directory is full
     */
    public short ialloc(String filename)
    {
        for (int i = 1; i < fsizes.length; i++)
        {
            if (fsizes[i] == 0)
            {
                int size;
                char[] name;
                if(filename.length() > maxChars)
                {
                    size = maxChars;
                    name = filename.substring(0, maxChars).toCharArray();
                }
                else
                {
                    size = filename.length();
                    name = filename.toCharArray();
                }
                fsizes[i] = size;
                fnames[i] = name;
                return (short)i;
            }
        }

        return (short)-1;
    }

    /**
     * sets the fsize to 0 to indicate a free directory space
     * @param iNumber short
     * @return true if freed
     */
    public boolean ifree(short iNumber)
    {
        if (fsizes[iNumber] > 0)
        {
            fsizes[iNumber] = 0; 
            return true;
        }
        return false;
    }

    /**
     * finds a file in the directory by checking if the input
     * is the same length, and then if it has the same characters
     * @param filename String
     * @return -1 if the file was not found
     */
    public short namei(String filename)
    {
        // finds a file in the directory by checking if the input
        // is the same length, and then if it has the same characters
        // returns -1 if the file was not found
        char[] chars = filename.toCharArray();

        fnamesloop:
        for (int i = 0; i < fnames.length; i++)
        {

            if (fsizes[i] != chars.length)
            {
                continue;
            }

            for (int j = 0; j < fnames[i].length; j++)
            {
                if (fnames[i][j] != chars[j])
                {
                    continue fnamesloop;
                }
            }
            return (short)i;
        }
        return (short)-1;
    }
}