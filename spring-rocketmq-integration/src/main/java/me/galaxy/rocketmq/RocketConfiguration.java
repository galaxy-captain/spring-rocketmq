package me.galaxy.rocketmq;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * @Description
 * @Author galaxy-captain
 * @Date 2019-06-17 14:21
 **/
public class RocketConfiguration {

    private List<String> nameServer = new LinkedList<String>();

    public void setNameServer(String nameServer) {
        this.nameServer = Arrays.asList(nameServer.split(";"));
    }

    public void setNameServer(String... nameServer) {
        this.nameServer = Arrays.asList(nameServer);
    }

    public void setNameServer(List<String> nameServer) {
        this.nameServer = new ArrayList<String>(nameServer);
    }

    public List<String> getNameServerList() {
        return nameServer;
    }

    public String getNameServer() {
        StringBuilder nameServer = new StringBuilder();
        for (String s : this.nameServer) {
            nameServer.append(s).append(";");
        }
        return nameServer.toString();
    }

}
