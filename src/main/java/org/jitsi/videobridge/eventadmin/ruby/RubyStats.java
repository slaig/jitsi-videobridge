package org.jitsi.videobridge.eventadmin.ruby;

import org.influxdb.dto.Point;
import org.jitsi.eventadmin.Event;
import org.jitsi.influxdb.AbstractLoggingHandler;
import org.jitsi.service.configuration.ConfigurationService;
import org.jitsi.service.neomedia.MediaType;
import org.jitsi.service.neomedia.stats.MediaStreamStats2;
import org.jitsi.service.neomedia.stats.ReceiveTrackStats;
import org.jitsi.service.neomedia.stats.SendTrackStats;

/**
 * Store statistics to influx
 */
public class RubyStats extends AbstractLoggingHandler {

    public RubyStats(ConfigurationService cfg) throws Exception {
        super(cfg);
    }

    public void reportOutbound(String bridgeId, String conferenceID, String endpointID, MediaType mediaType,
                               MediaStreamStats2 stats, SendTrackStats sendStat) {

        Point.Builder ptBuilder = Point.measurement("videobridge_conference_stats");
        ptBuilder.tag("bridgeId", bridgeId);
        ptBuilder.tag("conferenceId", conferenceID);
        ptBuilder.tag("endpointId", endpointID);
        ptBuilder.tag("mediaType", mediaType.name());
        ptBuilder.tag("flowType", "outbound");

        ptBuilder.tag("ssrc", String.valueOf(sendStat.getSSRC()));

        ptBuilder.field("jitter", stats.getSendStats().getJitter());
        ptBuilder.field("rtt", stats.getSendStats().getRtt());

        ptBuilder.field("bytesSent", sendStat.getCurrentBytes());
        ptBuilder.field("packetsSent", sendStat.getCurrentPackets());
        ptBuilder.field("packetRateSent", sendStat.getPacketRate());
        ptBuilder.field("lossRateSent", sendStat.getLossRate());
        ptBuilder.field("bitrateSent", sendStat.getBitrate());

        Point point = ptBuilder.build();

        writePoint(point);
    }

    public void reportInbound(String bridgeId, String conferenceID, String endpointID, MediaType mediaType, MediaStreamStats2 stats, ReceiveTrackStats receiveStat) {
        Point.Builder ptBuilder = Point.measurement("videobridge_conference_stats");
        ptBuilder.tag("bridgeId", bridgeId);
        ptBuilder.tag("conferenceId", conferenceID);
        ptBuilder.tag("endpointId", endpointID);
        ptBuilder.tag("mediaType", mediaType.name());
        ptBuilder.tag("flowType", "inbound");

        ptBuilder.tag("ssrc", String.valueOf(receiveStat.getSSRC()));

        ptBuilder.field("jitter", stats.getReceiveStats().getJitter());
        ptBuilder.field("rtt", stats.getReceiveStats().getRtt());

        ptBuilder.field("bytesReceive", receiveStat.getCurrentBytes());
        ptBuilder.field("packetsReceive", receiveStat.getCurrentPackets());
        ptBuilder.field("packetRateReceive", receiveStat.getPacketRate());
        ptBuilder.field("bitrateReceive", receiveStat.getBitrate());
        ptBuilder.field("packetLostReceive", receiveStat.getCurrentPacketsLost());

        Point point = ptBuilder.build();

        writePoint(point);
    }

    @Override
    public void handleEvent(Event event) {
        // skip
    }
}
