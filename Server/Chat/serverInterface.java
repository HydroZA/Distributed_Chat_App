/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Chat;

/**
 *
 * @author james
 */
import java.rmi.*;
import java.util.ArrayList;

public interface serverInterface extends Remote
{
    public boolean login(String username, byte[] password) throws RemoteException;

    public boolean addFriend(String username, String friendName) throws RemoteException;

    public boolean addUser(String username, byte[] password) throws RemoteException;

    public ArrayList getFriends(String username) throws RemoteException;

    public void logout(String username) throws RemoteException;
}
