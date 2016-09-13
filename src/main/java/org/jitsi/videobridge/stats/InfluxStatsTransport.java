package org.jitsi.videobridge.stats;

import net.java.sip.communicator.util.Logger;
import org.influxdb.dto.Point;
import org.jitsi.eventadmin.Event;
import org.jitsi.influxdb.AbstractLoggingHandler;
import org.jitsi.service.configuration.ConfigurationService;

import java.util.Map;


/**
 * Implements {@code StatsTransport} for store to Influx DB
 *
 * @author Lyubomir Marinov
 */
public class InfluxStatsTransport
    extends StatsTransport
{
    private static final Logger logger
            = Logger.getLogger(InfluxStatsTransport.class);

    private final AbstractLoggingHandler influxHandler;

    public InfluxStatsTransport(ConfigurationService cfg) throws Exception {
        this.influxHandler = new AbstractLoggingHandler(cfg) {
            @Override
            public void handleEvent(Event event) {
                //skip
            }
        };
     }

    /**
     * {@inheritDoc}
     *
     * {@code CallStatsIOTransport} overrides
     * {@link #publishStatistics(Statistics, long)} so it does not have to do
     * anything in its implementation of {@link #publishStatistics(Statistics)}.
     */
    @Override
    public void publishStatistics(Statistics statistics)
    {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void publishStatistics(
            Statistics statistics,
            long measurementInterval)
    {
        Point.Builder ptBuilder = Point.measurement("videobridge_stats");
        for (Map.Entry<String, Object> entry : statistics.getStats().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Number) {
                ptBuilder.field(entry.getKey(), value);
            }
        }

        Point point = ptBuilder.build();
        System.out.println(">>>> " + point);
        if (logger.isDebugEnabled()) {
            logger.debug("Store stats to influx: " + point);
        }

        this.influxHandler.writePoint(point);
    }
}
