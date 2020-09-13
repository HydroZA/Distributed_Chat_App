package Chat;


public class ClientHandler implements Runnable
{
    private String name;
    final DataInputStream dis;
    final DataOutputStream dos;
    private Socket s;
    private boolean isloggedin;

    public ClientHandler(Socket s, String name, DataInputStream dis, DataOutputStream dos)
    {
        this.dis = dis;
        this.dos = dos;
        this.name = name;
        this.s = s;
        this.isloggedin = true;
    }

    public String getName()
    {
        return name;
    }

    @Override
    public void run()
    {
        String received;
        while (true)
        {
            try
            {
                String type = dis.readUTF();

                if (type.equalsIgnoreCase("message"))
                {
                    // receive the string 
                    received = dis.readUTF();

                    System.out.println(received);

                    if (received.equals("logout"))
                    {
                        this.isloggedin = false;
                        this.s.close();
                        break;
                    }

                    // break the string into message and recipient part 
                    StringTokenizer st = new StringTokenizer(received, "#");
                    String MsgToSend = st.nextToken();
                    String recipient = st.nextToken();

                    // search for the recipient in the connected devices list. 
                    // ar is the vector storing client of active users 
                    for (ClientHandler mc : serverImpl.ar)
                    {
                        if (mc.name.equals(recipient) && mc.isloggedin == true)
                        {
                            mc.dos.writeUTF("message");
                            mc.dos.writeUTF(this.name + " : " + MsgToSend);
                            break;
                        }
                    }
                }
                else if (type.equalsIgnoreCase("file"))
                {
                    String recipient = dis.readUTF();
                    String fileName = dis.readUTF();
                    String strFileSize = dis.readUTF();
                    int fileSize = Integer.parseInt(strFileSize);
                    byte[] fileBytes = new byte[fileSize];

                    dis.readFully(fileBytes, 0, fileSize);

                    for (ClientHandler mc : serverImpl.ar)
                    {
                        if (mc.name.equals(recipient) && mc.isloggedin == true)
                        {
                            mc.dos.writeUTF("file");
                            mc.dos.writeUTF(fileName);
                            mc.dos.writeUTF(strFileSize);
                            mc.dos.write(fileBytes);
                            break;
                        }
                    }
                }
            }
            catch (IOException e)
            {
                System.out.println("Closed Client Handler for " + name);
                break;
            }
        }
        try
        {
            // closing resources 
            this.dis.close();
            this.dos.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }
}
