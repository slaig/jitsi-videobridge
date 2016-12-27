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

import java.util.concurrent.TimeUnit;

/**
 * Store statistics to influx
 */
public class RubyStats extends AbstractLoggingHandler {

    private final InfluxDB influxDB;
    private final String db;

    public RubyStats(ConfigurationService cfg) throws Exception {
        super(cfg);
        String urlBase = cfg.getString("org.jitsi.videobridge.log.INFLUX_URL_BASE", (String)null);
        String user = cfg.getString("org.jitsi.videobridge.log.INFLUX_USER", (String)null);
        String pass = cfg.getString("org.jitsi.videobridge.log.INFLUX_PASS", (String)null);
        this.db = cfg.getString("org.jitsi.videobridge.log.INFLUX_DATABASE", (String) null);

        this.influxDB = InfluxDBFactory.connect(urlBase, user, pass);
        this.influxDB.createDatabase(this.db);
        this.influxDB.enableBatch(2000, 100, TimeUnit.MILLISECONDS);
        System.out.println(">>>>>> " + urlBase);
        System.out.println(">>>>>> " + user);
        System.out.println(">>>>>> " + pass);
        System.out.println(">>>>>> " + this.db);

        Point.Builder b = Point.measurement("bbb");
        b.field("aaa", 23);
        this.influxDB.write(this.db, "default", b.build());
        writePoint(b.build());
        System.out.println("??????????????????? bb ");

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
        Point.Builder b = Point.measurement("bbb");
        b.field("aaa", 23);
        this.influxDB.write(this.db, "default", b.build());
//        writePoint(b.build());
        System.out.println("??????????????????? bb ");

       Point.Builder a = Point.measurement("aaa");
        a.time(System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        a.field("aaa", 23L);
        a.tag("label", "val");
        writePoint(a.build());
        System.out.println("??????????????????? aaa ");

        Point.Builder ptBuilder = Point.measurement("videobridge_conference_stats2");
        ptBuilder.tag("bridgeId", bridgeId);
        ptBuilder.tag("conferenceId", conferenceID);
        ptBuilder.tag("endpointId", endpointID);
        ptBuilder.tag("mediaType", mediaType.name());

        ptBuilder.tag("ssrc", String.valueOf(trackStats.getSSRC()));

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
        // skip
    }
}
