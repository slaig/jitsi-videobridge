package org.jitsi.videobridge.eventadmin.ruby;

import io.callstats.sdk.data.UserInfo;
import org.jitsi.eventadmin.Event;
import org.jitsi.eventadmin.EventHandler;
import org.jitsi.service.neomedia.MediaStream;
import org.jitsi.service.neomedia.MediaType;
import org.jitsi.service.neomedia.stats.MediaStreamStats2;
import org.jitsi.service.neomedia.stats.ReceiveTrackStats;
import org.jitsi.service.neomedia.stats.SendTrackStats;
import org.jitsi.util.Logger;
import org.jitsi.util.concurrent.PeriodicRunnableWithObject;
import org.jitsi.util.concurrent.RecurringRunnableExecutor;
import org.jitsi.videobridge.Conference;
import org.jitsi.videobridge.Endpoint;
import org.jitsi.videobridge.EventFactory;
import org.jitsi.videobridge.RtpChannel;
import org.jitsi.videobridge.stats.Statistics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles events of the bridge for creating a conference/channel or expiring it
 * and reports statistics per endpoint.
 */
class RubyConferenceStatsHandler
    implements EventHandler
{
    /**
     * The <tt>Logger</tt> used by the <tt>CallStatsConferenceStatsHandler</tt>
     * class and its instances to print debug information.
     */
    private static final Logger logger
        = Logger.getLogger(RubyConferenceStatsHandler.class);

    /*
    * The {@link MediaType}s for which we will report to callstats.
    */
    private static final MediaType[] MEDIA_TYPES
        = { MediaType.AUDIO, MediaType.VIDEO };

    /**
     * The {@link RecurringRunnableExecutor} which periodically invokes
     * generating and pushing statistics per conference for every Channel.
     */
    private static final RecurringRunnableExecutor statisticsExecutor
        = new RecurringRunnableExecutor(
        RubyConferenceStatsHandler.class.getSimpleName()
            + "-statisticsExecutor");

    /**
     * The entry point into the ruby stats (Java) library.
     */
    private RubyStats rubyStats;

    /**
     * The id which identifies the current bridge.
     */
    private String bridgeId;

    /**
     * List of the processor per conference. Kept in order to stop and
     * deRegister them from the executor.
     */
    private final Map<Conference,ConferencePeriodicProcessible>
        statisticsProcessors
            = new ConcurrentHashMap<>();

    /**
     * The interval to poll for stats and to push them to the stats service.
     */
    private int interval;

    public RubyConferenceStatsHandler(RubyStats rubyStats, String bridgeId, int interval) {
        this.rubyStats = rubyStats;
        this.bridgeId = bridgeId;
        this.interval = interval;
    }

    /**
     * Stops and cancels all pending operations. Clears all listeners.
     */
    void stop()
    {
        // Let's stop all left processibles.
        for (ConferencePeriodicProcessible cpp : statisticsProcessors.values())
        {
            statisticsExecutor.deRegisterRecurringRunnable(cpp);
        }
    }

    /**
     * Handles events.
     * @param event the event
     */
    @Override
    public void handleEvent(Event event)
    {
        if (event == null)
        {
            logger.debug("Could not handle an event because it was null.");
            return;
        }

        String topic = event.getTopic();

        if (EventFactory.CONFERENCE_CREATED_TOPIC.equals(topic))
        {
            conferenceCreated(
                    (Conference) event.getProperty(EventFactory.EVENT_SOURCE));
        }
        else if (EventFactory.CONFERENCE_EXPIRED_TOPIC.equals(topic))
        {
            conferenceExpired(
                    (Conference) event.getProperty(EventFactory.EVENT_SOURCE));
        }
    }

    /**
     * Conference created.
     * @param conference
     */
    private void conferenceCreated(final Conference conference)
    {
        if (conference == null)
        {
            logger.debug(
                    "Could not log conference created event because the"
                        + " conference is null.");
            return;
        }

        // Create a new PeriodicProcessible and start it.
        ConferencePeriodicProcessible cpp
            = new ConferencePeriodicProcessible(conference, interval);
//        cpp.start();

        // register for periodic execution.
        this.statisticsProcessors.put(conference, cpp);
        this.statisticsExecutor.registerRecurringRunnable(cpp);
    }

    /**
     * Conference expired.
     * @param conference
     */
    private void conferenceExpired(Conference conference)
    {
        if (conference == null)
        {
            logger.debug(
                    "Could not log conference expired event because the"
                        + " conference is null.");
            return;
        }

        ConferencePeriodicProcessible cpp
            = statisticsProcessors.remove(conference);

        if (cpp == null)
            return;

//        cpp.stop();
        statisticsExecutor.deRegisterRecurringRunnable(cpp);
    }

    /**
     * Implements a {@link org.jitsi.util.concurrent.RecurringRunnable} which periodically generates a
     * statistics for the conference channels.
     */
    private class ConferencePeriodicProcessible
        extends PeriodicRunnableWithObject<Conference>
    {
        /**
         * The user info object used to identify the reports to callstats. Holds
         * the conference, the bridgeID and user callstats ID.
         */
        private UserInfo userInfo = null;

        /**
         * The conference ID to use when reporting stats.
         */
        private final String conferenceID;

        /**
         * Initializes a new {@code ConferencePeriodicProcessible} instance
         * which is to {@code period}ically generate statistics for the
         * conference channels.
         *
         * @param conference the {@code Conference}'s channels to be
         * {@code period}ically checked for statistics by the new instance
         * @param period the time in milliseconds between consecutive
         * generations of statistics
         */
        public ConferencePeriodicProcessible(
                Conference conference,
                long period)
        {
            super(conference, period);

            String name = conference.getName();
            this.conferenceID = (name != null) ? name : conference.getID();
            this.userInfo = new UserInfo(conferenceID, bridgeId, "ruby");
        }

        /**
         * {@inheritDoc}
         *
         * Invokes {@link Statistics#generate()} on {@link #o}.
         */
        @Override
        protected void doRun()
        {
            try {
                for (Endpoint e : o.getEndpoints())
                {
                    for (MediaType mediaType : MEDIA_TYPES)
                    {
                        for (RtpChannel rc : e.getChannels(mediaType))
                            processChannelStats(mediaType, rc);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


        /**
         * Process channel statistics.
         * @param mediaType
         * @param channel the channel to process
         */
        private void processChannelStats(MediaType mediaType, RtpChannel channel)
        {
            if (channel == null)
            {
                logger.debug(
                        "Could not log the channel expired event because the"
                            + " channel is null.");
                return;
            }

            if (channel.getReceiveSSRCs().length == 0)
                return;

            MediaStream stream = channel.getStream();
            if (stream == null)
                return;

            MediaStreamStats2 stats = stream.getMediaStreamStats();
            if (stats == null)
                return;

            Endpoint endpoint = channel.getEndpoint();
            String endpointID = (endpoint == null) ? "" : endpoint.getID();


            // Send stats for received streams.
            for (ReceiveTrackStats receiveStat : stats.getAllReceiveStats())
            {
                rubyStats.reportInbound(bridgeId, conferenceID, endpointID,
                        mediaType, stats, receiveStat);
            }

            // Send stats for sent streams.
            for (SendTrackStats sendStat : stats.getAllSendStats())
            {
                rubyStats.reportOutbound(bridgeId, conferenceID, endpointID,
                        mediaType, stats, sendStat);
            }
        }
    }

}
