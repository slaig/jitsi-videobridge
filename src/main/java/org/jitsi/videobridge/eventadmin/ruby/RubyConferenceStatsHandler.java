package org.jitsi.videobridge.eventadmin.ruby;

import io.callstats.sdk.CallStats;
import io.callstats.sdk.CallStatsConferenceEvents;
import io.callstats.sdk.CallStatsErrors;
import io.callstats.sdk.data.*;
import io.callstats.sdk.listeners.CallStatsStartConferenceListener;
import org.jitsi.eventadmin.Event;
import org.jitsi.eventadmin.EventHandler;
import org.jitsi.service.neomedia.MediaStream;
import org.jitsi.service.neomedia.MediaType;
import org.jitsi.service.neomedia.stats.MediaStreamStats2;
import org.jitsi.service.neomedia.stats.ReceiveTrackStats;
import org.jitsi.service.neomedia.stats.SendTrackStats;
import org.jitsi.util.Logger;
import org.jitsi.util.concurrent.PeriodicProcessibleWithObject;
import org.jitsi.util.concurrent.RecurringProcessible;
import org.jitsi.util.concurrent.RecurringProcessibleExecutor;
import org.jitsi.videobridge.Conference;
import org.jitsi.videobridge.Endpoint;
import org.jitsi.videobridge.EventFactory;
import org.jitsi.videobridge.RtpChannel;
import org.jitsi.videobridge.stats.Statistics;

import java.lang.ref.WeakReference;
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
     * The {@link RecurringProcessibleExecutor} which periodically invokes
     * generating and pushing statistics per conference for every Channel.
     */
    private static final RecurringProcessibleExecutor statisticsExecutor
        = new RecurringProcessibleExecutor(
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
            statisticsExecutor.deRegisterRecurringProcessible(cpp);
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
        cpp.start();

        // register for periodic execution.
        this.statisticsProcessors.put(conference, cpp);
        this.statisticsExecutor.registerRecurringProcessible(cpp);
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

        cpp.stop();
        statisticsExecutor.deRegisterRecurringProcessible(cpp);
    }

    /**
     * Implements a {@link RecurringProcessible} which periodically generates a
     * statistics for the conference channels.
     */
    private class ConferencePeriodicProcessible
        extends PeriodicProcessibleWithObject<Conference>
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
            this.conferenceID = conference.getName();
        }

        /**
         * {@inheritDoc}
         *
         * Invokes {@link Statistics#generate()} on {@link #o}.
         */
        @Override
        protected void doProcess()
        {
            // if userInfo is missing the method conferenceSetupResponse
            // is not called, means callstats still has not setup internally
            // this conference, and no stats will be processed for it
            if(userInfo == null)
                return;

            for (Endpoint e : o.getEndpoints())
            {
                for (MediaType mediaType : MEDIA_TYPES)
                {
                    for (RtpChannel rc : e.getChannels(mediaType))
                        processChannelStats(rc);
                }
            }
        }

        /**
         * Called when conference is created. Sends a setup event to callstats
         * and creates the userInfo object that identifies the statistics for
         * this conference.
         */
        void start()
        {
            ConferenceInfo conferenceInfo
                = new ConferenceInfo(this.conferenceID, bridgeId);

            // Send setup event to callstats and on successful response create
            // the userInfo object.
            rubyStats.sendCallStatsConferenceEvent(
                    CallStatsConferenceEvents.CONFERENCE_SETUP,
                    conferenceInfo,
                    new CSStartConferenceListener(new WeakReference<>(this)));
        }

        /**
         * The conference has expired, send terminate event to callstats.
         */
        void stop()
        {
            rubyStats.sendCallStatsConferenceEvent(
                    CallStatsConferenceEvents.CONFERENCE_TERMINATED,
                    userInfo);
        }

        /**
         * Callstats has finished setting up the conference and we can start
         * sending stats.
         * @param ucid the id used to identify the conference inside callstats.
         */
        void conferenceSetupResponse(String ucid)
        {
            userInfo
                = new UserInfo(conferenceID, bridgeId, ucid);
        }

        /**
         * Process channel statistics.
         * @param channel the channel to process
         */
        private void processChannelStats(RtpChannel channel)
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

            rubyStats.startStatsReportingForUser(
                    endpointID,
                    this.conferenceID);

            // Send stats for received streams.
            for (ReceiveTrackStats receiveStat : stats.getAllReceiveStats())
            {
                ConferenceStats conferenceStats
                    = new ConferenceStatsBuilder()
                        .bytesSent(receiveStat.getBytes())
                        .packetsSent(receiveStat.getPackets())
                        .ssrc(String.valueOf(receiveStat.getSSRC()))
                        .confID(this.conferenceID)
                        .localUserID(bridgeId)
                        .remoteUserID(endpointID)
                        .statsType(CallStatsStreamType.INBOUND)
                        // XXX Note that we take these two from the global stats
                        .jitter(stats.getReceiveStats().getJitter())
                        .rtt((int) stats.getReceiveStats().getRtt())
                        .ucID(userInfo.getUcID())
                        .build();
                rubyStats.reportConferenceStats(endpointID, conferenceStats);
            }

            // Send stats for sent streams.
            for (SendTrackStats sendStat : stats.getAllSendStats())
            {
                ConferenceStats conferenceStats
                    = new ConferenceStatsBuilder()
                        .bytesSent(sendStat.getBytes())
                        .packetsSent(sendStat.getPackets())
                        .ssrc(String.valueOf(sendStat.getSSRC()))
                        .confID(this.conferenceID)
                        .localUserID(bridgeId)
                        .remoteUserID(endpointID)
                        .statsType(CallStatsStreamType.OUTBOUND)
                        // XXX Note that we take these two from the global stats
                        .jitter(stats.getSendStats().getJitter())
                        .rtt((int) stats.getSendStats().getRtt())
                        .ucID(userInfo.getUcID())
                        .build();
                rubyStats.reportConferenceStats(endpointID, conferenceStats);
            }

            rubyStats.stopStatsReportingForUser(endpointID, this.conferenceID);
        }
    }

    /**
     * Listener that get notified when conference had been processed
     * by callstats and we have the identifier for it and we can start sending
     * stats for it.
     */
    public static class CSStartConferenceListener
        implements CallStatsStartConferenceListener
    {
        /**
         * Weak reference for the ConferencePeriodicProcessible, to make sure
         * if this listener got leaked somwehere in callstats we will not keep
         * reference to conferences and such.
         */
        private final WeakReference<ConferencePeriodicProcessible> processible;

        /**
         * Creates listener.
         * @param processible the processible interested in ucid value on
         * successful setup of conference in callstats.
         */
        CSStartConferenceListener(
            WeakReference<ConferencePeriodicProcessible> processible)
        {
            this.processible = processible;
        }

        @Override
        public void onResponse(String ucid)
        {
            ConferencePeriodicProcessible p = processible.get();

            // maybe null cause it was garbage collected
            if(p != null)
                p.conferenceSetupResponse(ucid);
        }

        @Override
        public void onError(CallStatsErrors callStatsErrors, String s)
        {
            logger.error(s + "," + callStatsErrors);
        }
    }
}
