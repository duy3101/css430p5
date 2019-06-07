



public class Directory
{
    private static int maxChars = 30;   // max characters of each filename

    private int fsize[];    // each element stores a different file size
    private char fnames[][];    // each element storess a different file name

    public Directory(int maxInumber)
    {
        fsize = new int[maxInumber];
        for (int i = 0; i < maxInumber; i++)
        {
            fsize[i] = 0;
        }
        fnames = new char[maxInumber][maxChars];
        String root = "/";
        fsize[0] = root.length();
        root.getChars(0, fsize[0], fnames[0], 0);
    }

    public int bytes2directory(byte data[])
    {
        // assumes data[] recieved directory information from disk
        // initializes the Directory instance with this data[]
        return -1;
    }

    public byte[] directory2bytes()
    {
        // converts and return Directory information into a plain byte array
        // this byte array will be written back to Disk
        // note only meaning directory information should be converted to byte
        return byte[];
    }

}