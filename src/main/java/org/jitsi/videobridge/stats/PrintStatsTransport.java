package org.jitsi.videobridge.stats;

/**
 * Implements {@code StatsTransport} for
 * <a href="http://www.callstats.io">callstats.io</a>.
 *
 * @author Lyubomir Marinov
 */
public class PrintStatsTransport
    extends StatsTransport
{

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
        System.out.println(">>> " + measurementInterval + " : " + statistics);
    }
}
