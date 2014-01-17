/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cz.cuni.mff.bc.server;

import cz.cuni.mff.bc.api.main.ProjectUID;
import static cz.cuni.mff.bc.server.StrategiesList.HIGHEST_PRIORITY_FIRST;
import static cz.cuni.mff.bc.server.StrategiesList.MAXIMAL_THROUGHPUT;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

/**
 * Implementation of strategies plans creating for the clients
 *
 * @author Jakub Hava
 */
public class Planner {

    /**
     * This limit says when any project has less then TASK_LIMIT uncompleted
     * tasks, the absolute priority is given to the project
     */
    public static int TASK_LIMIT = 3;
    private int replanned = 0;

    public int getNumberOfReplanning() {
        return replanned;
    }

    public void resetNumberOfReplanning() {
    }

    /**
     * Creates plans for all the clients according to the active strategy
     *
     * @param activeClients list of active clients
     * @param activeProjects list of active projects
     * @param strategy actual strategy
     */
    public synchronized void plan(Collection<ActiveClient> activeClients, Collection<Project> activeProjects, StrategiesList strategy) {
        ArrayList<ActiveClient> computing = filterProjects(activeClients);
        switch (strategy) {
            case HIGHEST_PRIORITY_FIRST:
                planForHighestPriority(computing, activeProjects);
                break;
            case MAXIMAL_THROUGHPUT:
                planForMaxThroughput(computing, activeProjects);
                break;
        }
    }

    /**
     * Creates plans for one client according to the active strategy
     *
     * @param activeClient active client
     * @param activeProjects list of active projects
     * @param strategy actual strategy
     */
    public synchronized void plan(ActiveClient activeClient, Collection<Project> activeProjects, StrategiesList strategy) {
        switch (strategy) {
            case HIGHEST_PRIORITY_FIRST:
                if (activeClient.isComputing()) {
                    planForHighestPriority(activeClient, activeProjects);
                }
                break;
            case MAXIMAL_THROUGHPUT:
                if (activeClient.isComputing()) {
                    planForMaxThroughput(activeClient, activeProjects);
                }
                break;
        }
    }

    /*
     * Filters only clients which are currently in computation
     */
    private ArrayList<ActiveClient> filterProjects(Collection<ActiveClient> activeClients) {
        ArrayList<ActiveClient> onlyComputing = new ArrayList<>();
        for (ActiveClient activeClient : activeClients) {
            if (activeClient.isComputing()) {
                onlyComputing.add(activeClient);
            }
        }
        return onlyComputing;
    }

    /*
     * Sorts active projects by priorities and returns sorted
     */
    private LinkedList<Project> getSortedListByPriorities(Collection<Project> activeProjects) {

        LinkedList<Project> asList = new LinkedList<>(activeProjects);
        Collections.sort(asList, new Comparator() {
            @Override
            public int compare(Object o1, Object o2) {
                Project p1 = (Project) o1;
                Project p2 = (Project) o2;
                if (p1.getPriority() < p2.getPriority()) {
                    return -1;
                } else if (p1.getPriority() == p2.getPriority()) {
                    return 0;
                } else {
                    return 1;
                }
            }
        });
        return asList;
    }

    /*
     * Creates the plan for all the clients according the strategy Highest Priority First
     */
    private void planForHighestPriority(ArrayList<ActiveClient> activeClients, Collection<Project> activeProjects) {
        LinkedList<Project> sortedListByPriorities = getSortedListByPriorities(activeProjects);
        for (ActiveClient active : activeClients) {
            LinkedHashMap<ProjectUID, Integer> currentPlan = new LinkedHashMap<>();
            currentPlan.put(sortedListByPriorities.getFirst().getProjectUID(), 2);
            active.setCurrentPlan(currentPlan);
        }

    }

    /*
     * Creates the plan for one client according the strategy Highest Priority First
     */
    private void planForHighestPriority(ActiveClient activeClient, Collection<Project> activeProjects) {
        LinkedList<Project> sortedListByPriorities = getSortedListByPriorities(activeProjects);
        LinkedHashMap<ProjectUID, Integer> currentPlan = new LinkedHashMap<>();
        currentPlan.put(sortedListByPriorities.getFirst().getProjectUID(), 2);
        activeClient.setCurrentPlan(currentPlan);
    }

    /*
     * Creates the plan for all the clients according the strategy Maximal Throughput
     */
    private void planForMaxThroughput(ArrayList<ActiveClient> activeClients, Collection<Project> activeProjects) {
    }

    /*
     * Creates the plan for one client according the strategy Maximal Throughput
     */
    private void planForMaxThroughput(ActiveClient activeClient, Collection<Project> activeProjects) {
    }
}
