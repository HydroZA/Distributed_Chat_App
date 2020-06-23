/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Chat;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.rmi.RemoteException;
import java.util.ArrayList;
import javax.swing.DefaultListModel;
import javax.swing.JOptionPane;
import java.net.InetAddress;
import java.net.*;
import java.io.*;
import java.util.StringTokenizer;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.JFileChooser;

/**
 *
 * @author james
 */
public class ChatUI extends javax.swing.JFrame
{

    private serverInterface stub;
    private String username;
    private Socket s;
    private DataInputStream dis;
    private DataOutputStream dos;
    private PrintWriter log;
    private String ipAddress;
    
    public ChatUI(String username, serverInterface stub, String ipAddress)
    {
        this.ipAddress = ipAddress;
        
        // Create folders folder in jar location
        File logsFolder = new File("./logs");
        File attachementsFolder = new File ("./attachments");
        boolean bool = logsFolder.mkdir();
        boolean bool2 = attachementsFolder.mkdir();
        if (bool && bool2)
        {
            System.out.println("Folders Created");
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

            log.println(java.time.LocalTime.now().toString() + ": Logged in as " + username);
        }
        catch (IOException e)
        {
            System.out.println("Failed to initialize log file");
            e.printStackTrace();
        }

        // Inherit serverInterface and username from login form
        this.stub = stub;
        this.username = username;

        Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
        this.setLocation(dim.width / 2 - this.getSize().width / 2, dim.height / 2 - this.getSize().height / 2);

        ArrayList<String> friends = new ArrayList<>();
        try
        {
            friends = stub.getFriends(username);
            log.println(java.time.LocalTime.now().toString() + ": Retrieved friends from database");

        }
        catch (RemoteException e)
        {
            e.printStackTrace();
        }
        if (friends == null)
        {
            initComponents();
            this.getRootPane().setDefaultButton(btnSendMessage); // Send message on enter press
            lstFriends.setModel(new DefaultListModel()); // Set list to blank
            lblFriendSelected.setText("Add a friend to begin chatting");
        }
        else
        {
            String[] friendArr = friends.toArray(new String[friends.size()]);
            initComponents();
            this.getRootPane().setDefaultButton(btnSendMessage); // Send message on enter press
            lstFriends.setListData(friendArr);
        }
        
        // Connect to chat server
        try
        {
            InetAddress ip = InetAddress.getByName(ipAddress);
            s = new Socket(ip, 1234);
            dis = new DataInputStream(s.getInputStream());
            dos = new DataOutputStream(s.getOutputStream());

            dos.writeUTF(username);
            System.out.println("Connected to Chat Server");

            log.println(java.time.LocalTime.now().toString() + ": Connected to chat server");

            // Create thread to receive incoming messages 
            Thread readMessage = new Thread(new Runnable()
            {
                @Override
                public void run()
                {
                    while (true)
                    {
                        try
                        {
                            // Get type of content received
                            String type = dis.readUTF();

                            if (type.equalsIgnoreCase("message"))
                            {
                                // read the message sent to this client 
                                String msg = dis.readUTF();

                                // Split the message into sender and message parts
                                StringTokenizer st = new StringTokenizer(msg, " : ");
                                String messageSender = st.nextToken();
                                String activeChatPartner = lstFriends.getSelectedValue();

                                // Only show the message if it's from the active chat partner
                                if (messageSender.equalsIgnoreCase(activeChatPartner))
                                {
                                    txaMessages.append(msg);
                                    txaMessages.append("\n");

                                    log.println(java.time.LocalTime.now().toString() + ": Received message: \"" + msg + "\" from \"" + messageSender + "\"");
                                }
                                else
                                {
                                    log.println(java.time.LocalTime.now().toString() + ": Received message: \"" + msg + "\" from \"" + messageSender + "\" but ignored due to different active chat partner");
                                }
                            }
                            else if (type.equalsIgnoreCase("file"))
                            {
                                String fileName = dis.readUTF();
                                fileName = "./attachments/" + fileName;
                                String strFileSize = dis.readUTF();
                                int fileSize = Integer.parseInt(strFileSize);
                                byte[] fileBytes = new byte[fileSize];

                                //Receive the bytes
                                dis.readFully(fileBytes, 0, fileSize);

                                try
                                {
                                    // Write the received file to disk
                                    FileOutputStream fos = new FileOutputStream(fileName);
                                    fos.write(fileBytes);
                                    fos.close();
                                    
                                    txaMessages.append(lstFriends.getSelectedValue() + " sent you a file: \"" + fileName + "\"\n");
                                    log.println(java.time.LocalTime.now().toString() + ": Received file: \"" + fileName + "\" from " + lstFriends.getSelectedValue());

                                    fos.close();
                                }
                                catch (IOException e)
                                {
                                    System.out.println("Error Occured While Receiving a File");
                                    log.println(java.time.LocalTime.now().toString() + ": Error occured while receiving a file");
                                }
                            }
                        }
                        catch (Exception e)
                        {
                            e.printStackTrace();
                            System.out.println("read message thread closed");
                            break;
                        }
                    }
                }
            });
            readMessage.start();
            log.println(java.time.LocalTime.now().toString() + ": Started readMessage Thread");

        }
        catch (Exception e)
        {
            System.out.println("Exception while connecting to chat server");
        }

        this.setTitle("Logged in as: " + username);

        // Create shutdown hook to negotiate disconnect from servers and close log file
        Runtime.getRuntime().addShutdownHook(new Thread()
        {
            public void run()
            {
                try
                {
                    // Log out from chat server
                    dos.writeUTF("message");
                    dos.writeUTF("logout");
                    log.println(java.time.LocalTime.now().toString() + ": Logged out from Chat Server");

                    // Log out from Database Server
                    stub.logout(username);
                    log.println(java.time.LocalTime.now().toString() + ": Logged out from Database Server");
                    log.close();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents()
    {

        btnAddFriend = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        lstFriends = new javax.swing.JList<>();
        txfMessage = new javax.swing.JTextField();
        btnSendMessage = new javax.swing.JButton();
        jScrollPane2 = new javax.swing.JScrollPane();
        txaMessages = new javax.swing.JTextArea();
        lblFriendSelected = new javax.swing.JLabel();
        btnSendAttachment = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        btnAddFriend.setText("Add Friend");
        btnAddFriend.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btnAddFriendActionPerformed(evt);
            }
        });

        lstFriends.setModel(new javax.swing.AbstractListModel<String>()
        {
            String[] strings = { "Item 1", "Item 2", "Item 3", "Item 4", "Item 5" };
            public int getSize() { return strings.length; }
            public String getElementAt(int i) { return strings[i]; }
        });
        lstFriends.addListSelectionListener(new javax.swing.event.ListSelectionListener()
        {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt)
            {
                lstFriendsValueChanged(evt);
            }
        });
        jScrollPane1.setViewportView(lstFriends);

        btnSendMessage.setText("Send");
        btnSendMessage.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btnSendMessageActionPerformed(evt);
            }
        });

        txaMessages.setEditable(false);
        txaMessages.setColumns(20);
        txaMessages.setRows(5);
        jScrollPane2.setViewportView(txaMessages);

        lblFriendSelected.setFont(new java.awt.Font("Dialog", 1, 24)); // NOI18N
        lblFriendSelected.setText("Select a friend on the left to start chatting!");

        btnSendAttachment.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Chat/attachmentIcon.png"))); // NOI18N
        btnSendAttachment.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                btnSendAttachmentActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(btnAddFriend, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jScrollPane1))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(txfMessage)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnSendMessage, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnSendAttachment, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jScrollPane2)
                    .addComponent(lblFriendSelected, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addGap(7, 7, 7)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(btnAddFriend)
                    .addComponent(lblFriendSelected))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 370, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(btnSendAttachment, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(txfMessage, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(btnSendMessage)))
                        .addGap(6, 6, 6))
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 411, Short.MAX_VALUE))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents
    private static byte[] readFileToByteArray(File file)
    {
        // This method is used to return a byte array of a selected file
        
        FileInputStream fis = null;
        // Creating a byte array using the length of the file
        // file.length returns long which is cast to int
        byte[] bArray = new byte[(int) file.length()];
        try
        {
            fis = new FileInputStream(file);
            fis.read(bArray);
            fis.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        return bArray;
    }
    private void btnAddFriendActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_btnAddFriendActionPerformed
    {//GEN-HEADEREND:event_btnAddFriendActionPerformed
        String friendName = JOptionPane.showInputDialog("Friend Name:");
        boolean success = false;
        try
        {
            success = stub.addFriend(username, friendName);
        }
        catch (RemoteException e)
        {
            e.printStackTrace();
        }

        if (success)
        {
            JOptionPane.showMessageDialog(this, "Successfully added friend");
            log.println(java.time.LocalTime.now().toString() + ": Successfully added friend: " + friendName);

            // Update friend list
            ArrayList<String> friends = new ArrayList<>();
            try
            {
                friends = stub.getFriends(username);
            }
            catch (RemoteException e)
            {
                e.printStackTrace();
            }
            String[] friendArr = friends.toArray(new String[friends.size()]);
            lstFriends.setListData(friendArr);
        }
        else
        {
            JOptionPane.showMessageDialog(this, "Failed to add friend");

            log.println(java.time.LocalTime.now().toString() + ": Failed to add friend: " + friendName);
        }
    }//GEN-LAST:event_btnAddFriendActionPerformed

    private void btnSendMessageActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_btnSendMessageActionPerformed
    {//GEN-HEADEREND:event_btnSendMessageActionPerformed
        String recipient = lstFriends.getSelectedValue();
        String message = txfMessage.getText();
        if (recipient == null)
        {
            return;
        }
        if (message.equals(""))
        {
            return;
        }
        
        String formattedMessage = message + "#" + recipient;

        try
        {
            // Tell the server that we are sending a message
            dos.writeUTF("message");
            // Send the message over with the recipient appended
            dos.writeUTF(formattedMessage);
            // Log the activity
            log.println(java.time.LocalTime.now().toString() + ": Sent: " + message + " to recipient: " + recipient);
        }
        catch (IOException e)
        {
            System.out.println("Failed to send message");
            log.println(java.time.LocalTime.now().toString() + ": An error occured during sending message: \"" + message + "\" to: " + recipient);
            e.printStackTrace();
        }

        // add our message to the chat window and clear the textfield
        txaMessages.append("You : " + message + "\n");
        txfMessage.setText("");
    }//GEN-LAST:event_btnSendMessageActionPerformed

    private void lstFriendsValueChanged(javax.swing.event.ListSelectionEvent evt)//GEN-FIRST:event_lstFriendsValueChanged
    {//GEN-HEADEREND:event_lstFriendsValueChanged
        if (!evt.getValueIsAdjusting()) // Only trigger code on mouse up, rather than on mouse down and up. Otherwise this code will be called twice.
        {
            String selected = lstFriends.getSelectedValue();

            //Capitalize the first letter of the friends name and set to the heading
            lblFriendSelected.setText(selected.substring(0, 1).toUpperCase() + selected.substring(1));

            // Clear the chat when selecting a new user
            txaMessages.setText("");

            // Write to log file
            log.println(java.time.LocalTime.now().toString() + ": Selected friend: " + selected);
        }
    }//GEN-LAST:event_lstFriendsValueChanged

    private void btnSendAttachmentActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_btnSendAttachmentActionPerformed
    {//GEN-HEADEREND:event_btnSendAttachmentActionPerformed
        JFileChooser jfc = new JFileChooser();
        int choice = jfc.showOpenDialog(this); // Open a file browser
        if (choice != JFileChooser.APPROVE_OPTION)
        {
            // The user selected cancel or closed the file browser without selecting a file 
            return;
        }

        File file = jfc.getSelectedFile();
        String fileName = jfc.getName(file);

        try
        {
            // Convert the file to a byte array for network transmission
            byte[] fileBytes = readFileToByteArray(file);
            
            // Tell the server we are sending a file 
            dos.writeUTF("file");
            
            // Tell the server the intended recipient and the name of the file 
            String recipient = lstFriends.getSelectedValue();
            dos.writeUTF(recipient);
            dos.writeUTF(fileName);
            
            // Send the file length so the server knows how many bytes to listen for 
            String fileSize = Long.toString(file.length());
            dos.writeUTF(fileSize);
            
            // Send the file over the network
            dos.write(fileBytes);
            
            txaMessages.append("You sent \"" + fileName + "\" to " + recipient + "\n");
            log.println(java.time.LocalTime.now().toString() + ": User sent file: " + file.getAbsolutePath() + " to " + recipient);
        }
        catch (IOException e)
        {
            System.out.println("File not found at path: " + file.getAbsolutePath());
            log.println("Unable to locate the requested file at path: " + file.getAbsolutePath());
        }
    }//GEN-LAST:event_btnSendAttachmentActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[])
    {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try
        {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels())
            {
                if ("Windows".equals(info.getName()))
                {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        }
        catch (ClassNotFoundException ex)
        {
            java.util.logging.Logger.getLogger(ChatUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        catch (InstantiationException ex)
        {
            java.util.logging.Logger.getLogger(ChatUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        catch (IllegalAccessException ex)
        {
            java.util.logging.Logger.getLogger(ChatUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        catch (javax.swing.UnsupportedLookAndFeelException ex)
        {
            java.util.logging.Logger.getLogger(ChatUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnAddFriend;
    private javax.swing.JButton btnSendAttachment;
    private javax.swing.JButton btnSendMessage;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JLabel lblFriendSelected;
    private javax.swing.JList<String> lstFriends;
    private javax.swing.JTextArea txaMessages;
    private javax.swing.JTextField txfMessage;
    // End of variables declaration//GEN-END:variables
}
