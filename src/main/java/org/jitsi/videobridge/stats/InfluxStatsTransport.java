package org.jitsi.videobridge.stats;

import org.influxdb.dto.Point;
import org.jitsi.eventadmin.Event;
import org.jitsi.influxdb.AbstractLoggingHandler;
import org.jitsi.service.configuration.ConfigurationService;


/**
 * Implements {@code StatsTransport} for store to Influx DB
 *
 * @author Lyubomir Marinov
 */
public class InfluxStatsTransport
    extends StatsTransport
{

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
        ptBuilder.fields(statistics.getStats());
        Point point = ptBuilder.build();
        System.out.println(">>> " + point);
        this.influxHandler.writePoint(point);
    }
}
