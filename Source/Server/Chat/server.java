package Chat;

import java.rmi.*;
import java.rmi.registry.*;

public class server
{

    public static void main(String[] args)
    {
        String ipAddress = "127.0.0.1";
        try
        {
            ipAddress = args[0];
        }
        catch (IndexOutOfBoundsException e)
        {
            System.out.println("No IP given, using localhost");
        }
        try
        {          
            System.setProperty("java.rmi.server.hostname", ipAddress);
            serverImpl stub = new serverImpl(ipAddress);
            Naming.rebind("rmi://" + ipAddress + ":1099/123", stub);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}