/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cz.cuni.mff.bc.server;

import cz.cuni.mff.bc.api.main.IServer;
import cz.cuni.mff.bc.api.main.Task;
import cz.cuni.mff.bc.api.main.TaskID;
import cz.cuni.mff.bc.api.main.ProjectUID;
import cz.cuni.mff.bc.api.enums.InformMessage;
import cz.cuni.mff.bc.api.main.ProjectInfo;
import cz.cuni.mff.bc.api.main.CustomIO;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import org.cojen.dirmi.Pipe;

/**
 *
 * @author Jakub
 */
public class IServerImpl implements IServer {

    private TaskManager taskManager;
    private final int timerPeriodSec = 11;
    private HashMap<String, ActiveClient> activeClients;
    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(Server.class.getName());

    public IServerImpl(HashMap<String, ActiveClient> activeClients, TaskManager taskManager) {
        this.taskManager = taskManager;
        this.activeClients = activeClients;
    }

    public HashMap<String, ActiveClient> getActiveClients() {
        return activeClients;
    }

    @Override
    public void setClientsMemoryLimit(String clientID, int memory) throws RemoteException {
        activeClients.get(clientID).setMemoryLimit(memory);
        LOG.log(Level.INFO, "Memory limit on client {0} is now set to {1}m", new Object[]{clientID, memory});
    }

    @Override
    public void setClientsCoresLimit(String clientID, int cores) throws RemoteException {
        activeClients.get(clientID).setCoresLimit(cores);
        LOG.log(Level.INFO, "Cores limit on client {0} is now set to {1}", new Object[]{clientID, cores});
    }

    @Override
    public TaskID getTaskIdBeforeCalculation(String clientID) throws RemoteException {
        return taskManager.getTaskIDBeforeCalculation(clientID);
    }

    @Override
    public boolean hasClientTasksInProgress(String clientID) throws RemoteException {
        return taskManager.clientInActiveComputation(clientID);
    }

    @Override
    public Task getTask(String clientID, TaskID taskID) throws RemoteException {
        return taskManager.getTask(clientID, taskID);
    }

    @Override
    public void sendInformMessage(String clientID, InformMessage message) throws RemoteException {
        switch (message) {
            case CALCULATION_STARTED:
                startClientTimer(clientID);
                break;
            case CALCULATION_ENDED:
                stopClientTimer(clientID);
                break;
        }
    }

    @Override
    public boolean isConnected(String clientName) throws RemoteException {
        if (activeClients.containsKey(clientName)) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void saveCompletedTask(String clientID, Task task) throws RemoteException {
        if (!task.hasDataBeenSaved()) {
            task.saveData(FilesStructure.getTaskSavePath(task.getUnicateID()));
            taskManager.addCompletedTask(clientID, task.getUnicateID());
            LOG.log(Level.INFO, "Task saving: Task {0} has been saved", task.getUnicateID());
        }
    }

    @Override
    public Pipe uploadProject(String clientName, String projectName, int priority, int cores, int memory, int time, Pipe pipe) throws RemoteException {
        Project project = taskManager.createPreparingProject(clientName, projectName, priority, cores, memory, time);
        File upDir = CustomIO.createFolder(FilesStructure.getClientUploadedDir(clientName, projectName));
        try {
            File tmp = File.createTempFile(clientName, projectName + ".zip");
            try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(tmp))) {
                int n;
                byte[] buffer = new byte[8192];
                while ((n = pipe.read(buffer)) > -1) {
                    out.write(buffer, 0, n);
                }
                pipe.close();
            }
            CustomIO.extractZipFile(tmp, upDir);

            tmp.delete();
        } catch (IOException e) {
            taskManager.undoProject(project);
            LOG.log(Level.WARNING, "Problem during saving uploaded file: {0}", e.toString());
        }
        taskManager.addProject(project);
        return null;

    }

    @Override
    public boolean isProjectReadyForDownload(String clientID, String projectID) {
        return taskManager.isProjectReadyForDownload(clientID, projectID);
    }

    @Override
    public Pipe downloadProjectJar(ProjectUID uid, Pipe pipe) throws RemoteException {
        File input = FilesStructure.getProjectJarFile(uid.getClientName(), uid.getProjectName());
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(input))) {
            int n;
            byte[] buffer = new byte[8192];
            while ((n = in.read(buffer)) > -1) {
                pipe.write(buffer, 0, n);
            }
            pipe.close();

        } catch (IOException e) {
            LOG.log(Level.WARNING, "Loading project JAR for client class loader: {0}", e.getMessage());
        }
        return null;
    }

    @Override
    public Pipe downloadProject(String clientID, String projectID, Pipe pipe) throws RemoteException {
        File input = FilesStructure.getCalculatedDataFile(clientID, projectID);
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(input))) {

            int n;
            byte[] buffer = new byte[8192];
            while ((n = in.read(buffer)) > -1) {
                pipe.write(buffer, 0, n);
            }
            pipe.close();

        } catch (IOException e) {
            LOG.log(Level.WARNING, "Loading project for download: {0}", e.getMessage());
        }
        taskManager.removeDownloadedProject(new ProjectUID(clientID, projectID));
        return null;
    }

    @Override
    public long getProjectFileSize(String clientID, String projectID) throws RemoteException {
        File output = FilesStructure.getCalculatedDataFile(clientID, projectID);
        return output.length();
    }

    @Override
    public ArrayList<ProjectInfo> getProjectList(String clientID) throws RemoteException {
        return taskManager.getProjectList(clientID);
    }

    @Override
    public boolean isProjectExists(String clientID, String projectID) throws RemoteException {
        if (taskManager.isProjectInManager(clientID, projectID)) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean pauseProject(String clientID, String projectID) throws RemoteException {
        return taskManager.pauseProject(clientID, projectID);
    }

    @Override
    public boolean cancelProject(String clientID, String projectID) throws RemoteException {
        return taskManager.cancelProject(clientID, projectID);
    }

    @Override
    public boolean resumeProject(String clientID, String projectID) throws RemoteException {
        return taskManager.resumeProject(clientID, projectID);
    }

    @Override
    public void cancelTaskOnClient(String clientID, TaskID taskToCancel) throws RemoteException {
        taskManager.cancelTaskAssociation(clientID, taskToCancel);
    }

    @Override
    public ArrayList<TaskID> sendTasksInCalculation(String clientID, ArrayList<TaskID> tasks) throws RemoteException {
        ArrayList<TaskID> toCancel = new ArrayList<>();
        for (TaskID ID : tasks) {
            if (taskManager.isTaskCompleted(ID) && taskManager.isTaskInProgress(ID)) {
                toCancel.add(ID);
            }
        }
        activeClients.get(clientID).setTimeout(Boolean.TRUE);
        return toCancel;
    }

    private void startClientTimer(final String clientID) {
        LOG.log(Level.INFO, "Timer for client {0} started", clientID);
        final Timer t = new Timer(clientID);
        activeClients.get(clientID).setTimeout(Boolean.TRUE);
        t.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (activeClients.get(clientID).getTimeout().equals(Boolean.TRUE)) {
                    // vse ok, klient se ozval, znovu nastavuju timer
                    activeClients.get(clientID).setTimeout(Boolean.FALSE);
                    LOG.log(Level.INFO, "Client {0} is active", clientID);
                } else {
                    ArrayList<TaskID> tasks = taskManager.cancelTasksAssociation(clientID);
                    LOG.log(Level.WARNING, "Client  {0} has not sent ping message, disconnected", clientID);
                    if (tasks != null) {
                        for (TaskID taskID : tasks) {
                            LOG.log(Level.INFO, "Task {0} calculated by {1} is again in tasks pool", new Object[]{taskID, clientID});
                        }
                    }
                    stopClientTimer(clientID);
                    activeClients.remove(clientID);
                }
            }
        }, 0, timerPeriodSec * 1000);
        activeClients.get(clientID).setTimer(t);

    }

    private void stopClientTimer(String clientID) {
        Timer t = activeClients.get(clientID).getTimer();
        t.cancel();
        activeClients.get(clientID).setTimeout(null);
        activeClients.get(clientID).setTimer(null);
    }
}
