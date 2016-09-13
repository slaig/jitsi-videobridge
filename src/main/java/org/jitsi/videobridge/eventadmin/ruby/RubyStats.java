package org.jitsi.videobridge.eventadmin.ruby;

import com.google.gson.Gson;
import io.callstats.sdk.CallStatsConferenceEvents;
import io.callstats.sdk.data.*;

import java.util.*;

/**
 * Store statistics to influx
 */
public class RubyStats {

    private Map<String, List<ConferenceStats>> conferenceStatsMap = new HashMap<>();

    private Gson gson = new Gson();

    public void sendCallStatsConferenceEvent(CallStatsConferenceEvents event, ConferenceInfo conferenceInfo,
                                             RubyConferenceStatsHandler.CSStartConferenceListener listener) {

        System.err.println(">>>>>>>>>> CONFERENCE " + event + ": " + conferenceInfo);
        listener.onResponse("UCID");
    }

    public void sendCallStatsConferenceEvent(CallStatsConferenceEvents event, UserInfo userInfo) {
        System.err.println(">>>>>>>>>> CONFERENCE " + event + ": " + userInfo);
    }


    public synchronized void startStatsReportingForUser(String userID, String confID) {
        if (userID == null || confID == null) {
            throw new IllegalArgumentException("startStatsReportingForUser: Arguments cannot be null");
        }

        String key = userID + ":" + confID;
        List tempStats = (List) this.conferenceStatsMap.get(key);
        if (tempStats == null) {
            this.conferenceStatsMap.put(key, new ArrayList<ConferenceStats>());
        }
    }

    public synchronized void reportConferenceStats(String userID, ConferenceStats stats) {
        if (stats == null || userID == null) {
            throw new IllegalArgumentException("sendConferenceStats: Arguments cannot be null");
        }

        String key = userID + ":" + stats.getConfID();
        List<ConferenceStats> tempStats = this.conferenceStatsMap.get(key);
        if (tempStats == null) {
            throw new IllegalStateException("reportConferenceStats called without calling startStatsReportingForUser");
        } else {
            tempStats.add(stats);
            this.conferenceStatsMap.put(key, tempStats);
        }
    }

    public synchronized void stopStatsReportingForUser(String userID, String confID) {
        if (userID == null || confID == null) {
            throw new IllegalArgumentException("stopStatsReportingForUser: Arguments cannot be null");
        }

        String key = userID + ":" + confID;
        List tempStats = (List) this.conferenceStatsMap.get(key);
        if (tempStats != null && tempStats.size() > 0) {
            ConferenceStats conferenceStats = (ConferenceStats) tempStats.get(0);
            ConferenceStatsData conferenceStatsData = new ConferenceStatsData(conferenceStats.getLocalUserID(), conferenceStats.getRemoteUserID());
            UserInfo info = new UserInfo(conferenceStats.getConfID(), conferenceStats.getLocalUserID(), conferenceStats.getUcID());

            for (Object tempStat : tempStats) {
                ConferenceStats stats = (ConferenceStats) tempStat;
                StreamStatsData streamStatsData = new StreamStatsData(stats.getRtt(), stats.getPacketsSent(), stats.getBytesSent(), stats.getJitter());
                StreamStats streamStats = new StreamStats(stats.getFromUserID(), stats.getStatsType(), streamStatsData);
                conferenceStatsData.addStreamStats(stats.getSsrc(), streamStats);
            }

            String statsString1 = this.gson.toJson(conferenceStatsData);
            System.err.println(">>>>>>>>>>>>> > " + info);
            System.err.println(">>>>>>>>>>>>> > " + statsString1);

            this.conferenceStatsMap.remove(key);
        }
    }

}
