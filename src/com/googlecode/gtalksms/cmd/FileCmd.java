package com.googlecode.gtalksms.cmd;

import java.io.File;
import java.io.FileFilter;

import org.jivesoftware.smackx.filetransfer.FileTransfer;
import org.jivesoftware.smackx.filetransfer.FileTransferManager;
import org.jivesoftware.smackx.filetransfer.OutgoingFileTransfer;

import android.util.Log;

import com.googlecode.gtalksms.MainService;
import com.googlecode.gtalksms.XmppManager;
import com.googlecode.gtalksms.tools.Tools;
import com.googlecode.gtalksms.xmpp.XmppFileManager;
import com.googlecode.gtalksms.xmpp.XmppMsg;

public class FileCmd extends Command {
    private XmppManager xmppMgr;
    private File landingDir;
    private File sendDir;  // where the files come from if send:filename is given

    
    public FileCmd(MainService mainService) {
        super(mainService, new String[] {"send", "ls"}, Command.TYPE_SYSTEM);
        xmppMgr = _mainService.getXmppmanager();
        landingDir = xmppMgr.getXmppFileMgr().getLandingDir();
        sendDir = landingDir;
    }
    
    @Override
    protected void execute(String cmd, String args) {
        if (cmd.equals("send")) {
            sendFile(args);
        } else if (cmd.equals("ls")) {
            ls(args);
        }
    }
    
    @Override
    public String[] help() {
        return null;
    }
    
    private void sendFile(String args) {
        if (args.equals(""))
            return;        
    
        File file;
        if (args.startsWith("/")) {
            file = new File(args);
        } else {
            file = new File(sendDir, args);
        }
        
        if (file.exists()) {
            sendFile(file);
        } else {
            send("File '" + file.getAbsolutePath() + "' doesn't exist!");  // TODO localization
        }
    }
    
    private void sendFile(File file) {
        FileTransferManager fileTransferManager = xmppMgr.getXmppFileMgr().getFileTransferManager();
        OutgoingFileTransfer transfer = fileTransferManager.createOutgoingFileTransfer(_answerTo);

        try {
            transfer.sendFile(file, "Sending you: " + file.getAbsolutePath() + " to: " + _answerTo);
            send("File transfer: " + file.getAbsolutePath() + " - " + transfer.getFileSize() / 1024 + " KB");
            
            while (!transfer.isDone()) {
                if (transfer.getStatus() == FileTransfer.Status.refused) {
                    send("Could not send the file. Refused by peer.");
                    return;
                }
                if (transfer.getStatus() == FileTransfer.Status.error) {
                    send(XmppFileManager.returnAndLogError(transfer));
                    return;
               }
               Thread.sleep(1000);
            }
        } catch (Exception ex) {
            String message = "Cannot send the file because an error occured during the process." 
                + Tools.LineSep + ex.getMessage();
            Log.e(Tools.LOG_TAG, message, ex);
            send(message);
        }
    }
    
    private void ls(String args) {
        if (args.equals("")) {
            listDir(landingDir);
        } else if (args.startsWith("/")) {
            File dir = new File(args);
            listDir(dir);
        } else if (args.startsWith("./")) {  // emulate the cwd with help of sendDir
            File dir = new File(sendDir, args.substring(1));
            listDir(dir);
        }
    }
    
    private void listDir(File dir) {
        if (dir.isDirectory()) {
            sendDir = dir;
            XmppMsg res = new XmppMsg();
            File[] dirs = dir.listFiles(new FileCmd.DirFileFilter());
            File[] files = dir.listFiles(new FileCmd.FileFileFilter());

            if (dirs.length != 0) {
                res.appendBoldLine("Directories within " + dir.getAbsolutePath());
                for (File d : dirs) {
                    res.appendLine(d.getName() + "/");
                }
            }
            if (files.length != 0) {
                res.appendBoldLine("Files within " + dir.getAbsolutePath());
                for (File f : files) {
                    appendFileInfo(res, f);
                }
            }
            send(res);
        } else {
            send(dir.getAbsolutePath() + " is not a direcotry");
        }      
    }
    
    private static void appendFileInfo(XmppMsg msg, File f) {
        String name = f.getName();
        long size = f.length(); // the size of the file in bytes
        if (size > 1023) {
            msg.appendLine(name + " - " + size / 1024 + " KiB");
        } else {
            msg.appendLine(name + " - " + size + " Bytes");  
        }
    }
    
    public class DirFileFilter implements FileFilter {
        @Override
        public boolean accept(File pathname) {
            return pathname.isDirectory();
        }      
    }
    
    public class FileFileFilter implements FileFilter {
        @Override
        public boolean accept(File pathname) {
            return pathname.isFile();
        }      
    }

}
