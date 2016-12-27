/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jitsi.videobridge.eventadmin.ruby;

import org.jitsi.eventadmin.EventHandler;
import org.jitsi.eventadmin.EventUtil;
import org.jitsi.osgi.ServiceUtils2;
import org.jitsi.service.configuration.ConfigurationService;
import org.jitsi.util.ConfigUtils;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

/**
 * OSGi activator for the <tt>MetricService</tt>
 *
 * @author zbettenbuk
 * @author George Politis
 */
public class Activator
    implements BundleActivator
{
    private RubyConferenceStatsHandler handler;
    private ServiceRegistration<EventHandler> serviceRegistration;

    @Override
    public void start(BundleContext bundleContext)
        throws Exception
    {
        ConfigurationService cfg
            = ServiceUtils2.getService(
                    bundleContext,
                    ConfigurationService.class);

        String bridgeId = ConfigUtils.getString(
                cfg,
                "org.jitsi.videobridge.STATISTICS_BRIDGE_ID",
                "unknown");

        int interval = ConfigUtils.getInt(
                cfg,
                "org.jitsi.videobridge.STATISTICS_RUBY_INTERVAL",
                5000);

        handler = new RubyConferenceStatsHandler(new RubyStats(cfg), bridgeId, interval);

        serviceRegistration = EventUtil.registerEventHandler(
                bundleContext,
                new String[] { "org/jitsi/*" },
                handler);

        System.out.println(">>>>>>> RUby stats activator started");
    }

    @Override
    public void stop(BundleContext bundleContext)
        throws Exception
    {
        if (handler != null)
            handler.stop();

        if (serviceRegistration != null)
            serviceRegistration.unregister();
    }
}
