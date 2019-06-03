public class Nag extends Thread
{
    private String word;

    public Nag(String[] args)
    {
        word = args[0];
    }

    public void run()
    {
        // prints 'word' 10 times, sleeping for 1000
        // milliseconds between each print.

        for (int rep = 1; rep <= 10; ++rep)
        {
            SysLib.cout(this.word + " ");
            SysLib.sleep(1000);
        }
        SysLib.cout("\n");
        SysLib.exit();
    }
}
