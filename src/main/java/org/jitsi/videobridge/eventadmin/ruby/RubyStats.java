package org.jitsi.videobridge.eventadmin.ruby;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
import org.jitsi.eventadmin.Event;
import org.jitsi.influxdb.AbstractLoggingHandler;
import org.jitsi.service.configuration.ConfigurationService;
import org.jitsi.service.neomedia.MediaType;
import org.jitsi.service.neomedia.stats.MediaStreamStats2;
import org.jitsi.service.neomedia.stats.ReceiveTrackStats;
import org.jitsi.service.neomedia.stats.SendTrackStats;
import org.jitsi.service.neomedia.stats.TrackStats;
import org.jitsi.util.Logger;

import java.util.concurrent.TimeUnit;

/**
 * Store statistics to influx
 */
public class RubyStats extends AbstractLoggingHandler {

    private static final Logger logger = Logger.getLogger(RubyStats.class);


    public RubyStats(ConfigurationService cfg) throws Exception {
        super(cfg);
        logger.info("Init ruby logger");

    }

    public void reportOutbound(String bridgeId, String conferenceID, String endpointID, MediaType mediaType,
                               MediaStreamStats2 stats, SendTrackStats sendStat) {
        report(bridgeId, conferenceID, endpointID, mediaType, stats, sendStat);
    }

    public void reportInbound(String bridgeId, String conferenceID, String endpointID, MediaType mediaType,
                              MediaStreamStats2 stats, ReceiveTrackStats receiveStat) {
        report(bridgeId, conferenceID, endpointID, mediaType, stats, receiveStat);
    }

    private void report(String bridgeId, String conferenceID, String endpointID, MediaType mediaType, MediaStreamStats2 stats, TrackStats trackStats) {
        Point.Builder ptBuilder = Point.measurement("vb_conference_stats");
//        ptBuilder.time(System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        ptBuilder.tag("bridgeId", bridgeId);
        ptBuilder.tag("conferenceId", String.valueOf(conferenceID.hashCode() % 100));
        ptBuilder.tag("endpointId", String.valueOf(endpointID.hashCode() % 100));
        ptBuilder.tag("mediaType", mediaType.name());

        ptBuilder.tag("ssrc", String.valueOf(trackStats.getSSRC() % 100));

        ptBuilder.field("jitter", stats.getReceiveStats().getJitter());
        ptBuilder.field("rtt", stats.getReceiveStats().getRtt());

        ptBuilder.field("bytes", trackStats.getBytes());
        ptBuilder.field("currentBytes", trackStats.getCurrentBytes());
        ptBuilder.field("packets", trackStats.getPackets());
        ptBuilder.field("currentPackets", trackStats.getCurrentPackets());
        ptBuilder.field("packetRate", trackStats.getPacketRate());
        ptBuilder.field("bitrate", trackStats.getBitrate());
        ptBuilder.field("interval", trackStats.getInterval());

        if (trackStats instanceof ReceiveTrackStats)
        {
            ptBuilder.tag("flowType", "inbound");

            ptBuilder.field("packetsLost", ((ReceiveTrackStats)trackStats).getPacketsLost());
            ptBuilder.field("currentPacketsLost", ((ReceiveTrackStats)trackStats).getCurrentPacketsLost());
        }
        else if (trackStats instanceof SendTrackStats)
        {
            ptBuilder.tag("flowType", "outbound");

            ptBuilder.field("lossRateSent", ((SendTrackStats) trackStats).getLossRate());
        }

        Point point = ptBuilder.build();
        writePoint(point);
    }

    @Override
    public void handleEvent(Event event) {
        //skip
    }
}
