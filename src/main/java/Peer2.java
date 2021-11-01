/*
 * Copyright 2014-2021 Real Logic Limited.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import io.aeron.Aeron;
import io.aeron.FragmentAssembler;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.samples.MultipleSubscribersWithFragmentAssembly;
import io.aeron.samples.SampleConfiguration;
import io.aeron.samples.SamplesUtil;
import org.agrona.BufferUtil;
import org.agrona.CloseHelper;
import org.agrona.concurrent.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class Peer2
{
    private static final int STREAM_ID = 1001;
    private static final int STREAM_ID2 = 1005;
    private static final String CHANNEL = "aeron:udp?endpoint=localhost:20121";
    private static final String CHANNEL2 = "aeron:udp?endpoint=localhost:20125";
    private static final long NUMBER_OF_MESSAGES = 10000L;
    private static final int FRAGMENT_COUNT_LIMIT = 10;
    private static final boolean EMBEDDED_MEDIA_DRIVER = true;

    private static FragmentHandler sendBackMessage(Publication publication, UnsafeBuffer sendBuffer)
    {
        return (buffer, offset, length, header) -> {
            final byte[] data = new byte[length];
            buffer.getBytes(offset, data);
            sendBuffer.putBytes(0, data);
            publication.offer(sendBuffer, 0, length);
        };
    }

    /**
     * Main method for launching the process.
     *
     * @param args passed to the process.
     */
    public static void main(final String[] args)
    {
        System.setProperty("aeron.threading.mode", "DEDICATED");
//        System.setProperty("aeron.dir", "R:\\Aeron2");
        System.out.println("Subscribing to " + CHANNEL + " on stream id " + STREAM_ID);
        final MediaDriver driver = EMBEDDED_MEDIA_DRIVER ? MediaDriver.launchEmbedded() : null;
        final Aeron.Context ctx = new Aeron.Context()
                .availableImageHandler(SamplesUtil::printAvailableImage)
                .unavailableImageHandler(SamplesUtil::printUnavailableImage);

        ctx.aeronDirectoryName(driver.aeronDirectoryName());

        final AtomicBoolean running = new AtomicBoolean(true);

        // Register a SIGINT handler for graceful shutdown.

        // Create an Aeron instance using the configured Context and create a
        // Subscription on that instance that subscribes to the configured
        // channel and stream ID.
        // The Aeron and Subscription classes implement "AutoCloseable" and will automatically
        // clean up resources when this try block is finished
        try (Aeron aeron = Aeron.connect(ctx);
             Subscription subscription = aeron.addSubscription(CHANNEL, STREAM_ID);
             Publication publication = aeron.addPublication(CHANNEL2, STREAM_ID2))
        {
            IdleStrategy idleStrategy = new SleepingIdleStrategy(10);
            final UnsafeBuffer buffer = new UnsafeBuffer(BufferUtil.allocateDirectAligned(16, 64));
            final FragmentHandler fragmentHandler = sendBackMessage(publication, buffer);
            FragmentAssembler assembler = new FragmentAssembler(fragmentHandler);

            for (int i = 0; i < NUMBER_OF_MESSAGES; i++)
            {
                int fragmentsRead = subscription.poll(assembler, FRAGMENT_COUNT_LIMIT);
                idleStrategy.idle(fragmentsRead);
            }

            System.out.println("Shutting down...");
        }

        CloseHelper.close(driver);
    }
}
