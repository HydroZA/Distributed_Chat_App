package Chat;

import java.rmi.*;
import java.rmi.server.*;
import java.sql.*;
import java.util.ArrayList;
import java.io.*;
import java.util.*;
import java.net.*;
import java.util.StringTokenizer;
import java.text.SimpleDateFormat;
import java.util.Date;

public class serverImpl extends UnicastRemoteObject implements serverInterface
{

    public static Vector<ClientHandler> ar = new Vector<>();
    private ServerSocket ss;
    private Socket s;
    static int clients = 0;
    public PrintWriter log;
    private Connection con;
    private String ipAddress;
    
    serverImpl(String ipAddress) throws RemoteException
    {
        super();
        
        this.ipAddress = ipAddress;
        
        // Connect to the SQLite Database
        try
        {
            Class.forName("org.sqlite.JDBC");
            con = DriverManager.getConnection(
                "jdbc:sqlite:chat.sqlite");
        }
        catch (Exception e)
        {
            System.out.println("Failed to connect to the database. Is the file \'chat.sqlite\' present?");
            System.exit(0);
        }
        
        // Create log folder
        File logsFolder = new File("./logs");
        if (logsFolder.mkdir())
        {
            System.out.println("Created logs folder");
        }

        // Create PrintWriter to write output to a log file
        try
        {
            // Set the log file name to the current time and date
            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy-HH-mm-ss");
            Date date = new Date();
            String filename = "logs/" + sdf.format(date) + ".log";
            System.out.println("Created log file: " + filename);

            log = new PrintWriter(filename, "UTF-8");

            log.println(java.time.LocalTime.now().toString() + ": Server initialized");
        }
        catch (IOException e)
        {
            System.out.println("Failed to initialize log file");
            e.printStackTrace();
        }

        try
        {
            // server is listening on port 1234 
            InetAddress ip = InetAddress.getByName(ipAddress);
            ss = new ServerSocket(1234, 50, ip);
            log.println(java.time.LocalTime.now().toString() + ": Started server socket, listening on port 1234");

            Thread MessageServer = new Thread(new Runnable()
            {
                @Override
                public void run()
                {
                    while (true)
                    {
                        try
                        {
                            // Create new socket per client
                            s = ss.accept();
                            System.out.println("New client request received : " + s);
                            log.println(java.time.LocalTime.now().toString() + ": New Client request received : " + s);

                            // Dedicate a data input and output stream to the connected client
                            DataInputStream dis = new DataInputStream(s.getInputStream());
                            DataOutputStream dos = new DataOutputStream(s.getOutputStream());

                            System.out.println("Creating a new handler for this client...");
                            String username = dis.readUTF();

                            // Initialize the ClientHandler for the connected client
                            ClientHandler mtch = new ClientHandler(s, username, dis, dos);

                            // Run the ClientHandler in a new thread
                            Thread t = new Thread(mtch);

                            System.out.println("Adding " + username + " to active client list");
                            log.println(java.time.LocalTime.now().toString() + ": Adding " + username + " to active client list");

                            ar.add(mtch);

                            t.start();

                            clients++;
                        }

                        catch (Exception e)
                        {
                            System.out.println("Server Shutdown");
                            break;
                        }
                    }
                }
            });
            MessageServer.start();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        Runtime.getRuntime().addShutdownHook(new Thread()
        {
            public void run()
            {
                try
                {
                    String query = "UPDATE USERS SET loggedin=0";
                    PreparedStatement stmt = con.prepareStatement(query);

                    stmt.execute();
                    log.println(java.time.LocalTime.now().toString() + ": All users successfully logged out");
                    System.out.println("All users logged out");

                    stmt.close();
                    con.close();

                    log.println(java.time.LocalTime.now().toString() + ": Server Shutdown");
                    log.close();
                    ss.close();
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        });
    }

    private static String bytesToHex(byte[] hash)
    {
        // Method used to convert a byte array password hash to a string format
        StringBuffer hexString = new StringBuffer();
        for (int i = 0; i < hash.length; i++)
        {
            String hex = Integer.toHexString(0xff & hash[i]);
            if (hex.length() == 1)
            {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public boolean login(String username, byte[] passwordBytes)
    {
        // Method called when a user attempts to log in 

        // Convert the received password into a string format
        String password = bytesToHex(passwordBytes);
        password = password.toUpperCase();

        try
        {
            String query = "SELECT 1 FROM USERS WHERE EXISTS (SELECT * FROM USERS WHERE username=\'" + username + "\' AND pword=\'" + password + "\');";
            PreparedStatement stmt = con.prepareStatement(query);

            ResultSet rs = stmt.executeQuery();

            if (rs.getInt(1) == 1)
            {
                // Requested user exists
                // find out if user already logged in
                query = "SELECT loggedin FROM USERS WHERE username=\'" + username + "\'";
                stmt = con.prepareStatement(query);
                ResultSet rs2 = stmt.executeQuery();

                if (rs2.getInt(1) == 0)
                {
                    // User not logged in 
                    rs2.close();

                    query = "UPDATE USERS SET loggedin=1 WHERE username=\'" + username + "\'";
                    stmt = con.prepareStatement(query);
                    stmt.execute();

                    stmt.close();
                    rs.close();
                    return true;
                }
                else
                {
                    // user is already logged in
                    rs.close();
                    return false;
                }
            }
            else
            {
                rs.close();
                return false;
            }

        }
        catch (Exception e)
        {
            e.printStackTrace();
            return false;
        }
    }

    public void logout(String username)
    {
        try
        {
            // Update the database
            String query = "UPDATE USERS SET loggedin=0 WHERE username=\'" + username + "\'";
            PreparedStatement stmt = con.prepareStatement(query);

            stmt.execute();
            stmt.close();

            // Reduce the count of connected clients
            clients--;

            // Remove the client from the vector
            for (ClientHandler mc : ar)
            {
                String name = mc.getName();
                if (name.equals(username))
                {
                    ar.remove(mc);
                    System.out.println("REMOVED: " + name + " from the vector");
                    log.println(java.time.LocalTime.now().toString() + "REMOVED: " + name + " from the vector");
                    break;
                }
            }
            log.println(java.time.LocalTime.now().toString() + ": " + username + " logged out");
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public boolean addFriend(String username, String friendName)
    {
        try
        {
            int userid;
            String query = "SELECT id FROM USERS WHERE username=\'" + username + "\'";
            PreparedStatement stmt5 = con.prepareStatement(query);

            ResultSet rs4 = stmt5.executeQuery();
            //rs4.first();
            userid = rs4.getInt(1);

            query = "SELECT EXISTS ( SELECT 1 FROM USERS WHERE username=\'" + friendName + "\')";
            PreparedStatement stmt = con.prepareStatement(query);

            ResultSet rs = stmt.executeQuery();

            if (rs.getInt(1) == 1)
            {
                System.out.println("Requested friend exists, checking if users are already friends");

                //get userid from friend
                query = "SELECT id FROM USERS WHERE username=\'" + friendName + "\'";
                PreparedStatement stmt2 = con.prepareStatement(query);

                ResultSet rs2 = stmt2.executeQuery();

                int friendid = rs2.getInt(1);

                stmt2.close();
                rs2.close();

                // Check if users are already friends
                query = "SELECT EXISTS (SELECT 1 FROM friends WHERE userid=" + userid + " AND friendid=" + friendid + ")";
                PreparedStatement stmt3 = con.prepareStatement(query);

                ResultSet rs3 = stmt3.executeQuery();

                if (rs3.getInt(1) == 1)
                {
                    System.out.println("Users are already friends");

                    stmt3.close();
                    rs3.close();
                    return false;
                }
                else
                {
                    // create friend link
                    query = "INSERT INTO friends (userid, friendid) VALUES (" + userid + ", " + friendid + ")";
                    PreparedStatement stmt4 = con.prepareStatement(query);

                    System.out.println("FRIENDID=" + friendid + "\nUSERID=" + userid);

                    stmt4.execute();

                    stmt4.close();

                    log.println(java.time.LocalTime.now().toString() + ": Created friend link between " + username + " and " + friendName);

                    return true;
                }
            }
            else
            {
                System.out.println("Requested friend not found");
                log.println(java.time.LocalTime.now().toString() + ": Failed to create friend link between " + username + " and " + friendName);

                return false;
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            log.println(java.time.LocalTime.now().toString() + ": Failed to create friend link between " + username + " and " + friendName);
            return false;
        }
    }

    public boolean addUser(String username, byte[] pwHash)
    {
        String password = bytesToHex(pwHash);
        password = password.toUpperCase();
        try
        {
            String query = "SELECT EXISTS ( SELECT 1 FROM USERS WHERE username=\'" + username + "\')";
            PreparedStatement stmt = con.prepareStatement(query);

            //find if username already exists
            ResultSet rs = stmt.executeQuery();

            if (rs.getInt(1) == 0)
            {
                // Username does not exist, add user to db
                rs.close();
                stmt.close();

                query = "INSERT INTO USERS (username, pword) VALUES (\'" + username + "\', \'" + password + "\')";
                PreparedStatement stmt2 = con.prepareStatement(query);

                stmt2.execute();

                stmt2.close();
                log.println(java.time.LocalTime.now().toString() + ": Created new user: " + username);
                return true;
            }
            else
            {
                // Username already exists
                log.println(java.time.LocalTime.now().toString() + ": Failed to create new user using name: " + username);
                return false;
            }
        }
        catch (Exception e)
        {
            log.println(java.time.LocalTime.now().toString() + ": Failed to create new user using name: " + username);
            e.printStackTrace();
            return false;
        }
    }

    public ArrayList getFriends(String username)
    {
        try
        {
            String query = "SELECT id FROM USERS WHERE username=\'" + username + "\'";
            PreparedStatement stmt = con.prepareStatement(query);

            ResultSet rs = stmt.executeQuery();
            int userid = rs.getInt(1);
            stmt.close();
            rs.close();
            query = "SELECT friendid FROM friends WHERE userid=\'" + userid + "\'";
            PreparedStatement stmt2 = con.prepareStatement(query);

            ResultSet rs2 = stmt2.executeQuery();

            ArrayList<Integer> friendIDs = new ArrayList<Integer>();

            while (rs2.next())
            {
                friendIDs.add(rs2.getInt(1));
            }

            int rowCount = friendIDs.size();

            rs2.close();
            stmt2.close();

            ArrayList<String> friends = new ArrayList<String>();

            for (int i = 0; i < rowCount; i++)
            {
                query = "SELECT username FROM USERS WHERE id=\'" + friendIDs.get(i) + "\'";
                PreparedStatement stmt3 = con.prepareStatement(query);

                ResultSet rs3 = stmt3.executeQuery();
                friends.add(rs3.getString(1));
            }
            
            log.println(java.time.LocalTime.now().toString() + ": Retrieved friends for: " + username);
            return friends;
        }
        catch (Exception e)
        {
            e.printStackTrace();
            log.println(java.time.LocalTime.now().toString() + ": Failed to retrieve friends for: " + username);
            return null;
        }
    }
}